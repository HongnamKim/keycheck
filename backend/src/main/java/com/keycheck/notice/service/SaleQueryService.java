package com.keycheck.notice.service;

import com.keycheck.image.service.ThumbUrls;
import com.keycheck.notice.domain.ArchiveStatus;
import com.keycheck.notice.domain.NoticeAttachment;
import com.keycheck.notice.domain.SaleCategory;
import com.keycheck.notice.domain.SaleItem;
import com.keycheck.notice.dto.SaleCardResponse;
import com.keycheck.notice.dto.SaleDetailResponse;
import com.keycheck.notice.dto.SaleListResponse;
import com.keycheck.notice.dto.SaleScheduleResponse;
import com.keycheck.notice.dto.SaleSitemapItemResponse;
import com.keycheck.notice.repository.NoticeAttachmentRepository;
import com.keycheck.notice.repository.SaleItemRepository;
import com.keycheck.post.domain.KoreanVendor;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Limit;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/** 국내 판매 목록·상세·사이트맵 조회. 숨김·OTHER·요약 전 공지의 건은 내보내지 않는다. */
@Service
@RequiredArgsConstructor
public class SaleQueryService {
  private static final int PAGE_SIZE = 20;

  /** 첫 쪽의 커서 시각: 어떤 공지 시각보다 뒤 */
  private static final Instant FAR_FUTURE = Instant.parse("9999-12-31T00:00:00Z");

  private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
  private static final int SCHEDULE_MAX = 300;
  private static final int PAST_PAGE_SIZE = 30;

  /** 후보로 읽는 공지 기간 (일정 미정 30일, 시작 뒤 7일보다 넉넉히) */
  private static final Duration SCHEDULE_CANDIDATE_POSTED = Duration.ofDays(120);

  private static final Set<KoreanVendor> LIST_VENDORS =
      Set.of(KoreanVendor.SWAGKEYS, KoreanVendor.GEONWORKS);

  private final SaleItemRepository saleItemRepository;
  private final NoticeAttachmentRepository attachmentRepository;
  private final ThumbUrls thumbUrls;
  private final Clock clock;

  /**
   * @param vendor all, swagkeys, geonworks
   * @param category all, keyboard, keycap, switch, parts
   * @param cursor 이전 응답의 nextCursor("공지 시각 밀리초_id"). 첫 요청이면 null
   */
  public SaleListResponse getSales(String vendor, String category, String cursor) {
    String vendorName = parseVendor(vendor);
    String categoryName = parseCategory(category);
    Instant cursorAt = FAR_FUTURE;
    long cursorId = 0;
    if (cursor != null) {
      long[] parsed = parseCursor(cursor);
      cursorAt = Instant.ofEpochMilli(parsed[0]);
      cursorId = parsed[1];
    }

    List<SaleItem> items =
        saleItemRepository.findPage(
            vendorName, categoryName, cursorAt, cursorId, Limit.of(PAGE_SIZE + 1));
    boolean hasMore = items.size() > PAGE_SIZE;
    if (hasMore) {
      items = items.subList(0, PAGE_SIZE);
    }
    String nextCursor =
        hasMore
            ? items.getLast().getPostedAt().toEpochMilli() + "_" + items.getLast().getId()
            : null;
    return new SaleListResponse(cards(items), nextCursor, hasMore);
  }

  /**
   * 지금 기준으로 끝나지 않은 판매(설계 3.9, 화면 배지와 같은 기준). 마감이 있으면 마감(날짜만이면 23:59:59)이 지금 뒤. 마감이 없으면 시작이
   * 있을 때 시작이 아직 안 왔거나 시작 뒤 7일 안(예고 여부와 상관없이), 시작이 없을 때 예고가 아니면 공지 뒤 7일 안·예고면 공지 뒤 30일 안.
   * 공지 시각 최근 순으로 300건까지.
   */
  public SaleScheduleResponse getSchedule(String vendor, String category) {
    String vendorName = parseVendor(vendor);
    String categoryName = parseCategory(category);
    Instant now = clock.instant();
    LocalDate today = now.atZone(SEOUL).toLocalDate();
    List<SaleItem> active =
        saleItemRepository
            .findScheduleCandidates(
                vendorName,
                categoryName,
                now.minus(SCHEDULE_CANDIDATE_POSTED),
                today.minusDays(8),
                today.minusDays(1))
            .stream()
            .filter(i -> SaleTimes.isActive(i, now))
            .limit(SCHEDULE_MAX)
            .toList();
    return new SaleScheduleResponse(cards(active));
  }

  /**
   * 지난 판매: 보이는 판매 건 중 판매 일정 목록에 들지 않는 건 전부(같은 SaleTimes 규칙). 지난 시각 내림차순, 같으면 id 내림차순으로 30건씩.
   * 쪽 나눔은 걸러 낸 전체를 자바에서 정렬해 자른다. 데이터가 수백~수천 건일 때만 맞는 방식이라, 더 늘면 DB에서 정렬·커서로 바꾼다.
   *
   * @param cursor 이전 응답의 nextCursor("지난 시각 밀리초_id"). 첫 요청이면 null
   */
  public SaleListResponse getPast(String vendor, String category, String cursor) {
    String vendorName = parseVendor(vendor);
    String categoryName = parseCategory(category);
    long[] parsed = cursor == null ? null : parseCursor(cursor);
    Instant now = clock.instant();

    List<SaleItem> sorted =
        saleItemRepository.findAllVisible(vendorName, categoryName).stream()
            .filter(i -> !SaleTimes.isActive(i, now))
            .sorted(
                Comparator.comparingLong((SaleItem i) -> SaleTimes.pastAt(i).toEpochMilli())
                    .thenComparing(SaleItem::getId)
                    .reversed())
            .toList();
    List<SaleItem> page =
        sorted.stream()
            .filter(i -> parsed == null || isBefore(i, parsed[0], parsed[1]))
            .limit(PAST_PAGE_SIZE + 1L)
            .toList();
    boolean hasMore = page.size() > PAST_PAGE_SIZE;
    if (hasMore) {
      page = page.subList(0, PAST_PAGE_SIZE);
    }
    String nextCursor =
        hasMore
            ? SaleTimes.pastAt(page.getLast()).toEpochMilli() + "_" + page.getLast().getId()
            : null;
    return new SaleListResponse(cards(page), nextCursor, hasMore);
  }

  /** 내림차순에서 커서 뒤인가 (지난 시각은 밀리초로 비교) */
  private static boolean isBefore(SaleItem item, long cursorMillis, long cursorId) {
    long millis = SaleTimes.pastAt(item).toEpochMilli();
    return millis < cursorMillis || (millis == cursorMillis && item.getId() < cursorId);
  }

  public SaleDetailResponse getSale(Long id) {
    SaleItem item =
        saleItemRepository
            .findVisible(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    List<Long> imageIds = item.getImageIds() == null ? List.of() : item.getImageIds();
    Map<Long, NoticeAttachment> byId = attachments(imageIds);
    NoticeAttachment first = imageIds.isEmpty() ? null : byId.get(imageIds.getFirst());
    List<NoticeAttachment> done =
        imageIds.stream()
            .map(byId::get)
            .filter(a -> a != null && a.getArchiveStatus() == ArchiveStatus.DONE)
            .toList();
    return SaleDetailResponse.from(item, first, done, thumbUrls);
  }

  public List<SaleSitemapItemResponse> getSitemap() {
    return saleItemRepository.findSitemapRows().stream()
        .map(SaleSitemapItemResponse::from)
        .toList();
  }

  /** 카드로 만든다. 대표 사진 첨부는 한 번에 읽는다. */
  List<SaleCardResponse> cards(List<SaleItem> items) {
    List<Long> firstIds =
        items.stream()
            .filter(i -> i.getImageIds() != null && !i.getImageIds().isEmpty())
            .map(i -> i.getImageIds().getFirst())
            .toList();
    Map<Long, NoticeAttachment> byId = attachments(firstIds);
    return items.stream()
        .map(
            i ->
                SaleCardResponse.from(
                    i,
                    i.getImageIds() == null || i.getImageIds().isEmpty()
                        ? null
                        : byId.get(i.getImageIds().getFirst()),
                    thumbUrls))
        .toList();
  }

  private Map<Long, NoticeAttachment> attachments(List<Long> ids) {
    if (ids.isEmpty()) {
      return new HashMap<>();
    }
    return attachmentRepository.findAllById(ids).stream()
        .collect(Collectors.toMap(NoticeAttachment::getId, a -> a));
  }

  /** @return null이면 모든 판매처 */
  private static String parseVendor(String value) {
    if ("all".equalsIgnoreCase(value)) {
      return null;
    }
    for (KoreanVendor vendor : LIST_VENDORS) {
      if (vendor.name().equalsIgnoreCase(value)) {
        return vendor.name();
      }
    }
    throw badValue(value);
  }

  /** @return null이면 모든 분류 */
  private static String parseCategory(String value) {
    if ("all".equalsIgnoreCase(value)) {
      return null;
    }
    for (SaleCategory category : SaleCategory.values()) {
      if (category != SaleCategory.OTHER && category.name().equalsIgnoreCase(value)) {
        return category.name();
      }
    }
    throw badValue(value);
  }

  /** @return {공지 시각 밀리초, id} */
  private static long[] parseCursor(String cursor) {
    String[] parts = cursor.split("_", -1);
    if (parts.length == 2) {
      try {
        long millis = Long.parseLong(parts[0]);
        long id = Long.parseLong(parts[1]);
        if (millis >= 0 && id >= 1) {
          return new long[] {millis, id};
        }
      } catch (NumberFormatException e) {
        // 아래에서 400
      }
    }
    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "허용되지 않는 cursor: " + cursor);
  }

  private static ResponseStatusException badValue(String value) {
    return new ResponseStatusException(HttpStatus.BAD_REQUEST, "허용되지 않는 값: " + value);
  }
}

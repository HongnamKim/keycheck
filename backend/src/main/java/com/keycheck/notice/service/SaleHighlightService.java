package com.keycheck.notice.service;

import com.keycheck.notice.domain.ArchiveStatus;
import com.keycheck.notice.domain.NoticeAttachment;
import com.keycheck.notice.domain.SaleItem;
import com.keycheck.notice.domain.SalePhase;
import com.keycheck.notice.dto.SaleHighlightReason;
import com.keycheck.notice.dto.SalePickResponse;
import com.keycheck.notice.repository.NoticeAttachmentRepository;
import com.keycheck.notice.repository.SaleItemRepository;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 메인 하이라이트에 올릴 국내 판매 건 고르기(설계 3.8). 후보를 DB에서 넉넉히 읽고 한국 시각으로 메모리에서 계산한다. 곧 시작 → 곧 마감 → 최근
 * 소식 순으로 최대 2개를 채우고 같은 건은 한 번만 쓴다.
 */
@Service
@RequiredArgsConstructor
public class SaleHighlightService {
  private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
  private static final int MAX_PICKS = 2;
  private static final Duration SOON = Duration.ofDays(7);
  private static final Duration RECENT = Duration.ofDays(14);

  /** 후보로 읽는 공지 기간 (최근 소식 14일보다 넉넉히) */
  private static final Duration CANDIDATE_POSTED = Duration.ofDays(90);

  private final SaleItemRepository saleItemRepository;
  private final NoticeAttachmentRepository attachmentRepository;
  private final SaleQueryService saleQueryService;

  public List<SalePickResponse> pick(Instant now) {
    LocalDate today = now.atZone(SEOUL).toLocalDate();
    List<SaleItem> candidates =
        saleItemRepository.findHighlightCandidates(
            now.minus(CANDIDATE_POSTED), today.minusDays(1), today.plusDays(8));
    candidates = withReadyImage(candidates);

    Instant limit = now.plus(SOON);
    List<SaleItem> upcoming =
        sorted(
            candidates,
            i -> start(i) != null && start(i).toInstant().isAfter(now) && !start(i).toInstant().isAfter(limit),
            SaleHighlightService::start,
            i -> i.getStartTime() != null);
    List<SaleItem> closing =
        sorted(
            candidates,
            i ->
                onSale(i, now)
                    && end(i) != null
                    && end(i).toInstant().isAfter(now)
                    && !end(i).toInstant().isAfter(limit),
            SaleHighlightService::end,
            i -> i.getEndTime() != null);
    List<SaleItem> recent =
        candidates.stream()
            .filter(i -> !i.getPostedAt().isBefore(now.minus(RECENT)))
            .sorted(
                Comparator.comparing(SaleItem::getPostedAt)
                    .reversed()
                    .thenComparing(SaleItem::getId))
            .toList();

    List<SalePickResponse> picks = new ArrayList<>();
    Set<Long> used = new HashSet<>();
    List<SaleItem> chosen = new ArrayList<>();
    List<SaleHighlightReason> reasons = new ArrayList<>();
    fill(upcoming, SaleHighlightReason.UPCOMING, used, chosen, reasons);
    fill(closing, SaleHighlightReason.CLOSING, used, chosen, reasons);
    fill(recent, SaleHighlightReason.RECENT, used, chosen, reasons);
    var cards = saleQueryService.cards(chosen);
    for (int i = 0; i < chosen.size(); i++) {
      picks.add(new SalePickResponse(reasons.get(i), cards.get(i)));
    }
    return picks;
  }

  private static void fill(
      List<SaleItem> tier,
      SaleHighlightReason reason,
      Set<Long> used,
      List<SaleItem> chosen,
      List<SaleHighlightReason> reasons) {
    for (SaleItem item : tier) {
      if (chosen.size() >= MAX_PICKS) {
        return;
      }
      if (used.add(item.getId())) {
        chosen.add(item);
        reasons.add(reason);
      }
    }
  }

  /** 대표 사진(imageIds 첫째)의 보관이 끝난 건만 */
  private List<SaleItem> withReadyImage(List<SaleItem> items) {
    List<Long> firstIds =
        items.stream()
            .filter(i -> i.getImageIds() != null && !i.getImageIds().isEmpty())
            .map(i -> i.getImageIds().getFirst())
            .toList();
    if (firstIds.isEmpty()) {
      return List.of();
    }
    Set<Long> done =
        attachmentRepository.findAllById(firstIds).stream()
            .filter(a -> a.getArchiveStatus() == ArchiveStatus.DONE)
            .map(NoticeAttachment::getId)
            .collect(Collectors.toSet());
    return items.stream()
        .filter(i -> i.getImageIds() != null && !i.getImageIds().isEmpty())
        .filter(i -> done.contains(i.getImageIds().getFirst()))
        .toList();
  }

  /**
   * 조건에 맞는 건을 이른 순으로: 한국 날짜가 이르면 먼저, 같은 날이면 시각을 아는 건이 먼저, 그다음 시각, 마지막으로 공지가 최근인 순.
   */
  private static List<SaleItem> sorted(
      List<SaleItem> items,
      Predicate<SaleItem> filter,
      Function<SaleItem, ZonedDateTime> moment,
      Predicate<SaleItem> timeKnown) {
    return items.stream()
        .filter(filter)
        .sorted(
            Comparator.comparing((SaleItem i) -> moment.apply(i).toLocalDate())
                .thenComparing(i -> timeKnown.test(i) ? 0 : 1)
                .thenComparing(i -> moment.apply(i).toInstant())
                .thenComparing(SaleItem::getPostedAt, Comparator.reverseOrder())
                .thenComparing(SaleItem::getId))
        .toList();
  }

  /** 시작 순간(한국 시각). 날짜만 알면 그날 00:00, 날짜를 모르면 null */
  private static ZonedDateTime start(SaleItem item) {
    if (item.getStartDate() == null) {
      return null;
    }
    LocalTime time = item.getStartTime() == null ? LocalTime.MIDNIGHT : item.getStartTime();
    return item.getStartDate().atTime(time).atZone(SEOUL);
  }

  /** 마감 순간(한국 시각). 날짜만 알면 그날 23:59:59, 날짜를 모르면 null */
  private static ZonedDateTime end(SaleItem item) {
    if (item.getEndDate() == null) {
      return null;
    }
    LocalTime time = item.getEndTime() == null ? LocalTime.of(23, 59, 59) : item.getEndTime();
    return item.getEndDate().atTime(time).atZone(SEOUL);
  }

  /** 판매 중: 시작이 지났거나, 시작을 모르는데 예고가 아니다 */
  private static boolean onSale(SaleItem item, Instant now) {
    ZonedDateTime start = start(item);
    return start != null ? !start.toInstant().isAfter(now) : item.getPhase() != SalePhase.ANNOUNCE;
  }
}

package com.keycheck.notice.dto;

import com.keycheck.image.service.ThumbUrls;
import com.keycheck.notice.domain.ArchiveStatus;
import com.keycheck.notice.domain.NoticeAttachment;
import com.keycheck.notice.domain.NoticeKeys;
import com.keycheck.notice.domain.SaleCategory;
import com.keycheck.notice.domain.SaleItem;
import com.keycheck.notice.domain.SalePhase;
import com.keycheck.notice.domain.SaleType;
import com.keycheck.post.domain.KoreanVendor;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 판매 목록·하이라이트의 카드 하나. 시각은 한국 시각이다. */
@Getter
@RequiredArgsConstructor
public class SaleCardResponse {
  private static final DateTimeFormatter HH_MM = DateTimeFormatter.ofPattern("HH:mm");

  private final Long id;
  private final KoreanVendor vendor;
  private final String productName;
  private final SaleCategory category;
  private final SaleType saleType;
  private final SalePhase phase;

  /** 판매 시작 날짜(YYYY-MM-DD). 모르면 null */
  private final LocalDate startDate;

  /** 판매 시작 시각(HH:mm). 날짜만 알거나 모르면 null */
  private final String startTime;

  private final LocalDate endDate;
  private final String endTime;

  /** 요약 첫 줄. 없으면 null */
  private final String summaryFirstLine;

  /** 대표 사진 640. 사진이 없거나 아직 보관 전이면 null */
  private final String thumbUrl;

  /** 대표 사진 1280. 사진이 없거나 아직 보관 전이면 null */
  private final String largeUrl;

  /** 공지 시각(ISO-8601, UTC) */
  private final Instant postedAt;

  /**
   * @param first 대표 사진 첨부(imageIds 첫째). 없으면 null. 보관이 끝난(DONE) 것만 쓴다.
   */
  public static SaleCardResponse from(SaleItem item, NoticeAttachment first, ThumbUrls urls) {
    boolean ready = first != null && first.getArchiveStatus() == ArchiveStatus.DONE;
    return new SaleCardResponse(
        item.getId(),
        item.getVendor(),
        item.getProductName(),
        item.getCategory(),
        item.getSaleType(),
        item.getPhase(),
        item.getStartDate(),
        time(item.getStartTime()),
        item.getEndDate(),
        time(item.getEndTime()),
        item.getSummaryLines() == null || item.getSummaryLines().isEmpty()
            ? null
            : item.getSummaryLines().getFirst(),
        ready ? urls.url(NoticeKeys.thumb(first.getId())) : null,
        ready ? urls.url(NoticeKeys.large(first.getId())) : null,
        item.getPostedAt());
  }

  static String time(LocalTime time) {
    return time == null ? null : time.format(HH_MM);
  }
}

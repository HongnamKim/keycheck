package com.keycheck.notice.dto;

import com.keycheck.image.service.ThumbUrls;
import com.keycheck.notice.domain.NoticeAttachment;
import com.keycheck.notice.domain.NoticeKeys;
import com.keycheck.notice.domain.SaleCategory;
import com.keycheck.notice.domain.SaleItem;
import com.keycheck.notice.domain.SalePhase;
import com.keycheck.notice.domain.SaleType;
import com.keycheck.post.domain.KoreanVendor;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 판매 건 상세: 카드 필드에 요약 전부, 배송 안내, 링크, 사진들을 더한다. */
@Getter
@RequiredArgsConstructor
public class SaleDetailResponse {
  private final Long id;
  private final KoreanVendor vendor;
  private final String productName;
  private final SaleCategory category;
  private final SaleType saleType;
  private final SalePhase phase;
  private final LocalDate startDate;
  private final String startTime;
  private final LocalDate endDate;
  private final String endTime;
  private final String summaryFirstLine;
  private final String thumbUrl;
  private final String largeUrl;
  private final String imageUrl;
  private final Instant postedAt;

  /** 요약 줄 전부 */
  private final List<String> summaryLines;

  /** 배송 안내. 없으면 null */
  private final String shippingNote;

  private final List<SaleLinkResponse> links;

  /** 보관이 끝난 사진만, 공지에 나온 순서 */
  private final List<SaleImageResponse> images;

  /**
   * @param first 대표 사진 첨부 (SaleCardResponse.from 참고)
   * @param attachments 보관이 끝난(DONE) 사진 첨부, 순서대로
   */
  public static SaleDetailResponse from(
      SaleItem item, NoticeAttachment first, List<NoticeAttachment> attachments, ThumbUrls urls) {
    SaleCardResponse card = SaleCardResponse.from(item, first, urls);
    return new SaleDetailResponse(
        card.getId(),
        card.getVendor(),
        card.getProductName(),
        card.getCategory(),
        card.getSaleType(),
        card.getPhase(),
        card.getStartDate(),
        card.getStartTime(),
        card.getEndDate(),
        card.getEndTime(),
        card.getSummaryFirstLine(),
        card.getThumbUrl(),
        card.getLargeUrl(),
        card.getImageUrl(),
        card.getPostedAt(),
        item.getSummaryLines() == null ? List.of() : item.getSummaryLines(),
        item.getShippingNote(),
        item.getLinks() == null
            ? List.of()
            : item.getLinks().stream().map(SaleLinkResponse::from).toList(),
        attachments.stream()
            .map(
                a ->
                    new SaleImageResponse(
                        urls.url(NoticeKeys.archive(a.getId())),
                        urls.url(NoticeKeys.large(a.getId())),
                        urls.url(NoticeKeys.thumb(a.getId()))))
            .toList());
  }
}

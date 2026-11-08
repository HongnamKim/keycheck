package com.keycheck.notice.dto;

import com.keycheck.notice.domain.SaleCategory;
import com.keycheck.notice.domain.SaleLink;
import com.keycheck.notice.domain.SalePhase;
import com.keycheck.notice.domain.SaleType;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 요약기가 공지에서 뽑은 판매 건 하나 (저장 전) */
@Getter
@RequiredArgsConstructor
public class SaleItemDraft {
  private final String productName;
  private final SaleCategory category;
  private final SaleType saleType;
  private final SalePhase phase;
  private final LocalDate startDate;
  private final LocalTime startTime;
  private final LocalDate endDate;
  private final LocalTime endTime;
  private final String shippingNote;
  private final List<String> summaryLines;
  private final List<SaleLink> links;
  private final List<Long> imageIds;
}

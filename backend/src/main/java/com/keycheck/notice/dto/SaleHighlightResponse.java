package com.keycheck.notice.dto;

import java.util.List;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 하이라이트로 고른 판매 건들과 날짜 줄 숫자 */
@Getter
@RequiredArgsConstructor
public class SaleHighlightResponse {
  /** 고른 판매 건(최대 2개, 하나도 없으면 빈 배열) */
  private final List<SalePickResponse> picks;

  /** 최근 7일에 올라온 공지의 판매 건 중 숨김·OTHER를 뺀 수 */
  private final long newSaleCount;
}

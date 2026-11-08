package com.keycheck.notice.dto;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 하이라이트로 고른 판매 건 하나와 고른 단계 */
@Getter
@RequiredArgsConstructor
public class SalePickResponse {
  private final SaleHighlightReason reason;
  private final SaleCardResponse sale;
}

package com.keycheck.notice.dto;

import java.util.List;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 하이라이트로 고른 판매 건들(최대 2개, 하나도 없으면 빈 배열) */
@Getter
@RequiredArgsConstructor
public class SaleHighlightResponse {
  private final List<SalePickResponse> picks;
}

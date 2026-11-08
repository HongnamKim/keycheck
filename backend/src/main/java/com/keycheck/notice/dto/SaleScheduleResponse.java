package com.keycheck.notice.dto;

import java.util.List;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 끝나지 않은 판매 건 전체(최대 300). 공지 시각 최근 순 */
@Getter
@RequiredArgsConstructor
public class SaleScheduleResponse {
  private final List<SaleCardResponse> items;
}

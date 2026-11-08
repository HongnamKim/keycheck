package com.keycheck.notice.dto;

import java.util.List;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 무한 스크롤 목록 한 번 분량 */
@Getter
@RequiredArgsConstructor
public class SaleListResponse {
  private final List<SaleCardResponse> items;

  /** hasMore면 다음 요청의 cursor로 그대로 넘긴다. 끝이면 null. */
  private final String nextCursor;

  private final boolean hasMore;
}

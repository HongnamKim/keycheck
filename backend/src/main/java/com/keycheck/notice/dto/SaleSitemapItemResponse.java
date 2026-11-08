package com.keycheck.notice.dto;

import java.time.Instant;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 사이트맵에 넣을 판매 건 하나 */
@Getter
@RequiredArgsConstructor
public class SaleSitemapItemResponse {
  /** 판매 건 ID (키체크 내부 ID) */
  private final Long id;

  /** 공지를 요약한 시각(사이트맵 lastmod) */
  private final Instant lastModified;

  public static SaleSitemapItemResponse from(SaleSitemapRow row) {
    return new SaleSitemapItemResponse(row.getId(), row.getSummarizedAt());
  }
}

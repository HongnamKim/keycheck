package com.keycheck.notice.dto;

import java.time.Instant;

/** 사이트맵에 넣을 판매 건의 id와 공지 요약 시각 */
public interface SaleSitemapRow {
  Long getId();

  Instant getSummarizedAt();
}

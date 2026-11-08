package com.keycheck.post.dto;

import java.time.Instant;

/** 사이트맵에 넣을 글의 ID와 요약 저장 시각 */
public interface PostSitemapRow {
  Long getId();

  Instant getSummarizedAt();
}

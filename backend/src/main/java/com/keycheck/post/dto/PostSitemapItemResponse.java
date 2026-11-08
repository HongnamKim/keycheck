package com.keycheck.post.dto;

import java.time.Instant;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 사이트맵에 넣을 글 하나 */
@Getter
@RequiredArgsConstructor
public class PostSitemapItemResponse {
  /** 글 ID (키체크 내부 ID) */
  private final Long id;

  /** 요약을 마지막으로 저장한 시각(사이트맵 lastmod). 모르면 null. */
  private final Instant lastModified;

  public static PostSitemapItemResponse from(PostSitemapRow row) {
    return new PostSitemapItemResponse(row.getId(), row.getSummarizedAt());
  }
}

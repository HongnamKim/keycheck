package com.keycheck.post.dto;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 사이트맵에 넣을 글 하나 */
@Getter
@RequiredArgsConstructor
public class PostSitemapItemResponse {
  /** 글 ID (키체크 내부 ID) */
  private final Long id;
}

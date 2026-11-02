package com.keycheck.crawl;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 글 마지막 페이지(topic={id}.999999)에서 읽은 조회수와 댓글 수. 갱신에 쓴다. */
@Getter
@RequiredArgsConstructor
public class TopicStats {
  private final int views;
  private final int replies;
}

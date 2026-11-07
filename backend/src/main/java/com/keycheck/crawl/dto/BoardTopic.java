package com.keycheck.crawl.dto;

import java.time.Instant;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 게시판 목록의 글 한 행 */
@Getter
@RequiredArgsConstructor
public class BoardTopic {
  private final long topicId;
  private final String title;
  private final String author;
  private final int replies;
  private final int views;

  /** 마지막 게시물(댓글, 없으면 원글) 시각. 읽지 못하면 null. */
  private final Instant lastPostAt;
}

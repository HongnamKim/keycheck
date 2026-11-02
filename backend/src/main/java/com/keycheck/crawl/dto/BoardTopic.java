package com.keycheck.crawl.dto;

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
}

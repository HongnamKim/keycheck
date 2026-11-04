package com.keycheck.summary.dto;

import com.keycheck.post.domain.Board;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public class PostSource {
  /** 글을 읽어 온 게시판. 요약기가 게시판에 맞게 정리한다(GB 글이면 공동구매 정보를 먼저). */
  private final Board board;

  private final String title;
  private final String bodyText;
}

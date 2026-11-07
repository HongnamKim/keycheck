package com.keycheck.summary.dto;

import com.keycheck.post.domain.Board;
import com.keycheck.post.domain.BodyLink;
import java.time.Instant;
import java.util.List;
import lombok.Getter;

@Getter
public class PostSource {
  /** 글을 읽어 온 게시판. 요약기가 게시판에 맞게 정리한다(GB 글이면 공동구매 정보를 먼저). */
  private final Board board;

  private final String title;
  private final String bodyText;

  /** 글 작성 시각. 요약기가 연도 없이 적힌 공동구매 날짜의 연도를 정할 때 기준으로 쓴다. */
  private final Instant postedAt;

  /** 첫 글 본문의 링크. 요약기가 벤더 판매 링크를 고를 때 쓴다. 링크를 저장하지 않은 글(null)이면 빈 목록. */
  private final List<BodyLink> links;

  public PostSource(
      Board board, String title, String bodyText, Instant postedAt, List<BodyLink> links) {
    this.board = board;
    this.title = title;
    this.bodyText = bodyText;
    this.postedAt = postedAt;
    this.links = links == null ? List.of() : links;
  }
}

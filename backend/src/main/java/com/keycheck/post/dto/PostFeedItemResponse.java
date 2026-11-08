package com.keycheck.post.dto;

import com.keycheck.post.domain.Board;
import com.keycheck.post.domain.Category;
import com.keycheck.post.domain.Post;
import java.time.Instant;
import java.util.List;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** RSS 피드에 넣을 글 하나 */
@Getter
@RequiredArgsConstructor
public class PostFeedItemResponse {
  /** 글 ID (키체크 내부 ID) */
  private final Long id;

  /** 글을 읽어 온 geekhack 게시판 (IC, GB) */
  private final Board board;

  private final String title;

  /** 상품·프로젝트 이름. 모르거나 아직 뽑지 않았으면 null. */
  private final String productName;

  private final Category category;

  /** 요약 줄 전체(하나 이상) */
  private final List<String> summaryLines;

  /** 글 페이지에서 읽은 작성일 */
  private final Instant postedAt;

  /** 요약을 마지막으로 저장한 시각. 모르면 null. */
  private final Instant summarizedAt;

  public static PostFeedItemResponse from(Post post) {
    return new PostFeedItemResponse(
        post.getId(),
        post.getBoard(),
        post.getTitle(),
        post.getProductName(),
        post.getCategory(),
        post.getSummaryLines(),
        post.getPostedAt(),
        post.getSummarizedAt());
  }
}

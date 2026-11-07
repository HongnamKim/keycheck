package com.keycheck.post.dto;

import java.util.List;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 무한 스크롤 목록 한 번 분량 */
@Getter
@RequiredArgsConstructor
public class PostListResponse {
  private final List<PostCardResponse> items;

  /** hasMore면 다음 요청의 cursor로 그대로 넘긴다. null이면 cursor 없이(처음부터) 요청한다. */
  private final String nextCursor;

  /**
   * false면 끝. items가 비었는데 true면 geekhack에서 더 가져오지 못한 순간이므로 잠시 뒤 같은 cursor로 다시 요청한다.
   */
  private final boolean hasMore;
}

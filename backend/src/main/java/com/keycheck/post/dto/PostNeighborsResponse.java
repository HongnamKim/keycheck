package com.keycheck.post.dto;

import java.util.List;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 상세 아래 목록: 같은 조건의 목록에서 이 글 앞뒤 글. 둘 다 목록 순서대로다. */
@Getter
@RequiredArgsConstructor
public class PostNeighborsResponse {
  /** 이 글 바로 위 글들. 마지막 원소가 바로 위 글이다. */
  private final List<PostCardResponse> before;

  /** 이 글 바로 아래 글들. 첫 원소가 바로 아래 글이다. */
  private final List<PostCardResponse> after;
}

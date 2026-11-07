package com.keycheck.post.dto;

import com.keycheck.post.domain.Post;
import java.time.Instant;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public class PostStatsResponse {
  private final int views;
  private final int replies;
  private final Instant statsCheckedAt;

  public static PostStatsResponse from(Post post) {
    return new PostStatsResponse(post.getViews(), post.getReplies(), post.getStatsCheckedAt());
  }
}

package com.keycheck.post.dto;

import java.util.List;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public class PostPageResponse {
  private final List<PostCardResponse> items;
  private final int page;
  private final int totalPages;
}

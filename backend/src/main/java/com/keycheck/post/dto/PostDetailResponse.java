package com.keycheck.post.dto;

import com.keycheck.post.domain.Category;
import com.keycheck.post.domain.Post;
import com.keycheck.post.domain.SpecItem;
import com.keycheck.post.domain.SummaryStatus;
import java.time.Instant;
import java.util.List;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public class PostDetailResponse {
  private final Long id;
  private final String title;
  private final String author;
  private final Category category;
  private final String representativeImageUrl;
  private final SummaryStatus summaryStatus;

  /** summaryStatus가 DONE이 아니면 빈 목록 */
  private final List<String> summaryLines;

  private final List<SpecItem> specs;
  private final String originalUrl;
  private final int views;
  private final int replies;
  private final Instant statsCheckedAt;
  private final Instant postedAt;

  public static PostDetailResponse from(Post post, String imageUrl) {
    boolean done = post.getSummaryStatus() == SummaryStatus.DONE;
    return new PostDetailResponse(
        post.getId(),
        post.getTitle(),
        post.getAuthor(),
        post.getCategory(),
        imageUrl,
        post.getSummaryStatus(),
        done && post.getSummaryLines() != null ? post.getSummaryLines() : List.of(),
        post.getSpecs() != null ? post.getSpecs() : List.of(),
        post.getOriginalUrl(),
        post.getViews(),
        post.getReplies(),
        post.getStatsCheckedAt(),
        post.getPostedAt());
  }
}

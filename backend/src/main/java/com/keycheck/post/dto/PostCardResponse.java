package com.keycheck.post.dto;

import com.keycheck.post.domain.Category;
import com.keycheck.post.domain.Post;
import com.keycheck.post.domain.SummaryStatus;
import java.time.Instant;
import java.util.List;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public class PostCardResponse {
  private final Long id;
  private final String title;
  private final Category category;
  private final String representativeImageUrl;
  private final SummaryStatus summaryStatus;

  /** summaryStatus가 DONE일 때만 값이 있다 */
  private final String summaryFirstLine;

  private final int views;
  private final int replies;
  private final Instant statsCheckedAt;
  private final Instant postedAt;

  public static PostCardResponse from(Post post, String imageUrl) {
    List<String> lines = post.getSummaryLines();
    boolean hasFirstLine =
        post.getSummaryStatus() == SummaryStatus.DONE && lines != null && !lines.isEmpty();
    return new PostCardResponse(
        post.getId(),
        post.getTitle(),
        post.getCategory(),
        imageUrl,
        post.getSummaryStatus(),
        hasFirstLine ? lines.get(0) : null,
        post.getViews(),
        post.getReplies(),
        post.getStatsCheckedAt(),
        post.getPostedAt());
  }
}

package com.keycheck.post.dto;

import com.keycheck.post.Category;
import com.keycheck.post.Post;
import com.keycheck.post.SummaryStatus;
import java.time.Instant;
import java.util.List;

/**
 * @param summaryFirstLine summaryStatus가 DONE일 때만 값이 있다
 */
public record PostCardResponse(
    Long id,
    String title,
    Category category,
    String representativeImageUrl,
    SummaryStatus summaryStatus,
    String summaryFirstLine,
    int views,
    int replies,
    Instant statsCheckedAt,
    Instant postedAt) {

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

package com.keycheck.post.dto;

import com.keycheck.post.Category;
import com.keycheck.post.Post;
import com.keycheck.post.SpecItem;
import com.keycheck.post.SummaryStatus;
import java.time.Instant;
import java.util.List;

/**
 * @param summaryLines summaryStatus가 DONE이 아니면 빈 목록
 */
public record PostDetailResponse(
    Long id,
    String title,
    String author,
    Category category,
    String representativeImageUrl,
    SummaryStatus summaryStatus,
    List<String> summaryLines,
    List<SpecItem> specs,
    String originalUrl,
    int views,
    int replies,
    Instant statsCheckedAt,
    Instant postedAt) {

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

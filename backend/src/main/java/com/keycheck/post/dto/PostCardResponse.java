package com.keycheck.post.dto;

import com.keycheck.image.service.ThumbUrls;
import com.keycheck.post.domain.Board;
import com.keycheck.post.domain.Category;
import com.keycheck.post.domain.Post;
import com.keycheck.post.domain.PostImage;
import com.keycheck.post.domain.Subcategory;
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

  /** 글을 읽어 온 geekhack 게시판 (IC, GB) */
  private final Board board;

  private final Category category;

  /** 세부 분류 코드(KB_65, KC_GMK 등). 기타·미분류 글이나 아직 정하지 않았으면 null. */
  private final Subcategory subcategory;

  private final String representativeImageUrl;

  /** 대표 이미지 축소본(가로 최대 640px) 공개 주소. 대표 이미지나 축소본이 없으면 null이고 화면은 원본을 쓴다. */
  private final String representativeThumbUrl;

  private final SummaryStatus summaryStatus;

  /** summaryStatus가 DONE일 때만 값이 있다 */
  private final String summaryFirstLine;

  private final int views;
  private final int replies;
  private final Instant statsCheckedAt;
  private final Instant postedAt;

  /** postedAt이 없는 글(틀 글)의 글 번호로 추정한 작성일. postedAt이 있거나 추정할 수 없으면 null. */
  private final Instant estimatedPostedAt;

  /** 게시판 목록에서 읽은 마지막 게시물(댓글) 시각. 아직 읽지 못했으면 null. */
  private final Instant lastPostAt;

  /** false면 틀 글: 본문·이미지를 아직 읽지 않았다(대표 이미지·작성일 없음). */
  private final boolean detailLoaded;

  /** representative는 대표 이미지. 없으면 null. */
  public static PostCardResponse from(
      Post post, PostImage representative, Instant estimatedPostedAt, ThumbUrls urls) {
    List<String> lines = post.getSummaryLines();
    boolean hasFirstLine =
        post.getSummaryStatus() == SummaryStatus.DONE && lines != null && !lines.isEmpty();
    return new PostCardResponse(
        post.getId(),
        post.getTitle(),
        post.getBoard(),
        post.getCategory(),
        post.getSubcategory(),
        representative != null ? representative.getUrl() : null,
        representative != null ? urls.url(representative.getThumbKey()) : null,
        post.getSummaryStatus(),
        hasFirstLine ? lines.get(0) : null,
        post.getViews(),
        post.getReplies(),
        post.getStatsCheckedAt(),
        post.getPostedAt(),
        estimatedPostedAt,
        post.getLastPostAt(),
        post.isDetailLoaded());
  }
}

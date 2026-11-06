package com.keycheck.post.dto;

import com.keycheck.image.service.ThumbUrls;
import com.keycheck.post.domain.Board;
import com.keycheck.post.domain.Category;
import com.keycheck.post.domain.Post;
import com.keycheck.post.domain.PostImage;
import com.keycheck.post.domain.SpecItem;
import com.keycheck.post.domain.Subcategory;
import com.keycheck.post.domain.SummaryStatus;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public class PostDetailResponse {
  private final Long id;
  private final String title;
  private final String author;

  /** 글을 읽어 온 geekhack 게시판 (IC, GB) */
  private final Board board;

  private final Category category;

  /** 세부 분류 코드(KB_65, KC_GMK 등). 기타·미분류 글이나 아직 정하지 않았으면 null. */
  private final Subcategory subcategory;

  private final String representativeImageUrl;

  /** 대표 이미지 축소본(가로 최대 640px) 공개 주소. 대표 이미지나 축소본이 없으면 null. */
  private final String representativeThumbUrl;

  /** 대표 이미지 큰 축소본(가로 최대 1280px) 공개 주소. 상세 맨 위에 쓴다. 대표 이미지나 큰 축소본이 없으면 null. */
  private final String representativeLargeUrl;

  /** 규칙에서 빠지지 않은 후보 이미지 전체, 본문 순서, 주소 중복 제외(첫 행) (대표 이미지 포함) */
  private final List<PostImageResponse> images;

  private final SummaryStatus summaryStatus;

  /** summaryStatus가 DONE이 아니면 빈 목록 */
  private final List<String> summaryLines;

  private final List<SpecItem> specs;
  private final String originalUrl;
  private final int views;
  private final int replies;
  private final Instant statsCheckedAt;
  private final Instant postedAt;

  /** postedAt이 없는 글(틀 글)의 글 번호로 추정한 작성일. postedAt이 있거나 추정할 수 없으면 null. */
  private final Instant estimatedPostedAt;

  /** 게시판 목록에서 읽은 마지막 게시물(댓글) 시각. 아직 읽지 못했으면 null. */
  private final Instant lastPostAt;

  /** false면 틀 글: geekhack에서 글을 읽지 못해 본문·이미지·요약이 없다. */
  private final boolean detailLoaded;

  /** representative는 대표 이미지. 없으면 null. */
  public static PostDetailResponse from(
      Post post, PostImage representative, Instant estimatedPostedAt, ThumbUrls urls) {
    boolean done = post.getSummaryStatus() == SummaryStatus.DONE;
    Set<String> seen = new HashSet<>();
    return new PostDetailResponse(
        post.getId(),
        post.getTitle(),
        post.getAuthor(),
        post.getBoard(),
        post.getCategory(),
        post.getSubcategory(),
        representative != null ? representative.getUrl() : null,
        representative != null ? urls.url(representative.getThumbKey()) : null,
        representative != null ? urls.url(representative.getThumbLargeKey()) : null,
        post.getImages().stream()
            .filter(image -> image.getExcludedReason() == null)
            .filter(image -> seen.add(image.getUrl()))
            .map(image -> new PostImageResponse(image.getUrl(), urls.url(image.getThumbKey())))
            .toList(),
        post.getSummaryStatus(),
        done && post.getSummaryLines() != null ? post.getSummaryLines() : List.of(),
        post.getSpecs() != null ? post.getSpecs() : List.of(),
        post.getOriginalUrl(),
        post.getViews(),
        post.getReplies(),
        post.getStatsCheckedAt(),
        post.getPostedAt(),
        estimatedPostedAt,
        post.getLastPostAt(),
        post.isDetailLoaded());
  }
}

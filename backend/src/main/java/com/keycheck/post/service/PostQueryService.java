package com.keycheck.post.service;

import com.keycheck.crawl.domain.BoardBackfill;
import com.keycheck.crawl.repository.BoardBackfillRepository;
import com.keycheck.crawl.service.CrawlService;
import com.keycheck.post.domain.Post;
import com.keycheck.post.domain.PostImage;
import com.keycheck.post.domain.Subcategory;
import com.keycheck.post.dto.CategoryFilter;
import com.keycheck.post.dto.PostCardResponse;
import com.keycheck.post.dto.PostDetailResponse;
import com.keycheck.post.dto.PostListResponse;
import com.keycheck.post.dto.PostSort;
import com.keycheck.post.dto.PostedAtAnchor;
import com.keycheck.post.repository.PostImageRepository;
import com.keycheck.post.repository.PostRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Limit;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/**
 * 목록·상세 조회. 목록이 모자라면 과거 글을 확보하고, 틀 글 상세는 geekhack에서 읽어 채운다.
 *
 * <p>geekhack 응답을 기다리는 동안 DB 트랜잭션을 잡지 않도록 메서드에 @Transactional을 달지 않는다.
 */
@Service
@RequiredArgsConstructor
public class PostQueryService {
  private static final int PAGE_SIZE = 20;
  private static final Duration BACKFILL_WAIT = Duration.ofSeconds(3);
  private static final Duration FILL_WAIT = Duration.ofSeconds(5);

  private final PostRepository postRepository;
  private final PostImageRepository postImageRepository;
  private final BoardBackfillRepository backfillRepository;
  private final CrawlService crawlService;
  private final Clock clock;

  /**
   * @param subcategory null이면 세부 분류로 거르지 않는다. 있으면 filter의 분류에 속한다.
   * @param cursor 이전 응답의 nextCursor. 첫 요청이면 null.
   */
  public PostListResponse getPosts(
      CategoryFilter filter, Subcategory subcategory, PostSort sort, String cursor) {
    return sort == PostSort.VIEWS
        ? getByViews(filter, subcategory, cursor)
        : getRecent(filter, subcategory, cursor);
  }

  /**
   * 커버리지 안의 글을 글 번호 내림차순으로. 모자라고 끝까지 받지 않았으면 작성순 목록 다음 쪽을 한 번 받아 다시 조회한다. cursor는 마지막 글 번호.
   *
   * <p>분류 필터(키보드·키캡, 세부 분류 포함)는 과거 글을 확보하지 않는다. 확보한 틀 글은 미분류라 필터 목록에 붙지 않기 때문이다. 그래서 필터
   * 목록은 지금 분류된 글이 다하면 끝난다.
   */
  private PostListResponse getRecent(
      CategoryFilter filter, Subcategory subcategory, String cursor) {
    long beforeTopicId =
        cursor == null ? Long.MAX_VALUE : parseCursor(cursor, cursor, 1, Long.MAX_VALUE);
    BoardBackfill backfill = backfillRepository.current();
    List<Post> posts = findRecent(filter, subcategory, backfill, beforeTopicId);
    boolean canBackfill = filter.getCategory() == null;
    if (canBackfill
        && posts.size() < PAGE_SIZE
        && !backfill.isExhausted()
        && crawlService.backfillNextPage(BACKFILL_WAIT)) {
      backfill = backfillRepository.current();
      posts = findRecent(filter, subcategory, backfill, beforeTopicId);
    }

    boolean ended = (backfill.isExhausted() || !canBackfill) && posts.size() < PAGE_SIZE;
    boolean hasMore = !ended;
    String nextCursor = posts.isEmpty() ? cursor : String.valueOf(posts.getLast().getTopicId());
    return new PostListResponse(cards(posts), nextCursor, hasMore);
  }

  private List<Post> findRecent(
      CategoryFilter filter, Subcategory subcategory, BoardBackfill backfill, long beforeTopicId) {
    if (backfill.getFloorTopicId() == null) {
      return List.of();
    }
    return postRepository.findRecent(
        backfill.getFloorTopicId(),
        beforeTopicId,
        filter.getCategory(),
        subcategory,
        Limit.of(PAGE_SIZE));
  }

  /**
   * 저장된 글 전체를 조회수순으로. cursor는 마지막 글의 "조회수_글 번호". 오프셋 대신 이 값보다 뒤의 글을 주므로, 이어 받는 사이 앞쪽에 글이
   * 늘어도 같은 글이 다시 오지 않는다.
   */
  private PostListResponse getByViews(
      CategoryFilter filter, Subcategory subcategory, String cursor) {
    int views = Integer.MAX_VALUE;
    long beforeTopicId = Long.MAX_VALUE;
    if (cursor != null) {
      String[] parts = cursor.split("_", -1);
      if (parts.length != 2) {
        throw badCursor(cursor);
      }
      views = (int) parseCursor(parts[0], cursor, 0, Integer.MAX_VALUE);
      beforeTopicId = parseCursor(parts[1], cursor, 1, Long.MAX_VALUE);
    }
    List<Post> posts =
        postRepository.findByViews(
            views, beforeTopicId, filter.getCategory(), subcategory, Limit.of(PAGE_SIZE + 1));

    boolean hasMore = posts.size() > PAGE_SIZE;
    if (hasMore) {
      posts = posts.subList(0, PAGE_SIZE);
    }
    String nextCursor =
        hasMore ? posts.getLast().getViews() + "_" + posts.getLast().getTopicId() : null;
    return new PostListResponse(cards(posts), nextCursor, hasMore);
  }

  /** value가 min 이상 max 이하의 정수가 아니면 400 */
  private long parseCursor(String value, String cursor, long min, long max) {
    try {
      long parsed = Long.parseLong(value);
      if (parsed >= min && parsed <= max) {
        return parsed;
      }
    } catch (NumberFormatException e) {
      // 아래에서 400
    }
    throw badCursor(cursor);
  }

  private ResponseStatusException badCursor(String cursor) {
    return new ResponseStatusException(HttpStatus.BAD_REQUEST, "허용되지 않는 cursor: " + cursor);
  }

  private List<PostCardResponse> cards(List<Post> posts) {
    Map<Long, String> imageUrls = representativeImageUrls(posts);
    Map<Long, Instant> estimates = estimatedPostedAts(posts);
    return posts.stream()
        .map(
            post ->
                PostCardResponse.from(
                    post,
                    imageUrls.get(post.getRepresentativeImageId()),
                    estimates.get(post.getId())))
        .toList();
  }

  /** 틀 글이면 geekhack 글 첫 페이지를 읽어 채운 뒤 돌려준다. 읽지 못하면 틀 그대로 돌려준다. */
  public PostDetailResponse getPost(Long id) {
    Post post = visible(postRepository.findWithImagesById(id));
    if (!post.isDetailLoaded()) {
      crawlService.fillSkeleton(post, FILL_WAIT);
      post = visible(postRepository.findWithImagesById(id));
    }
    String imageUrl = representativeImageUrls(List.of(post)).get(post.getRepresentativeImageId());
    return PostDetailResponse.from(
        post, imageUrl, estimatedPostedAts(List.of(post)).get(post.getId()));
  }

  private Post visible(Optional<Post> post) {
    return post.filter(found -> !found.isHidden())
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
  }

  /**
   * 작성일이 없는 글의 추정 작성일(글 ID → 시각, 추정하지 못한 글은 빠진다). 읽을 때마다 계산하고 저장하지 않는다. 기준점은 글 수와 상관없이
   * 쿼리 한 번으로 모은다.
   */
  private Map<Long, Instant> estimatedPostedAts(List<Post> posts) {
    List<Post> targets = posts.stream().filter(post -> post.getPostedAt() == null).toList();
    if (targets.isEmpty()) {
      return Collections.emptyMap();
    }
    PostedAtEstimator estimator =
        new PostedAtEstimator(
            postRepository
                .findPostedAtAnchors(targets.stream().map(Post::getTopicId).toList())
                .stream()
                .collect(
                    Collectors.toMap(
                        PostedAtAnchor::getTopicId, PostedAtAnchor::getPostedAt)));
    Instant now = clock.instant();
    Map<Long, Instant> estimates = new HashMap<>();
    for (Post post : targets) {
      Instant estimate = estimator.estimate(post.getTopicId(), post.getLastPostAt(), now);
      if (estimate != null) {
        estimates.put(post.getId(), estimate);
      }
    }
    return estimates;
  }

  private Map<Long, String> representativeImageUrls(List<Post> posts) {
    List<Long> ids =
        posts.stream().map(Post::getRepresentativeImageId).filter(Objects::nonNull).toList();
    if (ids.isEmpty()) {
      return Collections.emptyMap();
    }
    return postImageRepository.findAllById(ids).stream()
        .collect(Collectors.toMap(PostImage::getId, PostImage::getUrl));
  }
}

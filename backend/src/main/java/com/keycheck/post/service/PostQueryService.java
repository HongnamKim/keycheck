package com.keycheck.post.service;

import com.keycheck.crawl.config.CollectProperties;
import com.keycheck.crawl.domain.BoardBackfill;
import com.keycheck.crawl.repository.BoardBackfillRepository;
import com.keycheck.crawl.service.CrawlService;
import com.keycheck.image.service.ThumbUrls;
import com.keycheck.post.domain.Board;
import com.keycheck.post.domain.Category;
import com.keycheck.post.domain.Post;
import com.keycheck.post.domain.PostImage;
import com.keycheck.post.domain.Subcategory;
import com.keycheck.post.dto.CategoryFilter;
import com.keycheck.post.dto.PostCardResponse;
import com.keycheck.post.dto.PostDetailResponse;
import com.keycheck.post.dto.PostListResponse;
import com.keycheck.post.dto.PostNeighborsResponse;
import com.keycheck.post.dto.PostSitemapItemResponse;
import com.keycheck.post.dto.PostSort;
import com.keycheck.post.dto.PostedAtAnchor;
import com.keycheck.post.repository.PostImageRepository;
import com.keycheck.post.repository.PostRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
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
  public static final int NEIGHBOR_MAX_SIZE = 10;

  private final PostRepository postRepository;
  private final PostImageRepository postImageRepository;
  private final BoardBackfillRepository backfillRepository;
  private final CrawlService crawlService;
  private final CollectProperties collectProperties;
  private final ThumbUrls thumbUrls;
  private final Clock clock;

  /**
   * @param board null이면 두 게시판 모두(all)
   * @param subcategory null이면 세부 분류로 거르지 않는다. 있으면 filter의 분류에 속한다.
   * @param cursor 이전 응답의 nextCursor. 첫 요청이면 null.
   */
  public PostListResponse getPosts(
      Board board, CategoryFilter filter, Subcategory subcategory, PostSort sort, String cursor) {
    return sort == PostSort.VIEWS
        ? getByViews(board, filter, subcategory, cursor)
        : getRecent(board, filter, subcategory, cursor);
  }

  /**
   * 커버리지 안의 글을 글 번호 내림차순으로. 모자라고 끝까지 받지 않았으면 작성순 목록 다음 쪽을 한 번 받아 다시 조회한다. cursor는 마지막 글 번호.
   *
   * <p>한 게시판이면 그 게시판의 커버리지를 쓴다. 두 게시판(all)이면 두 게시판 모두 빠짐없이 받아 둔 범위만 보여 주고(floorTopicId),
   * 모자라면 덜 받은 게시판(backfillTarget)의 다음 쪽을 받는다. 두 게시판 모두 끝까지 받았을 때만 끝난다.
   *
   * <p>분류 필터(키보드·키캡, 세부 분류 포함)는 과거 글을 확보하지 않는다. 확보한 틀 글은 미분류라 필터 목록에 붙지 않기 때문이다. 그래서 필터
   * 목록은 지금 분류된 글이 다하면 끝난다. 필터 목록의 all은 게시판별 필터 목록을 합친 것이다(게시판마다 그 게시판의 커버리지로 거른다). 큰
   * floor로 자르면 덜 받은 게시판 때문에 커버리지 안의 다른 게시판 글이 가려지는데, 필터 목록은 그 범위를 넓힐 수 없기 때문이다.
   */
  private PostListResponse getRecent(
      Board board, CategoryFilter filter, Subcategory subcategory, String cursor) {
    long beforeTopicId =
        cursor == null ? Long.MAX_VALUE : parseCursor(cursor, cursor, 1, Long.MAX_VALUE);
    List<BoardBackfill> backfills = backfills(board);
    List<Post> posts = findRecent(board, filter, subcategory, backfills, beforeTopicId);
    boolean canBackfill = filter.getCategory() == null;
    Board target = backfillTarget(backfills);
    if (canBackfill
        && posts.size() < PAGE_SIZE
        && target != null
        && crawlService.backfillNextPage(target, BACKFILL_WAIT)) {
      backfills = backfills(board);
      posts = findRecent(board, filter, subcategory, backfills, beforeTopicId);
    }

    boolean exhausted = backfills.stream().allMatch(BoardBackfill::isExhausted);
    boolean ended = (exhausted || !canBackfill) && posts.size() < PAGE_SIZE;
    boolean hasMore = !ended;
    String nextCursor = posts.isEmpty() ? cursor : String.valueOf(posts.getLast().getTopicId());
    return new PostListResponse(cards(posts), nextCursor, hasMore);
  }

  /** board가 null이면 두 게시판의 확보 상태 (Board 순서) */
  private List<BoardBackfill> backfills(Board board) {
    if (board != null) {
      return List.of(backfillRepository.current(board));
    }
    return Arrays.stream(Board.values()).map(backfillRepository::current).toList();
  }

  private List<Post> findRecent(
      Board board,
      CategoryFilter filter,
      Subcategory subcategory,
      List<BoardBackfill> backfills,
      long beforeTopicId) {
    return findInCoverage(
        board,
        filter,
        backfills,
        (floorTopicId, queryBoard) ->
            postRepository.findRecent(
                floorTopicId,
                beforeTopicId,
                queryBoard,
                filter.getCategory(),
                subcategory,
                Limit.of(PAGE_SIZE)),
        Comparator.comparing(Post::getTopicId).reversed(),
        PAGE_SIZE);
  }

  /**
   * 최근순 목록의 커버리지 규칙(getRecent 참고)으로 query를 부른다. query는 (floorTopicId, board) 범위에서 order 순서로 최대
   * limit개를 읽는다. 분류 필터의 all이면 게시판마다 그 게시판의 floor로 부르고 order로 합쳐 limit개를 남긴다. 아니면 한 번 부른다.
   * floor가 수집 기간 경계보다 작으면 경계로 올린다.
   */
  private List<Post> findInCoverage(
      Board board,
      CategoryFilter filter,
      List<BoardBackfill> backfills,
      BiFunction<Long, Board, List<Post>> query,
      Comparator<Post> order,
      int limit) {
    long since = collectProperties.getSinceTopicId();
    if (board == null && filter.getCategory() != null) {
      return backfills.stream()
          .filter(backfill -> backfill.getFloorTopicId() != null)
          .flatMap(
              backfill ->
                  query
                      .apply(Math.max(backfill.getFloorTopicId(), since), backfill.getBoard())
                      .stream())
          .sorted(order)
          .limit(limit)
          .toList();
    }
    Long floorTopicId = floorTopicId(board, backfills);
    if (floorTopicId == null) {
      return List.of();
    }
    return query.apply(Math.max(floorTopicId, since), board);
  }

  /**
   * 빠짐없이 보여 줄 수 있는 글 번호의 하한. null이면 아직 보여 줄 수 있는 글이 없다.
   *
   * <p>한 게시판이면 그 게시판의 floor. 두 게시판이면 게시판별 유효 floor(끝까지 받았으면 0, 아무것도 받지 않았으면 무한대, 아니면 floor) 중
   * 큰 값이다. 그보다 작은 번호에는 덜 받은 게시판의 글이 빠져 있을 수 있다.
   */
  private static Long floorTopicId(Board board, List<BoardBackfill> backfills) {
    if (board != null) {
      return backfills.getFirst().getFloorTopicId();
    }
    long floor = 0;
    for (BoardBackfill backfill : backfills) {
      if (backfill.isExhausted()) {
        continue;
      }
      if (backfill.getFloorTopicId() == null) {
        return null;
      }
      floor = Math.max(floor, backfill.getFloorTopicId());
    }
    return floor;
  }

  /**
   * 다음에 받을 게시판: 끝까지 받지 않은 게시판 중 floor가 가장 큰(아무것도 받지 않았으면 무한대) 게시판. 같으면 Board 순서가 앞인 것. 모두
   * 끝까지 받았으면 null.
   */
  private static Board backfillTarget(List<BoardBackfill> backfills) {
    BoardBackfill target = null;
    for (BoardBackfill backfill : backfills) {
      if (!backfill.isExhausted()
          && (target == null || effectiveFloor(backfill) > effectiveFloor(target))) {
        target = backfill;
      }
    }
    return target == null ? null : target.getBoard();
  }

  private static long effectiveFloor(BoardBackfill backfill) {
    return backfill.getFloorTopicId() == null ? Long.MAX_VALUE : backfill.getFloorTopicId();
  }

  /**
   * 저장된 글 전체(board가 있으면 그 게시판 글)를 조회수순으로. 커버리지와 상관없다. cursor는 마지막 글의 "조회수_글 번호". 오프셋 대신 이 값보다 뒤의 글을 주므로, 이어 받는 사이 앞쪽에 글이
   * 늘어도 같은 글이 다시 오지 않는다. 수집 기간 경계보다 작은 글은 주지 않는다.
   */
  private PostListResponse getByViews(
      Board board, CategoryFilter filter, Subcategory subcategory, String cursor) {
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
            views,
            beforeTopicId,
            collectProperties.getSinceTopicId(),
            board,
            filter.getCategory(),
            subcategory,
            Limit.of(PAGE_SIZE + 1));

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

  List<PostCardResponse> cards(List<Post> posts) {
    Map<Long, PostImage> images = representativeImages(posts);
    Map<Long, Instant> estimates = estimatedPostedAts(posts);
    return posts.stream()
        .map(
            post ->
                PostCardResponse.from(
                    post,
                    images.get(post.getRepresentativeImageId()),
                    estimates.get(post.getId()),
                    thumbUrls))
        .toList();
  }

  /**
   * 틀 글이면 geekhack 글 첫 페이지를 읽어 채운 뒤 돌려준다. 읽지 못하면 틀 그대로 돌려준다. 미리 읽기가 채운 글이면 미리 읽기 표시를
   * 지워, 요약이 아직이면 미리 읽기로 채운 다른 글보다 먼저 요약되게 한다.
   *
   * @param fill false면 채우지 않고 미리 읽기 표시도 그대로 둔다(검색·미리보기 봇이 원문 읽기·요약을 늘리지 않게)
   */
  public PostDetailResponse getPost(Long id, boolean fill) {
    Post post = visible(postRepository.findWithImagesById(id));
    if (fill && !post.isDetailLoaded()) {
      crawlService.fillSkeleton(post, FILL_WAIT);
      post = visible(postRepository.findWithImagesById(id));
    }
    if (fill && post.isPrefetched()) {
      postRepository.clearPrefetched(post.getId());
    }
    PostImage representative =
        representativeImages(List.of(post)).get(post.getRepresentativeImageId());
    return PostDetailResponse.from(
        post, representative, estimatedPostedAts(List.of(post)).get(post.getId()), thumbUrls);
  }

  /** 사이트맵에 넣을 글(PostRepository.findSitemapIds), 글 번호 내림차순. geekhack을 호출하지 않는다. */
  public List<PostSitemapItemResponse> getSitemap() {
    return postRepository.findSitemapIds(collectProperties.getSinceTopicId()).stream()
        .map(PostSitemapItemResponse::new)
        .toList();
  }

  /**
   * 상세 아래 목록: getPosts와 같은 조건의 목록에서 이 글 바로 위(before)·아래(after) 최대 size개씩, 둘 다 목록 순서대로. 이 글이 목록
   * 조건에 맞지 않아도 그 정렬 기준 위치로 계산한다. 과거 글을 확보하지 않는다(geekhack 요청 없음).
   *
   * @param size 1 이상 NEIGHBOR_MAX_SIZE 이하
   */
  public PostNeighborsResponse getNeighbors(
      Long id, Board board, CategoryFilter filter, Subcategory subcategory, PostSort sort, int size) {
    if (size < 1 || size > NEIGHBOR_MAX_SIZE) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "size는 1 이상 " + NEIGHBOR_MAX_SIZE + " 이하: " + size);
    }
    Post post = visible(postRepository.findById(id));
    Category category = filter.getCategory();
    Limit limit = Limit.of(size);
    List<Post> above;
    List<Post> below;
    if (sort == PostSort.VIEWS) {
      long since = collectProperties.getSinceTopicId();
      above =
          postRepository.findByViewsAbove(
              post.getViews(), post.getTopicId(), since, board, category, subcategory, limit);
      below =
          postRepository.findByViews(
              post.getViews(), post.getTopicId(), since, board, category, subcategory, limit);
    } else {
      List<BoardBackfill> backfills = backfills(board);
      above =
          findInCoverage(
              board,
              filter,
              backfills,
              (floorTopicId, queryBoard) ->
                  postRepository.findRecentAbove(
                      floorTopicId, post.getTopicId(), queryBoard, category, subcategory, limit),
              Comparator.comparing(Post::getTopicId),
              size);
      below =
          findInCoverage(
              board,
              filter,
              backfills,
              (floorTopicId, queryBoard) ->
                  postRepository.findRecent(
                      floorTopicId, post.getTopicId(), queryBoard, category, subcategory, limit),
              Comparator.comparing(Post::getTopicId).reversed(),
              size);
    }
    // above는 가까운 글부터라 목록 순서로 뒤집는다. 대표 이미지·추정 작성일은 한 번에 모아 읽는다.
    List<Post> before = above.reversed();
    List<PostCardResponse> cards =
        cards(Stream.concat(before.stream(), below.stream()).toList());
    return new PostNeighborsResponse(
        cards.subList(0, before.size()), cards.subList(before.size(), cards.size()));
  }

  /** 없거나 숨김이거나 수집 기간 경계보다 작은 글이면 404 */
  private Post visible(Optional<Post> post) {
    return post.filter(
            found ->
                !found.isHidden() && found.getTopicId() >= collectProperties.getSinceTopicId())
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

  /** 대표 이미지(이미지 ID → 이미지). 원본 주소와 축소본 객체 이름을 함께 쓴다. */
  private Map<Long, PostImage> representativeImages(List<Post> posts) {
    List<Long> ids =
        posts.stream().map(Post::getRepresentativeImageId).filter(Objects::nonNull).toList();
    if (ids.isEmpty()) {
      return Collections.emptyMap();
    }
    return postImageRepository.findAllById(ids).stream()
        .collect(Collectors.toMap(PostImage::getId, Function.identity()));
  }
}

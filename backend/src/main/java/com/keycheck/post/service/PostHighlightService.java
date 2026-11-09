package com.keycheck.post.service;

import com.keycheck.crawl.config.CollectProperties;
import com.keycheck.post.domain.Board;
import com.keycheck.post.domain.Post;
import com.keycheck.post.dto.HighlightResponse;
import com.keycheck.post.dto.HighlightSlot;
import com.keycheck.post.dto.HighlightSlotResponse;
import com.keycheck.post.dto.PostCardResponse;
import com.keycheck.post.repository.PostRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;

/**
 * 메인 하이라이트 칸과 날짜 줄 숫자(설계 3.2). 칸마다 쿼리로 후보를 읽어 HighlightSlot 순서로 고르고, 앞 칸에 들어간 글은 건너뛴다. 요청마다
 * 계산하고 geekhack을 부르지 않는다.
 *
 * <p>"최근 N일"은 지금에서 N일 전부터(굴러가는 창, 작성일 기준)이고, "오늘"은 한국 날짜다.
 */
@Service
@RequiredArgsConstructor
public class PostHighlightService {
  private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

  /** 창 안에 후보가 없을 때 차례로 넓히는 창(일). 처음 창보다 넓은 것만 쓰고, 그래도 없으면 전체 */
  private static final List<Integer> WIDER_WINDOW_DAYS = List.of(30, 90);

  /** 날짜 줄의 새 글 창(일) */
  private static final int NEW_POST_DAYS = 7;

  /** 마감 띠 후보 범위: 오늘부터 이 날수 뒤까지(양끝 포함) 마감하는 공동구매. 먼 마감(D-120 등)은 넣지 않는다 */
  private static final int DEADLINE_DAYS = 30;

  private final PostRepository postRepository;
  private final PostQueryService postQueryService;
  private final CollectProperties collectProperties;
  private final Clock clock;

  public HighlightResponse getHighlights() {
    Instant now = clock.instant();
    LocalDate today = now.atZone(SEOUL).toLocalDate();
    long since = collectProperties.getSinceTopicId();

    List<Pick> picks = new ArrayList<>();
    Set<Long> used = new HashSet<>();
    for (HighlightSlot slot : HighlightSlot.values()) {
      Pick pick = pick(slot, since, now, today, used);
      if (pick != null) {
        picks.add(pick);
        used.add(pick.getPost().getId());
      }
    }
    // 대표 이미지는 한 번에 모아 읽는다
    List<PostCardResponse> cards =
        postQueryService.cards(picks.stream().map(Pick::getPost).toList());
    List<HighlightSlotResponse> slots = new ArrayList<>();
    for (int i = 0; i < picks.size(); i++) {
      slots.add(picks.get(i).toResponse(cards.get(i)));
    }

    Instant newFrom = now.minus(Duration.ofDays(NEW_POST_DAYS));
    return new HighlightResponse(
        today,
        postRepository.countPostedSince(since, Board.IC, newFrom),
        postRepository.countPostedSince(since, Board.GB, newFrom),
        slots);
  }

  /** slot 규칙(HighlightSlot)으로 used를 건너뛴 첫 글. 없으면 null */
  private Pick pick(HighlightSlot slot, long since, Instant now, LocalDate today, Set<Long> used) {
    // 앞 칸에 들어간 글을 모두 건너뛰어도 한 글이 남도록 읽는다
    Limit limit = Limit.of(used.size() + 1);
    Function<Instant, List<Post>> gbViews =
        postedFrom -> postRepository.findHighlightsByViews(since, Board.GB, postedFrom, limit);
    Function<Instant, List<Post>> icViews =
        postedFrom -> postRepository.findHighlightsByViews(since, Board.IC, postedFrom, limit);
    return switch (slot) {
      case FEATURE -> widening(slot, 7, gbViews, now, used);
      case TALL -> widening(slot, 14, icViews, now, used);
      case DEADLINE -> {
        Post post =
            postRepository
                .findHighlightsByDeadline(since, today, today.plusDays(DEADLINE_DAYS), limit)
                .stream()
                .filter(candidate -> !used.contains(candidate.getId()))
                .findFirst()
                .orElse(null);
        yield post != null
            ? new Pick(slot, true, null, null, post)
            : widening(slot, 14, gbViews, now, used);
      }
      case IC_REPLIES ->
          widening(
              slot,
              14,
              postedFrom ->
                  postRepository.findHighlightsByReplies(since, Board.IC, postedFrom, limit),
              now,
              used);
      case IC_VIEWS -> widening(slot, 14, icViews, now, used);
      case GB_VIEWS -> widening(slot, 30, gbViews, now, used);
    };
  }

  /**
   * 최근 firstDays일 창에서 query(작성일 하한 → 정렬된 후보)로 읽어 used를 건너뛴 첫 글. 없으면 창을 30일(처음 창이 더 짧을 때만) →
   * 90일 → 전체로 넓힌다. 끝까지 없으면 null.
   */
  private static Pick widening(
      HighlightSlot slot,
      int firstDays,
      Function<Instant, List<Post>> query,
      Instant now,
      Set<Long> used) {
    List<Integer> windows = new ArrayList<>();
    windows.add(firstDays);
    WIDER_WINDOW_DAYS.stream().filter(days -> days > firstDays).forEach(windows::add);
    // 전체
    windows.add(null);
    for (Integer days : windows) {
      Instant postedFrom = days == null ? Instant.EPOCH : now.minus(Duration.ofDays(days));
      List<Post> posts = query.apply(postedFrom);
      for (int i = 0; i < posts.size(); i++) {
        if (!used.contains(posts.get(i).getId())) {
          return new Pick(slot, false, days, i + 1, posts.get(i));
        }
      }
    }
    return null;
  }

  /** 고른 글과 칸 정보. 카드는 고른 글을 모아 한 번에 만든다. */
  @Getter
  @RequiredArgsConstructor
  private static class Pick {
    private final HighlightSlot slot;
    private final boolean deadline;

    /** 전체 창이거나 마감 띠면 null */
    private final Integer windowDays;

    /** 그 창의 결과 목록 위치 + 1. 마감 띠면 null */
    private final Integer rank;

    private final Post post;

    HighlightSlotResponse toResponse(PostCardResponse card) {
      return new HighlightSlotResponse(slot, deadline, windowDays, rank, card);
    }
  }
}

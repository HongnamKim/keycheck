package com.keycheck.post.service;

import com.keycheck.crawl.client.GeekhackClient;
import com.keycheck.crawl.client.RequestGate;
import com.keycheck.crawl.config.CollectProperties;
import com.keycheck.crawl.dto.TopicStats;
import com.keycheck.crawl.parser.TopicParser;
import com.keycheck.post.config.StatsProperties;
import com.keycheck.post.domain.Post;
import com.keycheck.post.dto.PostStatsResponse;
import com.keycheck.post.repository.PostRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/**
 * 상세 화면의 조회수·댓글 수 (설계 4.3). 오래된 값이면 geekhack에서 다시 읽고, 읽지 못하면 저장값을 돌려준다. 마지막 댓글이 오래된 글은
 * 갱신 간격을 늘리고, 한 시간(정각 기준)에 geekhack에 요청하는 횟수에 상한을 둔다(StatsProperties).
 *
 * <p>geekhack 응답을 기다리는 동안 DB 트랜잭션을 잡지 않도록 메서드에 @Transactional을 달지 않는다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PostStatsService {
  private static final Duration FRESH_FOR = Duration.ofHours(1);

  private final PostRepository postRepository;
  private final GeekhackClient client;
  private final RequestGate gate;
  private final TopicParser topicParser;
  private final CollectProperties collectProperties;
  private final StatsProperties properties;
  private final Clock clock;

  /** 갱신 예산을 세는 시간 창(정각)과 그 창에서 geekhack에 요청한 횟수 */
  private Instant budgetHour;
  private int budgetUsed;

  /** 상세와 같이 없거나 숨김이거나 수집 기간 경계보다 작은 글이면 404 (geekhack을 부르지 않는다) */
  public PostStatsResponse getStats(Long id) {
    Post post =
        postRepository
            .findById(id)
            .filter(
                found ->
                    !found.isHidden()
                        && found.getTopicId() >= collectProperties.getSinceTopicId())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));

    Instant now = clock.instant();
    boolean fresh = !post.getStatsCheckedAt().plus(freshFor(post, now)).isBefore(now);
    if (fresh || !acquireRequest(now)) {
      return PostStatsResponse.from(post);
    }

    TopicStats stats = fetchStats(post);
    if (stats == null) {
      return PostStatsResponse.from(post);
    }
    postRepository.updateStats(post.getId(), stats.getViews(), stats.getReplies(), now);
    return new PostStatsResponse(stats.getViews(), stats.getReplies(), now);
  }

  /** 마지막 댓글(없으면 작성일)이 inactiveAfter보다 오래된 글은 inactiveInterval, 그 밖의 글(둘 다 모르는 글 포함)은 FRESH_FOR */
  private Duration freshFor(Post post, Instant now) {
    Instant lastActivity = post.getLastPostAt() != null ? post.getLastPostAt() : post.getPostedAt();
    boolean inactive =
        lastActivity != null && lastActivity.plus(properties.getInactiveAfter()).isBefore(now);
    return inactive ? properties.getInactiveInterval() : FRESH_FOR;
  }

  /**
   * 이번 시간 창(정각 기준)의 갱신 예산이 남았고 요청 간격이 비었으면(기다리지 않는다) 요청 한 번을 센다.
   *
   * @return geekhack에 요청해도 되면 true
   */
  private synchronized boolean acquireRequest(Instant now) {
    Instant hour = now.truncatedTo(ChronoUnit.HOURS);
    if (!hour.equals(budgetHour)) {
      budgetHour = hour;
      budgetUsed = 0;
    }
    if (budgetUsed >= properties.getHourlyBudget() || !gate.tryAcquire()) {
      return false;
    }
    budgetUsed++;
    return true;
  }

  /** 요청이나 해석에 실패하면 null */
  private TopicStats fetchStats(Post post) {
    try {
      return topicParser.parseStats(client.fetchTopicLastPage(post.getTopicId()));
    } catch (RuntimeException e) {
      log.warn("조회수 갱신 실패: topic={}", post.getTopicId(), e);
      return null;
    }
  }
}

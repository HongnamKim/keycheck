package com.keycheck.post.service;

import com.keycheck.crawl.client.GeekhackClient;
import com.keycheck.crawl.client.RequestGate;
import com.keycheck.crawl.dto.TopicStats;
import com.keycheck.crawl.parser.TopicParser;
import com.keycheck.post.domain.Post;
import com.keycheck.post.dto.PostStatsResponse;
import com.keycheck.post.repository.PostRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/**
 * 상세 화면의 조회수·댓글 수 (설계 4.3). 오래된 값이면 geekhack에서 다시 읽고, 읽지 못하면 저장값을 돌려준다.
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
  private final Clock clock;

  public PostStatsResponse getStats(Long id) {
    Post post =
        postRepository
            .findById(id)
            .filter(found -> !found.isHidden())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));

    Instant now = clock.instant();
    boolean fresh = !post.getStatsCheckedAt().plus(FRESH_FOR).isBefore(now);
    if (fresh || !gate.tryAcquire()) {
      return PostStatsResponse.from(post);
    }

    TopicStats stats = fetchStats(post);
    if (stats == null) {
      return PostStatsResponse.from(post);
    }
    postRepository.updateStats(post.getId(), stats.getViews(), stats.getReplies(), now);
    return new PostStatsResponse(stats.getViews(), stats.getReplies(), now);
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

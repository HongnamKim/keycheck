package com.keycheck.summary.service;

import com.anthropic.errors.AnthropicException;
import com.anthropic.errors.AnthropicInvalidDataException;
import com.keycheck.post.domain.Post;
import com.keycheck.post.domain.SummaryStatus;
import com.keycheck.post.repository.PostRepository;
import com.keycheck.summary.config.SummaryProperties;
import com.keycheck.summary.dto.PostSource;
import com.keycheck.summary.dto.SummaryResult;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;

/**
 * 수집은 글을 요약 대기(PENDING)로만 저장하고, 이 작업이 하나씩 요약한다. 요약기 호출은 트랜잭션 밖에서 하고, 저장은 요약
 * 컬럼(과 분류·세부 분류)만 바꿔 그사이 다른 쓰기를 덮지 않는다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SummaryWorker {
  /** API를 쓸 수 없을 때(인증·설정·과부하·연결 오류) 다음 시도까지 쉬는 시간 */
  static final Duration ERROR_PAUSE = Duration.ofMinutes(1);

  private final PostRepository postRepository;
  private final Summarizer summarizer;
  private final SummaryProperties properties;
  private final Clock clock;

  private Instant pausedUntil = Instant.MIN;

  /**
   * 이번 실행에서 이미 다시 요약한(요약이 있던) 글 ID. 다시 요약해도 여전히 대상인 글(예: 세부 분류를 정하지 못한 글)을 계속 요약하지 않도록
   * 건너뛴다.
   */
  private final Set<Long> redone = new HashSet<>();

  /**
   * 요약 대상 중 글 번호가 가장 큰 글 하나를 요약한다. 글 때문에 요약할 수 없으면 FAILED로 두고 다시 시도하지 않는다. API를 쓸
   * 수 없으면 상태를 그대로 두고 ERROR_PAUSE 동안 쉰다. 요약이 있던 글은 한 실행에서 한 번만 다시 요약한다.
   *
   * @return 대상이 있었으면 true (쉬는 중이면 false)
   */
  public boolean summarizeNext() {
    if (clock.instant().isBefore(pausedUntil)) {
      return false;
    }
    // 가짜 요약기는 세부 분류를 정하지 않으므로, 켜 두면 Claude 요약만 가짜 요약으로 덮는다
    boolean redoMissingSubcategory =
        properties.isRedoMissingSubcategory() && !(summarizer instanceof FakeSummarizer);
    List<Post> targets =
        postRepository.findSummaryTargets(
            properties.isRedoFake(), redoMissingSubcategory, Limit.of(redone.size() + 1));
    Post post =
        targets.stream()
            .filter(target -> !redone.contains(target.getId()))
            .findFirst()
            .orElse(null);
    if (post == null) {
      return false;
    }
    SummaryResult result;
    try {
      result = summarizer.summarize(
              new PostSource(post.getBoard(), post.getTitle(), post.getBodyText()));
    } catch (RuntimeException e) {
      if (isApiUnavailable(e)) {
        log.error("요약 API를 쓸 수 없어 {} 쉰다: topic={}", ERROR_PAUSE, post.getTopicId(), e);
        pausedUntil = clock.instant().plus(ERROR_PAUSE);
      } else {
        log.warn("요약 실패: topic={}", post.getTopicId(), e);
        postRepository.updateSummaryStatus(post.getId(), SummaryStatus.FAILED);
      }
      return true;
    }
    postRepository.saveSummary(
        post.getId(),
        result.getLines(),
        result.getSpecs(),
        result.getCategory(),
        result.getSubcategory(),
        result.getSummarizedBy());
    if (post.getSummaryStatus() == SummaryStatus.DONE) {
      redone.add(post.getId());
    }
    return true;
  }

  /** 글과 상관없는 SDK 오류(HTTP 오류 응답, 연결·시간 초과, 인증 정보 없음). 응답을 읽지 못한 경우는 글 탓으로 본다. */
  private static boolean isApiUnavailable(RuntimeException e) {
    return e instanceof AnthropicException && !(e instanceof AnthropicInvalidDataException);
  }
}

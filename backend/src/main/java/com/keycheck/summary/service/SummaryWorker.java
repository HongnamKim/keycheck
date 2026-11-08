package com.keycheck.summary.service;

import com.anthropic.errors.AnthropicException;
import com.anthropic.errors.AnthropicInvalidDataException;
import com.anthropic.errors.AnthropicServiceException;
import com.keycheck.crawl.config.CollectProperties;
import com.keycheck.post.domain.Board;
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
 * 컬럼(과 분류·세부 분류·공동구매 기간)만 바꿔 그사이 다른 쓰기를 덮지 않는다.
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
  private final CollectProperties collectProperties;
  private final DailySummaryBudget budget;
  private final Clock clock;

  private Instant pausedUntil = Instant.MIN;

  /**
   * 이번 실행에서 이미 요약한 글 ID(요약 대기였던 글 포함). 요약해도 여전히 대상인 글(예: 세부 분류나 상품 이름을 정하지 못한 글)을 계속
   * 요약하지 않도록 건너뛴다. GB 다시 요약 대상(isGbRedo)은 건너뛰지 않는다.
   */
  private final Set<Long> redone = new HashSet<>();

  /**
   * 요약 대상 중 맨 앞 글(요약 대기 글 중 본문을 가장 늦게 채운 글. 미리 읽기로 채우고 아무도 열지 않은 글은 뒤로) 하나를 요약한다. 글 때문에 요약할 수 없으면 FAILED로 두고 다시 시도하지 않는다. 단 GB
   * 다시 요약이 실패하면 요약은 그대로 두고 기간만 비운 뒤 그 제목으로는 다시 요약하지 않는다. 상품 이름을 채우려는 다시 요약이 실패해도 요약은
   * 그대로 두고 그 실행에서는 다시 요약하지 않는다(재시작하면 다시 요약한다). API를 쓸 수 없으면 상태를 그대로 두고
   * ERROR_PAUSE 동안 쉰다. 요약한 글은 한 실행에서 다시 요약하지 않는다(GB 다시 요약은 제목이 바뀔 때마다 한 번). 오늘(한국 시간) Claude
   * 요약기를 부른 횟수(API 오류 응답은 빼고)가 하루 상한에 닿았으면 다음 날까지 쉰다. 가짜 요약기는 세지 않는다.
   *
   * @return 대상이 있었으면 true (쉬는 중이거나 하루 상한에 닿았으면 false)
   */
  public boolean summarizeNext() {
    if (clock.instant().isBefore(pausedUntil)) {
      return false;
    }
    // 가짜 요약기는 세부 분류·공동구매 기간·상품 이름을 정하지 않으므로, 다시 요약하면 Claude 요약만 가짜 요약으로 덮는다
    boolean claude = !(summarizer instanceof FakeSummarizer);
    boolean redoMissingSubcategory = properties.isRedoMissingSubcategory() && claude;
    boolean redoMissingProductName = properties.isRedoMissingProductName() && claude;
    List<Post> targets =
        postRepository.findSummaryTargets(
            collectProperties.getSinceTopicId(),
            properties.isRedoFake(),
            redoMissingSubcategory,
            claude,
            redoMissingProductName,
            Limit.of(redone.size() + 1));
    Post post =
        targets.stream()
            .filter(target -> isGbRedo(target, claude) || !redone.contains(target.getId()))
            .findFirst()
            .orElse(null);
    if (post == null) {
      return false;
    }
    if (claude && !budget.tryAcquire()) {
      // 다음 날 0시(한국 시간)까지 대상을 다시 읽지 않는다. 5초마다 도는 작업이 그동안 DB 전송량을 쓰지 않게
      pausedUntil = budget.nextReset();
      return false;
    }
    boolean gbRedo = isGbRedo(post, claude);
    // 상품 이름을 채우려는 다시 요약(다른 다시 요약 조건에도 걸려 있어도)
    boolean productNameRedo =
        redoMissingProductName
            && post.getSummaryStatus() == SummaryStatus.DONE
            && post.getProductName() == null;
    SummaryResult result;
    try {
      result = summarizer.summarize(
              new PostSource(
                  post.getBoard(),
                  post.getTitle(),
                  post.getBodyText(),
                  post.getPostedAt(),
                  post.getBodyLinks()));
    } catch (RuntimeException e) {
      if (isApiUnavailable(e)) {
        log.error("요약 API를 쓸 수 없어 {} 쉰다: topic={}", ERROR_PAUSE, post.getTopicId(), e);
        pausedUntil = clock.instant().plus(ERROR_PAUSE);
        // 오류 응답(인증·과부하·요청 한도 등)은 과금되지 않으니 하루 상한에서 되돌린다. 연결 실패·시간 초과는 과금됐을 수 있어 센다
        if (claude && e instanceof AnthropicServiceException) {
          budget.release();
        }
      } else if (gbRedo) {
        log.warn("공동구매 기간을 다시 뽑지 못해 요약은 두고 기간만 비운다: topic={}", post.getTopicId(), e);
        postRepository.markGbRedoFailed(post.getId(), post.getTitle());
        redone.add(post.getId());
      } else if (productNameRedo) {
        log.warn("상품 이름을 채우려던 다시 요약에 실패해 요약은 그대로 둔다: topic={}", post.getTopicId(), e);
        redone.add(post.getId());
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
        result.getSummarizedBy(),
        clock.instant(),
        result.getGbStartDate(),
        result.getGbEndDate(),
        result.getProductName(),
        claude && post.getBoard() == Board.GB,
        post.getTitle(),
        result.getVendors());
    redone.add(post.getId());
    return true;
  }

  /**
   * GB 다시 요약 대상: Claude로 돌 때, 요약이 있는 GB 글 중 GB로 기간을 뽑아 본 적이 없거나 요약한 뒤 제목이 바뀐 글. 저장하면(실패해도) 조건이
   * 풀리므로 redone으로 건너뛰지 않는다. 그래서 다시 요약한 뒤 제목이 또 바뀌면 같은 실행에서 또 요약한다.
   */
  private static boolean isGbRedo(Post post, boolean claude) {
    return claude
        && post.getBoard() == Board.GB
        && post.getSummaryStatus() == SummaryStatus.DONE
        && (!post.isGbDatesExtracted() || !post.getTitle().equals(post.getSummarizedTitle()));
  }

  /** 글과 상관없는 SDK 오류(HTTP 오류 응답, 연결·시간 초과, 인증 정보 없음). 응답을 읽지 못한 경우는 글 탓으로 본다. */
  private static boolean isApiUnavailable(RuntimeException e) {
    return e instanceof AnthropicException && !(e instanceof AnthropicInvalidDataException);
  }
}

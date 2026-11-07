package com.keycheck.crawl.service;

import com.keycheck.crawl.config.CollectProperties;
import com.keycheck.crawl.dto.PrefetchResult;
import com.keycheck.post.domain.Post;
import com.keycheck.post.repository.PostRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;

/**
 * 사용자가 열지 않은 틀 글을 하나씩 미리 채운다 (설계 3.3). 우선순위가 가장 낮아 사용자 요청·정기 수집이 쓰지 않는 빈 간격에만
 * geekhack에 요청한다. 채운 글은 요약 작업이 이어서 요약한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PrefetchWorker {
  /** 이만큼 읽지 못한 틀 글은 미리 읽기에서 뺀다. 상세 열기는 계속 채운다. */
  static final int MAX_FAILURES = 3;

  private final PostRepository postRepository;
  private final CrawlService crawlService;
  private final CollectProperties collectProperties;

  /**
   * 대상 중 맨 앞 글(수집 기간 안의 숨기지 않은 틀 글 중 글 번호가 가장 큰 글) 하나를 채운다. 빈 간격이 없으면 그대로 두고, 읽지 못하면
   * 실패 횟수를 늘린다. 실패가 MAX_FAILURES에 닿아 빠지는 글은 경고 로그를 남긴다(geekhack 장애 탓이면 README의 SQL로 되돌린다).
   *
   * @return 대상이 있었으면 true
   */
  public boolean prefetchNext() {
    List<Post> targets =
        postRepository.findPrefetchTargets(
            collectProperties.getSinceTopicId(), MAX_FAILURES, Limit.of(1));
    if (targets.isEmpty()) {
      return false;
    }
    Post post = targets.getFirst();
    if (crawlService.prefetchSkeleton(post) == PrefetchResult.FAILED) {
      postRepository.incrementPrefetchFailures(post.getId());
      if (post.getPrefetchFailures() + 1 >= MAX_FAILURES) {
        log.warn(
            "틀 글을 {}번 읽지 못해 미리 읽기에서 뺀다. geekhack 장애 탓이면 README의 SQL로 되돌린다: topic={}",
            MAX_FAILURES,
            post.getTopicId());
      }
    }
    return true;
  }
}

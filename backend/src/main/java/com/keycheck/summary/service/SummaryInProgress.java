package com.keycheck.summary.service;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * 지금 요약 중인 geekhack 글(처리 중 표시)과 요약을 시작한 시각. 여러 요약 칸이 같은 글을 집지 않게 하고, 상세 응답이 요약 진행 상태를
 * 알려 주는 데 쓴다. 메모리에 두므로 재시작하면 비고, 끊긴 글은 요약 대기(PENDING) 그대로라 다시 집힌다. 상세 응답(post)도 읽으므로 다른
 * 빈에 기대지 않는다.
 */
@Component
public class SummaryInProgress {
  private final Map<Long, Instant> startedAts = new ConcurrentHashMap<>();

  public boolean contains(long postId) {
    return startedAts.containsKey(postId);
  }

  public int size() {
    return startedAts.size();
  }

  public void add(long postId, Instant startedAt) {
    startedAts.put(postId, startedAt);
  }

  public void remove(long postId) {
    startedAts.remove(postId);
  }

  /** 처리 중이면 요약을 시작한 뒤 흐른 밀리초(0 이상), 아니면 null */
  public Long elapsedMs(long postId, Instant now) {
    Instant startedAt = startedAts.get(postId);
    if (startedAt == null) {
      return null;
    }
    return Math.max(0, Duration.between(startedAt, now).toMillis());
  }
}

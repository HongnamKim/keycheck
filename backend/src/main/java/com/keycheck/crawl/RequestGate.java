package com.keycheck.crawl;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.function.Consumer;

/**
 * 모든 geekhack 요청이 공유하는 간격 제한 (설계 4.3).
 *
 * <ul>
 *   <li>사용자 조회: tryAcquire() — 간격이 안 찼으면 즉시 false
 *   <li>정기 수집: acquire() — 간격이 찰 때까지 기다림
 * </ul>
 */
public class RequestGate {
  private final Duration minInterval;
  private final Clock clock;
  private final Consumer<Duration> sleeper;
  private Instant lastAcquiredAt;

  public RequestGate(Duration minInterval, Clock clock) {
    this(minInterval, clock, RequestGate::sleep);
  }

  RequestGate(Duration minInterval, Clock clock, Consumer<Duration> sleeper) {
    this.minInterval = minInterval;
    this.clock = clock;
    this.sleeper = sleeper;
  }

  public synchronized boolean tryAcquire() {
    if (!remainingWait().isZero()) {
      return false;
    }
    lastAcquiredAt = clock.instant();
    return true;
  }

  public void acquire() {
    while (true) {
      Duration wait;
      synchronized (this) {
        wait = remainingWait();
        if (wait.isZero()) {
          lastAcquiredAt = clock.instant();
          return;
        }
      }
      sleeper.accept(wait);
    }
  }

  private Duration remainingWait() {
    if (lastAcquiredAt == null) {
      return Duration.ZERO;
    }
    Duration elapsed = Duration.between(lastAcquiredAt, clock.instant());
    return elapsed.compareTo(minInterval) >= 0 ? Duration.ZERO : minInterval.minus(elapsed);
  }

  private static void sleep(Duration duration) {
    try {
      Thread.sleep(duration);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException(e);
    }
  }
}

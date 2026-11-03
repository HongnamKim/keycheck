package com.keycheck.crawl.client;

import static org.assertj.core.api.Assertions.assertThat;

import com.keycheck.MutableClock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class RequestGateTest {
  private final MutableClock clock = new MutableClock();
  private final List<Duration> sleeps = new ArrayList<>();
  private final RequestGate gate =
      new RequestGate(
          Duration.ofSeconds(2),
          clock,
          duration -> {
            sleeps.add(duration);
            clock.advance(duration);
          });

  @Test
  @DisplayName("tryAcquire는 간격이 찼을 때만 통과한다")
  void tryAcquirePassesOnlyAfterInterval() {
    assertThat(gate.tryAcquire()).isTrue();

    clock.advance(Duration.ofMillis(1999));
    assertThat(gate.tryAcquire()).isFalse();

    clock.advance(Duration.ofMillis(1));
    assertThat(gate.tryAcquire()).isTrue();
  }

  @Test
  @DisplayName("acquire는 남은 시간만큼 기다린 뒤 통과한다")
  void acquireWaitsForRemainingTime() {
    gate.acquire();
    assertThat(sleeps).isEmpty();

    clock.advance(Duration.ofMillis(500));
    gate.acquire();
    assertThat(sleeps).containsExactly(Duration.ofMillis(1500));
  }

  @Test
  @DisplayName("tryAcquire(maxWait)는 간격이 찼으면 기다리지 않고 통과한다")
  void tryAcquireWithWaitPassesImmediately() {
    assertThat(gate.tryAcquire(Duration.ofSeconds(3))).isTrue();
    assertThat(sleeps).isEmpty();
  }

  @Test
  @DisplayName("tryAcquire(maxWait)는 남은 시간이 maxWait 이내면 기다린 뒤 통과한다")
  void tryAcquireWithWaitWaitsWithinLimit() {
    gate.acquire();
    clock.advance(Duration.ofMillis(500));

    assertThat(gate.tryAcquire(Duration.ofMillis(1500))).isTrue();
    assertThat(sleeps).containsExactly(Duration.ofMillis(1500));
  }

  @Test
  @DisplayName("tryAcquire(maxWait)는 남은 시간이 maxWait보다 길면 기다리지 않고 false")
  void tryAcquireWithWaitGivesUpBeyondLimit() {
    gate.acquire();
    clock.advance(Duration.ofMillis(500));

    assertThat(gate.tryAcquire(Duration.ofMillis(1499))).isFalse();
    assertThat(sleeps).isEmpty();
    assertThat(gate.tryAcquire()).isFalse();
  }

  @Test
  @DisplayName("acquire가 통과하면 그 뒤 tryAcquire는 막힌다")
  void tryAcquireBlockedAfterAcquire() {
    gate.acquire();

    assertThat(gate.tryAcquire()).isFalse();
  }
}

package com.keycheck.crawl.client;

import static org.assertj.core.api.Assertions.assertThat;

import com.keycheck.MutableClock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
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

  @Test
  @DisplayName("tryAcquireIdle은 마지막 획득 뒤 간격의 2배가 지나야 통과한다")
  void idleNeedsTwiceInterval() {
    gate.acquire();

    clock.advance(Duration.ofMillis(3999));
    assertThat(gate.tryAcquireIdle()).isFalse();

    clock.advance(Duration.ofMillis(1));
    assertThat(gate.tryAcquireIdle()).isTrue();
    assertThat(gate.tryAcquire()).isFalse();
  }

  @Test
  @DisplayName("처음 부른 tryAcquireIdle은 통과한다")
  void idleFirstCallPasses() {
    assertThat(gate.tryAcquireIdle()).isTrue();
  }

  /** 기다리는 동안 시계를 5초(간격의 2배 넘게) 밀고 tryAcquireIdle()을 불러 결과를 idleResults에 남기는 게이트 */
  private RequestGate gateCallingIdleWhileWaiting(List<Boolean> idleResults) {
    AtomicReference<RequestGate> waitingGate = new AtomicReference<>();
    waitingGate.set(
        new RequestGate(
            Duration.ofSeconds(2),
            clock,
            duration -> {
              // 기다리는 사이 간격의 2배가 지나도 백그라운드가 끼어들지 못해야 한다
              assertThat(idleResults).as("대기는 한 번만").isEmpty();
              clock.advance(Duration.ofSeconds(5));
              idleResults.add(waitingGate.get().tryAcquireIdle());
            }));
    return waitingGate.get();
  }

  @Test
  @DisplayName("기다리는 요청이 있으면 간격이 비어 있어도 tryAcquireIdle은 false")
  void idleYieldsToWaiter() {
    List<Boolean> idleResults = new ArrayList<>();
    RequestGate waitingGate = gateCallingIdleWhileWaiting(idleResults);

    waitingGate.acquire();
    waitingGate.acquire();

    assertThat(idleResults).containsExactly(false);
    assertThat(waitingGate.tryAcquire()).isFalse();

    // 기다리던 요청이 지나가면 다시 빈 간격을 쓸 수 있다
    clock.advance(Duration.ofSeconds(4));
    assertThat(waitingGate.tryAcquireIdle()).isTrue();
  }

  @Test
  @DisplayName("tryAcquire(maxWait)로 기다리는 사용자 요청이 있어도 tryAcquireIdle은 false이고 기다리던 요청이 통과한다")
  void idleYieldsToTimedWaiter() {
    List<Boolean> idleResults = new ArrayList<>();
    RequestGate waitingGate = gateCallingIdleWhileWaiting(idleResults);

    waitingGate.acquire();
    assertThat(waitingGate.tryAcquire(Duration.ofSeconds(10))).isTrue();

    assertThat(idleResults).containsExactly(false);
    assertThat(waitingGate.tryAcquire()).isFalse();
  }

  @Test
  @DisplayName("기다림을 포기한 tryAcquire(maxWait)는 그 뒤 tryAcquireIdle을 막지 않는다")
  void idlePassesAfterWaiterGivesUp() {
    gate.acquire();
    clock.advance(Duration.ofMillis(500));
    assertThat(gate.tryAcquire(Duration.ofMillis(1499))).isFalse();

    clock.advance(Duration.ofSeconds(4));
    assertThat(gate.tryAcquireIdle()).isTrue();
  }
}

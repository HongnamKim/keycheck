package com.keycheck.summary.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SummaryInProgressTest {
  private static final Instant STARTED_AT = Instant.parse("2026-11-01T00:00:00Z");

  private final SummaryInProgress inProgress = new SummaryInProgress();

  @Test
  @DisplayName("올린 글은 처리 중이고, 내린 글은 처리 중이 아니다")
  void addContainsRemove() {
    inProgress.add(1L, STARTED_AT);

    assertThat(inProgress.contains(1L)).isTrue();
    assertThat(inProgress.size()).isEqualTo(1);

    inProgress.remove(1L);

    assertThat(inProgress.contains(1L)).isFalse();
    assertThat(inProgress.size()).isZero();
  }

  @Test
  @DisplayName("처리 중이 아닌 글은 흐른 시간이 null이다")
  void elapsedMsNullWhenAbsent() {
    assertThat(inProgress.elapsedMs(1L, STARTED_AT)).isNull();
  }

  @Test
  @DisplayName("처리 중인 글은 요약을 시작한 뒤 흐른 밀리초를 돌려준다")
  void elapsedMsSinceStart() {
    inProgress.add(1L, STARTED_AT);

    assertThat(inProgress.elapsedMs(1L, STARTED_AT.plusMillis(1500))).isEqualTo(1500L);
  }

  @Test
  @DisplayName("시작 시각보다 이른 시각으로 물어도 흐른 시간은 0보다 작지 않다")
  void elapsedMsNeverNegative() {
    inProgress.add(1L, STARTED_AT);

    assertThat(inProgress.elapsedMs(1L, STARTED_AT.minusSeconds(1))).isEqualTo(0L);
  }
}

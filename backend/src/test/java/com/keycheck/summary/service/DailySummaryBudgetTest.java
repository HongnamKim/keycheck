package com.keycheck.summary.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.keycheck.MutableClock;
import com.keycheck.summary.config.SummaryProperties;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class DailySummaryBudgetTest {
  private final MutableClock clock = new MutableClock();

  private DailySummaryBudget budget(int dailyLimit) {
    return new DailySummaryBudget(
        new SummaryProperties(false, false, false, dailyLimit, 1), clock);
  }

  @Test
  @DisplayName("한도까지만 센다")
  void countsOnlyUpToLimit() {
    DailySummaryBudget budget = budget(2);

    assertThat(budget.tryAcquire()).isTrue();
    assertThat(budget.tryAcquire()).isTrue();
    assertThat(budget.tryAcquire()).isFalse();
  }

  @Test
  @DisplayName("한국 날짜가 바뀌면 다시 센다")
  void resetsWhenKoreanDateChanges() {
    clock.set(Instant.parse("2026-11-01T14:59:59Z"));
    DailySummaryBudget budget = budget(1);
    assertThat(budget.tryAcquire()).isTrue();
    assertThat(budget.tryAcquire()).isFalse();

    clock.advance(Duration.ofSeconds(1));

    assertThat(budget.tryAcquire()).isTrue();
  }

  @Test
  @DisplayName("되돌리면 하나 더 셀 수 있다")
  void releaseAllowsOneMore() {
    DailySummaryBudget budget = budget(1);
    assertThat(budget.tryAcquire()).isTrue();
    assertThat(budget.tryAcquire()).isFalse();

    budget.release();

    assertThat(budget.tryAcquire()).isTrue();
  }

  @Test
  @DisplayName("nextReset은 다음 한국 0시")
  void nextResetIsNextKoreanMidnight() {
    assertThat(budget(1).nextReset()).isEqualTo(Instant.parse("2026-11-01T15:00:00Z"));
  }
}

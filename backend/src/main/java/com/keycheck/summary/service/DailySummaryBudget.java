package com.keycheck.summary.service;

import com.keycheck.summary.config.SummaryProperties;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/** Claude 요약기를 하루(한국 시간)에 부를 수 있는 횟수를 세는 부품. 요약 작업들이 같이 쓴다. */
@Slf4j
@Component
@RequiredArgsConstructor
public class DailySummaryBudget {
  /** 하루 요약 상한을 세는 날짜의 시간대 */
  private static final ZoneId DAILY_LIMIT_ZONE = ZoneId.of("Asia/Seoul");

  private final SummaryProperties properties;
  private final Clock clock;

  /** Claude 요약기를 부른 횟수를 세는 날짜(한국 시간)와 그날 부른 횟수, 상한 경고를 남겼는지 */
  private LocalDate countedDate;
  private int countedToday;
  private boolean limitWarned;

  /**
   * 오늘(한국 시간) Claude 요약기를 부른 횟수가 하루 상한보다 적으면 한 번을 센다. 상한에 닿으면 그날 처음 한 번만 경고를 남긴다.
   *
   * @return 세었으면 true, 상한에 닿았으면 false
   */
  public synchronized boolean tryAcquire() {
    LocalDate today = LocalDate.ofInstant(clock.instant(), DAILY_LIMIT_ZONE);
    if (!today.equals(countedDate)) {
      countedDate = today;
      countedToday = 0;
      limitWarned = false;
    }
    if (countedToday >= properties.getDailyLimit()) {
      if (!limitWarned) {
        log.warn("오늘(한국 시간) 요약 상한 {}건에 닿아 다음 날까지 요약하지 않는다", properties.getDailyLimit());
        limitWarned = true;
      }
      return false;
    }
    countedToday++;
    return true;
  }

  /** tryAcquire로 센 한 번을 되돌린다 */
  public synchronized void release() {
    if (countedToday > 0) {
      countedToday--;
    }
  }

  /** 다음 날 0시(한국 시간) */
  public Instant nextReset() {
    return LocalDate.ofInstant(clock.instant(), DAILY_LIMIT_ZONE)
        .plusDays(1)
        .atStartOfDay(DAILY_LIMIT_ZONE)
        .toInstant();
  }
}

package com.keycheck;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

/** 테스트에서 시간을 직접 움직이는 Clock */
public class MutableClock extends Clock {
  private Instant now = Instant.parse("2026-11-01T00:00:00Z");

  public void advance(Duration duration) {
    now = now.plus(duration);
  }

  /** 빈 하나를 테스트끼리 함께 쓰므로, 테스트마다 시각을 정해 앞 테스트가 움직인 시각과 상관없게 한다 */
  public void set(Instant instant) {
    now = instant;
  }

  @Override
  public Instant instant() {
    return now;
  }

  @Override
  public ZoneId getZone() {
    return ZoneOffset.UTC;
  }

  @Override
  public Clock withZone(ZoneId zone) {
    return this;
  }
}

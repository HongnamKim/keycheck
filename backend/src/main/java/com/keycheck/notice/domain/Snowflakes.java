package com.keycheck.notice.domain;

import java.time.Instant;

/** 디스코드 id(snowflake)에서 만든 시각을 구한다 */
public final class Snowflakes {
  /** 디스코드 기준 시각(2015-01-01T00:00:00Z)의 epoch 밀리초 */
  private static final long DISCORD_EPOCH_MILLIS = 1420070400000L;

  private Snowflakes() {}

  public static Instant time(long id) {
    return Instant.ofEpochMilli((id >> 22) + DISCORD_EPOCH_MILLIS);
  }
}

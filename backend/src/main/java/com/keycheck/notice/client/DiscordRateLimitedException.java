package com.keycheck.notice.client;

import java.time.Duration;
import lombok.Getter;

/** 디스코드가 요청이 너무 많다(429)고 한 경우. retryAfter 뒤에 다시 시도한다 */
@Getter
public class DiscordRateLimitedException extends RuntimeException {
  private final Duration retryAfter;

  public DiscordRateLimitedException(Duration retryAfter) {
    super("디스코드 요청 한도 초과, " + retryAfter.toMillis() + "ms 뒤 재시도");
    this.retryAfter = retryAfter;
  }
}

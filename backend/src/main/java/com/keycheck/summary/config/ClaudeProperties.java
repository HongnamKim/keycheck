package com.keycheck.summary.config;

import java.time.Duration;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@RequiredArgsConstructor
@ConfigurationProperties("keycheck.claude")
public class ClaudeProperties {
  /** 모델 ID (예: claude-sonnet-5-5, claude-haiku-5-5) */
  private final String model;

  /** low | medium | high | xhigh | max */
  private final String effort;

  /** 생각(thinking)과 답을 합친 최대 출력 토큰 */
  private final long maxTokens;

  /** 요청 하나의 시간 제한 */
  private final Duration timeout;

  /** 분류기 거절 시 서버 쪽 대체 모델 재시도 (fallbacks "default") */
  private final boolean fallbacks;
}

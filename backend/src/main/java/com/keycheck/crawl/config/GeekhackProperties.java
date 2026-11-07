package com.keycheck.crawl.config;

import java.time.Duration;
import java.time.ZoneId;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@RequiredArgsConstructor
@ConfigurationProperties("keycheck.geekhack")
public class GeekhackProperties {
  private final String baseUrl;
  private final String userAgent;
  private final Duration minInterval;

  /** geekhack이 비로그인 사용자에게 시각을 표시하는 시간대 */
  private final ZoneId timeZone;
}

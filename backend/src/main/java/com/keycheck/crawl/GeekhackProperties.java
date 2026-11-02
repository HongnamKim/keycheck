package com.keycheck.crawl;

import java.time.Duration;
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
}

package com.keycheck.image.config;

import com.keycheck.crawl.config.GeekhackProperties;
import com.keycheck.image.client.ImageDownloader;
import java.time.Duration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ImageConfig {
  /** 이미지 호스트는 느릴 때가 있다(postimg 3~9초, 큰 geekhack 첨부 약 8초). 백그라운드 작업이라 넉넉히 기다린다. */
  private static final Duration TIMEOUT = Duration.ofSeconds(15);

  /** 한 이미지 받기 전체(리다이렉트 포함)의 상한 */
  private static final Duration TOTAL_TIMEOUT = Duration.ofMinutes(1);

  /** geekhack 첨부도 받으므로 geekhack 요청과 같은 User-Agent를 쓴다 */
  @Bean
  public ImageDownloader imageDownloader(GeekhackProperties properties) {
    return new ImageDownloader(properties.getUserAgent(), TIMEOUT, TOTAL_TIMEOUT);
  }
}

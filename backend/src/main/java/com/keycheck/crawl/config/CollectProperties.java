package com.keycheck.crawl.config;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@RequiredArgsConstructor
@ConfigurationProperties("keycheck.collect")
public class CollectProperties {
  /** 수집 기간 경계. 글 번호가 이 값 이상인 글만 모으고 보여 준다(2020-01-01 무렵 첫 글 번호) */
  private final long sinceTopicId;
}

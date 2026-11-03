package com.keycheck.summary.config;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@RequiredArgsConstructor
@ConfigurationProperties("keycheck.summary")
public class SummaryProperties {
  /** true면 가짜 요약기로 만든 요약(summarized_by = 'fake')도 다시 요약한다 */
  private final boolean redoFake;

  /**
   * true면 세부 분류가 없는 키보드·키캡 요약(Claude 요약 포함)도 다시 요약한다. 한 번 실행하는 동안 글마다 한 번만 다시 요약한다.
   */
  private final boolean redoMissingSubcategory;
}

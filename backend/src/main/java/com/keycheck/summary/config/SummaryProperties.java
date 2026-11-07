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

  /**
   * true면 상품 이름이 없는 요약 완료 글(Claude 요약 포함)도 다시 요약한다. 한 번 실행하는 동안 글마다 한 번만 다시 요약한다. 글당 약
   * 1.1센트이므로 켜기 전에 확인한다.
   */
  private final boolean redoMissingProductName;

  /**
   * 하루(한국 시간 자정 기준)에 Claude 요약기를 부르는 상한. 글 탓 실패와 연결 실패·시간 초과는 세고, 과금되지 않는 API 오류 응답(인증·과부하·요청
   * 한도 등)은 세지 않는다. 닿으면 다음 날까지 쉰다. 메모리에서 세므로 재시작하면 0부터 센다. 가짜 요약기에서는 무시.
   */
  private final int dailyLimit;
}

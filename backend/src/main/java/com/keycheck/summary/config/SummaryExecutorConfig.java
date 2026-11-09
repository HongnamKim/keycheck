package com.keycheck.summary.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
@ConditionalOnProperty(name = "keycheck.summary.enabled", havingValue = "true")
public class SummaryExecutorConfig {
  /**
   * 요약 칸(SummarySlots)을 돌리는 전용 스레드 풀. 정기 작업 스케줄러는 남는 스레드가 없어 쓰지 않는다. 칸 수만큼만 받고 줄을 세우지
   * 않는다. 종료할 때 진행 중인 요약을 기다리지 않고 스레드를 깨운다(끊긴 글은 요약 대기 그대로라 다음 기동 뒤 다시 집힌다). 기본 후보에서
   * 빼서, 이 빈 때문에 Spring Boot의 기본 작업 실행기(applicationTaskExecutor)가 물러나지 않게 한다.
   */
  @Bean(defaultCandidate = false)
  public ThreadPoolTaskExecutor summaryExecutor(SummaryProperties properties) {
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.setCorePoolSize(properties.getConcurrency());
    executor.setMaxPoolSize(properties.getConcurrency());
    executor.setQueueCapacity(0);
    executor.setThreadNamePrefix("summary-");
    executor.setWaitForTasksToCompleteOnShutdown(false);
    // false(기본)면 종료 단계에서 진행 중인 작업이 끝나기를 단계 제한 시간(Spring Boot 기본 30초)까지 기다린 뒤에야
    // shutdownNow로 깨운다. true면 그 단계를 건너뛰고 소멸할 때 곧바로 깨운다. 종료 뒤 새 칸은 SummarySlots가 열지 않는다
    executor.setAcceptTasksAfterContextClose(true);
    return executor;
  }
}

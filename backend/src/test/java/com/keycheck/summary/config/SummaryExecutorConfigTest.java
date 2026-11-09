package com.keycheck.summary.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

class SummaryExecutorConfigTest {
  @Test
  @DisplayName("컨텍스트를 닫을 때 진행 중인 요약을 기다리지 않고 스레드를 깨운다")
  void closeInterruptsRunningSlotWithoutWaiting() throws Exception {
    GenericApplicationContext context = new GenericApplicationContext();
    context.registerBean(
        "summaryExecutor",
        ThreadPoolTaskExecutor.class,
        () ->
            new SummaryExecutorConfig()
                .summaryExecutor(new SummaryProperties(false, false, false, 300, 2)));
    context.refresh();
    CountDownLatch started = new CountDownLatch(1);
    CountDownLatch interrupted = new CountDownLatch(1);
    context
        .getBean(ThreadPoolTaskExecutor.class)
        .execute(
            () -> {
              started.countDown();
              try {
                // 오래 걸리는 Claude 호출 대신
                Thread.sleep(60_000);
              } catch (InterruptedException e) {
                interrupted.countDown();
              }
            });
    assertThat(started.await(5, TimeUnit.SECONDS)).isTrue();

    long closeStartedAt = System.nanoTime();
    context.close();
    long closeMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - closeStartedAt);

    // 종료 단계 제한 시간(기본 10초, 운영 Spring Boot 30초)까지 기다리지 않는다
    assertThat(closeMs).isLessThan(2_000);
    assertThat(interrupted.await(5, TimeUnit.SECONDS)).isTrue();
  }
}

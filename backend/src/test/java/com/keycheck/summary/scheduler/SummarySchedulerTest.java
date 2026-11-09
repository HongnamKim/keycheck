package com.keycheck.summary.scheduler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import com.keycheck.TestcontainersConfiguration;
import com.keycheck.summary.service.SummaryWorker;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest(properties = {"keycheck.summary.enabled=true", "keycheck.summary.interval=100ms"})
@Import(TestcontainersConfiguration.class)
class SummarySchedulerTest {
  @MockitoBean private SummaryWorker worker;
  @Autowired private ApplicationContext context;

  @Test
  @DisplayName("켜져 있으면 주기마다 요약 칸을 열어 요약 대기 글을 요약한다")
  void runsWorkerPeriodically() {
    verify(worker, timeout(5000).atLeast(2)).summarizeNext(any(Runnable.class));
  }

  @Test
  @DisplayName("요약 전용 스레드 풀이 있어도 기본 작업 실행기(applicationTaskExecutor)는 물러나지 않는다")
  void keepsApplicationTaskExecutor() {
    assertThat(context.containsBean("summaryExecutor")).isTrue();
    assertThat(context.containsBean("applicationTaskExecutor")).isTrue();
  }
}

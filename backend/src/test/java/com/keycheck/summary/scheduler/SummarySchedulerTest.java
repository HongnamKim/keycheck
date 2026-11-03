package com.keycheck.summary.scheduler;

import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import com.keycheck.TestcontainersConfiguration;
import com.keycheck.summary.service.SummaryWorker;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest(properties = {"keycheck.summary.enabled=true", "keycheck.summary.interval=100ms"})
@Import(TestcontainersConfiguration.class)
class SummarySchedulerTest {
  @MockitoBean private SummaryWorker worker;

  @Test
  @DisplayName("켜져 있으면 주기마다 요약 대기 글을 하나씩 요약한다")
  void runsWorkerPeriodically() {
    verify(worker, timeout(5000).atLeast(2)).summarizeNext();
  }
}

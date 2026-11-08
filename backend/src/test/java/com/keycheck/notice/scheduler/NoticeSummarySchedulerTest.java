package com.keycheck.notice.scheduler;

import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import com.keycheck.TestcontainersConfiguration;
import com.keycheck.notice.service.NoticeSummaryWorker;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest(
    properties = {"keycheck.notice.summary.enabled=true", "keycheck.notice.summary.interval=100ms"})
@Import(TestcontainersConfiguration.class)
class NoticeSummarySchedulerTest {
  @MockitoBean private NoticeSummaryWorker worker;

  @Test
  @DisplayName("켜져 있으면 주기마다 공지를 하나씩 요약한다")
  void 켜져_있으면_주기마다_공지를_하나씩_요약한다() {
    verify(worker, timeout(5000).atLeast(2)).summarizeNext();
  }
}

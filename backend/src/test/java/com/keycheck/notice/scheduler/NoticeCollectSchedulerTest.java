package com.keycheck.notice.scheduler;

import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import com.keycheck.TestcontainersConfiguration;
import com.keycheck.notice.service.NoticeCollector;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest(properties = {"keycheck.notice.enabled=true", "keycheck.notice.interval=100ms"})
@Import(TestcontainersConfiguration.class)
class NoticeCollectSchedulerTest {
  @MockitoBean private NoticeCollector collector;

  @Test
  @DisplayName("켜져 있으면 주기마다 수집한다")
  void 켜져_있으면_주기마다_수집한다() {
    verify(collector, timeout(5000).atLeast(2)).collect();
  }
}

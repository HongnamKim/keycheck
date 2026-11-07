package com.keycheck.crawl.scheduler;

import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import com.keycheck.TestcontainersConfiguration;
import com.keycheck.crawl.service.PrefetchWorker;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest(properties = {"keycheck.prefetch.enabled=true", "keycheck.prefetch.interval=100ms"})
@Import(TestcontainersConfiguration.class)
class PrefetchSchedulerTest {
  // 실제 geekhack을 호출하지 않도록 PrefetchWorker를 대체한다
  @MockitoBean private PrefetchWorker worker;

  @Test
  @DisplayName("켜져 있으면 주기마다 틀 글을 하나씩 미리 읽는다")
  void runsWorkerPeriodically() {
    verify(worker, timeout(5000).atLeast(2)).prefetchNext();
  }
}

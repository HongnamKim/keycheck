package com.keycheck.crawl.scheduler;

import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import com.keycheck.TestcontainersConfiguration;
import com.keycheck.crawl.service.CrawlService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest(properties = "keycheck.crawl.enabled=true")
@Import(TestcontainersConfiguration.class)
class CrawlSchedulerTest {
  // 실제 geekhack을 호출하지 않도록 CrawlService를 대체한다
  @MockitoBean private CrawlService crawlService;

  @Test
  @DisplayName("켜져 있으면 기동 직후 수집을 실행한다")
  void crawlsOnStartupWhenEnabled() {
    verify(crawlService, timeout(5000).atLeastOnce()).crawl();
  }
}

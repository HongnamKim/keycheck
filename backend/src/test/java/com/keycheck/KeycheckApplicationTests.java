package com.keycheck;

import static org.assertj.core.api.Assertions.assertThat;

import com.keycheck.crawl.scheduler.CrawlScheduler;
import com.keycheck.summary.service.FakeSummarizer;
import com.keycheck.summary.service.Summarizer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class KeycheckApplicationTests {
  @Autowired private ApplicationContext context;

  @Test
  void contextLoads() {}

  @Test
  @DisplayName("기본 설정에서는 수집 스케줄러가 없어 실제 geekhack에 요청하지 않는다")
  void crawlSchedulerIsOffByDefault() {
    assertThat(context.getBeansOfType(CrawlScheduler.class)).isEmpty();
  }

  @Test
  @DisplayName("테스트에서는 가짜 요약기만 있어 실제 Claude API를 호출하지 않는다")
  void summarizerIsFake() {
    assertThat(context.getBeansOfType(Summarizer.class).values())
        .singleElement()
        .isInstanceOf(FakeSummarizer.class);
  }
}

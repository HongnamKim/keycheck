package com.keycheck.crawl.scheduler;

import com.keycheck.crawl.service.CrawlService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 실제 geekhack에 요청을 보내므로 keycheck.crawl.enabled=true일 때만 동작한다. 기동 직후 1회 실행된다. */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "keycheck.crawl.enabled", havingValue = "true")
public class CrawlScheduler {
  private final CrawlService crawlService;

  @Scheduled(fixedDelayString = "${keycheck.crawl.interval}")
  public void run() {
    crawlService.crawl();
  }
}

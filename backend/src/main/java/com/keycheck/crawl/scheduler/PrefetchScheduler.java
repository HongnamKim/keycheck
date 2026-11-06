package com.keycheck.crawl.scheduler;

import com.keycheck.crawl.service.PrefetchWorker;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 실제 geekhack에 요청하므로 keycheck.prefetch.enabled=true일 때만 interval마다 틀 글을 하나씩 미리 읽는다 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "keycheck.prefetch.enabled", havingValue = "true")
public class PrefetchScheduler {
  private final PrefetchWorker worker;

  @Scheduled(fixedDelayString = "${keycheck.prefetch.interval}")
  public void run() {
    worker.prefetchNext();
  }
}

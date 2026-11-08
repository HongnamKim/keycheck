package com.keycheck.notice.scheduler;

import com.keycheck.notice.service.NoticeSummaryWorker;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** keycheck.notice.summary.enabled=true일 때 interval마다 공지를 하나씩 요약한다 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "keycheck.notice.summary.enabled", havingValue = "true")
public class NoticeSummaryScheduler {
  private final NoticeSummaryWorker worker;

  @Scheduled(fixedDelayString = "${keycheck.notice.summary.interval}")
  public void run() {
    worker.summarizeNext();
  }
}

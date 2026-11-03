package com.keycheck.summary.scheduler;

import com.keycheck.summary.service.SummaryWorker;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** keycheck.summary.enabled=true일 때 interval마다 요약 대기 글을 하나씩 요약한다 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "keycheck.summary.enabled", havingValue = "true")
public class SummaryScheduler {
  private final SummaryWorker worker;

  @Scheduled(fixedDelayString = "${keycheck.summary.interval}")
  public void run() {
    worker.summarizeNext();
  }
}

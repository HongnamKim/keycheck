package com.keycheck.summary.scheduler;

import com.keycheck.summary.service.SummarySlots;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * keycheck.summary.enabled=true일 때 interval마다 빈 요약 칸이 있으면 하나를 연다. 열린 칸은 요약 전용 스레드에서 대상이 없을 때까지 요약
 * 대기 글을 이어서 요약하므로, 이 작업은 스케줄러 스레드를 붙잡지 않는다.
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "keycheck.summary.enabled", havingValue = "true")
public class SummaryScheduler {
  private final SummarySlots slots;

  @Scheduled(fixedDelayString = "${keycheck.summary.interval}")
  public void run() {
    slots.openIfFree();
  }
}

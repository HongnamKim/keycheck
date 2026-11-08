package com.keycheck.notice.scheduler;

import com.keycheck.notice.service.NoticeCollector;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 실제 디스코드에 요청을 보내므로 keycheck.notice.enabled=true일 때만 동작한다. */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "keycheck.notice.enabled", havingValue = "true")
public class NoticeCollectScheduler {
  private final NoticeCollector collector;

  @Scheduled(fixedDelayString = "${keycheck.notice.interval}")
  public void run() {
    collector.collect();
  }
}

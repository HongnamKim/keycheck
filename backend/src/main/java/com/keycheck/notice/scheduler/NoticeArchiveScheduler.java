package com.keycheck.notice.scheduler;

import com.keycheck.notice.service.NoticeArchiver;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** keycheck.notice.enabled=true일 때 interval마다 첨부를 하나씩 보관한다. 저장소 설정이 없으면 아무것도 하지 않는다. */
@Slf4j
@Component
@ConditionalOnProperty(name = "keycheck.notice.enabled", havingValue = "true")
public class NoticeArchiveScheduler {
  private final NoticeArchiver archiver;

  public NoticeArchiveScheduler(ObjectProvider<NoticeArchiver> archiver) {
    this.archiver = archiver.getIfAvailable();
    if (this.archiver == null) {
      log.warn("공지 수집이 켜져 있지만 저장소(R2_*) 설정이 없어 첨부를 보관하지 않는다");
    }
  }

  @Scheduled(fixedDelayString = "${keycheck.notice.archive.interval}")
  public void run() {
    if (archiver != null) {
      archiver.archiveNext();
    }
  }
}

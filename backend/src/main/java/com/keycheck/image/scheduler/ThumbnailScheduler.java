package com.keycheck.image.scheduler;

import com.keycheck.image.config.StorageProperties;
import com.keycheck.image.service.ThumbnailWorker;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 실제 이미지 호스트와 저장소(R2)에 요청하므로 keycheck.thumbnail.enabled=true일 때만 interval마다 이미지를 하나씩 축소본으로 만든다.
 * 저장소 설정이 없으면(축소본 작업 빈이 없으면) 아무것도 하지 않는다.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "keycheck.thumbnail.enabled", havingValue = "true")
public class ThumbnailScheduler {
  private final ThumbnailWorker worker;

  public ThumbnailScheduler(ObjectProvider<ThumbnailWorker> worker, StorageProperties storage) {
    this.worker = worker.getIfAvailable();
    if (this.worker == null) {
      log.warn("축소본 작업이 켜져 있지만 저장소(R2_*) 설정이 없어 축소본을 만들지 않는다");
    } else if (!StringUtils.hasText(storage.getPublicUrl())) {
      // 축소본은 만들어 두되(나중에 공개 주소를 넣으면 바로 쓴다) 지금은 화면에 쓰이지 않는다는 것을 알린다
      log.warn("공개 주소(R2_PUBLIC_URL)가 비어 있어 축소본을 만들어도 화면은 원본을 쓴다");
    }
  }

  @Scheduled(fixedDelayString = "${keycheck.thumbnail.interval}")
  public void run() {
    if (worker != null) {
      worker.makeNext();
    }
  }
}

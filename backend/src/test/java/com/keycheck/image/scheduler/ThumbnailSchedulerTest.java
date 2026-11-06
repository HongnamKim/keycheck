package com.keycheck.image.scheduler;

import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import com.keycheck.TestcontainersConfiguration;
import com.keycheck.image.service.ThumbnailWorker;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest(
    properties = {"keycheck.thumbnail.enabled=true", "keycheck.thumbnail.interval=100ms"})
@Import(TestcontainersConfiguration.class)
class ThumbnailSchedulerTest {
  // 실제 이미지 호스트·R2를 부르지 않도록 ThumbnailWorker를 대체한다(테스트에는 저장소 설정이 없어 원래 빈도 없다)
  @MockitoBean private ThumbnailWorker worker;

  @Test
  @DisplayName("켜져 있고 저장소가 있으면 주기마다 이미지를 하나씩 축소본으로 만든다")
  void runsWorkerPeriodically() {
    verify(worker, timeout(5000).atLeast(2)).makeNext();
  }
}

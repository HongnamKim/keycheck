package com.keycheck.image.scheduler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.keycheck.image.config.StorageProperties;
import com.keycheck.image.service.ThumbnailWorker;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

/** 켤 때 설정이 모자라면 경고를 남긴다. 작업을 실행하지 않는다. */
@ExtendWith(OutputCaptureExtension.class)
class ThumbnailSchedulerWarningTest {
  private static ObjectProvider<ThumbnailWorker> worker() {
    return new StaticListableBeanFactory(Map.of("thumbnailWorker", mock(ThumbnailWorker.class)))
        .getBeanProvider(ThumbnailWorker.class);
  }

  private static StorageProperties storage(String publicUrl) {
    return new StorageProperties(
        "http://127.0.0.1:9", "keycheck-test", "test-access-key-id", "test-secret", publicUrl);
  }

  @Test
  @DisplayName("저장소는 있지만 공개 주소가 비었으면 축소본을 만들어도 화면은 원본을 쓴다고 경고한다")
  void warnsWhenPublicUrlBlank(CapturedOutput output) {
    new ThumbnailScheduler(worker(), storage(""));

    assertThat(output).contains("R2_PUBLIC_URL");
  }

  @Test
  @DisplayName("공개 주소가 있으면 경고하지 않는다")
  void noWarningWithPublicUrl(CapturedOutput output) {
    new ThumbnailScheduler(worker(), storage("https://img.test"));

    assertThat(output).doesNotContain("R2_PUBLIC_URL");
  }
}

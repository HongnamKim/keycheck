package com.keycheck.image.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.keycheck.TestcontainersConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

/**
 * 저장소 접속 정보가 있으면 앱이 축소본 작업을 갖고 뜬다. 작업은 켜지 않아(keycheck.thumbnail.enabled=false) 저장소에 요청하지 않는다.
 * 접속 정보가 없을 때 작업 없이 뜨는 것은 다른 모든 앱 테스트가 본다.
 */
@SpringBootTest(
    properties = {
      "keycheck.storage.endpoint=http://127.0.0.1:9",
      "keycheck.storage.bucket=keycheck-test",
      "keycheck.storage.access-key-id=test-access-key-id",
      "keycheck.storage.secret-access-key=test-secret-access-key"
    })
@Import(TestcontainersConfiguration.class)
class ThumbnailWorkerWiringTest {
  @Autowired private ObjectProvider<ThumbnailWorker> worker;

  @Test
  @DisplayName("저장소 접속 정보가 있으면 축소본 작업을 만든다")
  void createsWorkerWhenStorageConfigured() {
    assertThat(worker.getIfAvailable()).isNotNull();
  }
}

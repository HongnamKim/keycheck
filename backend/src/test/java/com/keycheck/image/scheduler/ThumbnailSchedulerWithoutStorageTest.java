package com.keycheck.image.scheduler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.keycheck.TestcontainersConfiguration;
import com.keycheck.image.service.ThumbnailWorker;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

/** 축소본 작업을 켰지만 저장소 접속 정보가 없다(테스트 기본값). 앱은 뜨고 축소본은 만들지 않는다 (설계 3.2). */
@SpringBootTest(properties = "keycheck.thumbnail.enabled=true")
@Import(TestcontainersConfiguration.class)
class ThumbnailSchedulerWithoutStorageTest {
  @Autowired private ThumbnailScheduler scheduler;
  @Autowired private ObjectProvider<ThumbnailWorker> worker;

  @Test
  @DisplayName("켜져 있어도 저장소 설정이 없으면 축소본 작업 없이 뜨고 주기 실행은 아무것도 하지 않는다")
  void startsWithoutStorageAndDoesNothing() {
    assertThat(worker.getIfAvailable()).isNull();
    assertThatCode(scheduler::run).doesNotThrowAnyException();
  }
}

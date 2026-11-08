package com.keycheck.notice.scheduler;

import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import com.keycheck.TestcontainersConfiguration;
import com.keycheck.notice.service.NoticeArchiver;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest(
    properties = {
      "keycheck.notice.enabled=true",
      "keycheck.notice.interval=1h",
      "keycheck.notice.archive.interval=100ms",
      "keycheck.storage.endpoint=http://127.0.0.1:9",
      "keycheck.storage.bucket=keycheck-test",
      "keycheck.storage.access-key-id=test-access-key-id",
      "keycheck.storage.secret-access-key=test-secret-access-key"
    })
@Import(TestcontainersConfiguration.class)
class NoticeArchiveSchedulerTest {
  @MockitoBean private NoticeArchiver archiver;

  @Test
  @DisplayName("켜져 있고 저장소가 있으면 주기마다 첨부를 보관한다")
  void 켜져_있고_저장소가_있으면_주기마다_첨부를_보관한다() {
    verify(archiver, timeout(5000).atLeast(2)).archiveNext();
  }
}

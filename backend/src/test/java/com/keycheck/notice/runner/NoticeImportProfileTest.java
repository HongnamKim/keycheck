package com.keycheck.notice.runner;

import static org.assertj.core.api.Assertions.assertThat;

import com.keycheck.TestcontainersConfiguration;
import com.keycheck.crawl.scheduler.CrawlScheduler;
import com.keycheck.notice.scheduler.NoticeCollectScheduler;
import com.keycheck.notice.scheduler.NoticeSummaryScheduler;
import com.keycheck.summary.scheduler.SummaryScheduler;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * notice-import 프로필은 서버 env_file처럼 작업을 켜는 값(CRAWL_ENABLED=true 등)이 있어도 모든 작업을 끈다. 실행기가 뜨도록 저장소
 * 설정과 빈 묶음 폴더를 준다.
 */
@SpringBootTest(
    properties = {
      "spring.profiles.active=notice-import",
      "CRAWL_ENABLED=true",
      "PREFETCH_ENABLED=true",
      "THUMBNAIL_ENABLED=true",
      "NOTICE_ENABLED=true",
      "NOTICE_SUMMARY_ENABLED=true",
      "NOTICE_IMPORT_VENDOR=SWAGKEYS",
      "keycheck.storage.endpoint=http://127.0.0.1:9",
      "keycheck.storage.bucket=keycheck-test",
      "keycheck.storage.access-key-id=test-access-key-id",
      "keycheck.storage.secret-access-key=test-secret-access-key"
    })
@Import(TestcontainersConfiguration.class)
class NoticeImportProfileTest {
  private static final Path DIR = emptyBundle();

  @Autowired private ApplicationContext context;

  private static Path emptyBundle() {
    try {
      Path dir = Files.createTempDirectory("notice-import-test");
      Files.writeString(dir.resolve("messages.json"), "[]");
      return dir;
    } catch (IOException e) {
      throw new IllegalStateException(e);
    }
  }

  @DynamicPropertySource
  static void importDir(DynamicPropertyRegistry registry) {
    registry.add("NOTICE_IMPORT_DIR", () -> DIR.toString());
  }

  @Test
  @DisplayName("가져오기 프로필에서는 작업 켜는 값이 있어도 스케줄러가 없다")
  void 가져오기_프로필에서는_스케줄러가_없다() {
    assertThat(context.getBeansOfType(CrawlScheduler.class)).isEmpty();
    assertThat(context.getBeansOfType(SummaryScheduler.class)).isEmpty();
    assertThat(context.getBeansOfType(NoticeCollectScheduler.class)).isEmpty();
    assertThat(context.getBeansOfType(NoticeSummaryScheduler.class)).isEmpty();
    assertThat(context.getBeansOfType(NoticeImportRunner.class)).hasSize(1);
  }
}

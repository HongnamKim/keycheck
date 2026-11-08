package com.keycheck.notice.runner;

import com.keycheck.notice.config.NoticeImportProperties;
import com.keycheck.notice.service.ImportReport;
import com.keycheck.notice.service.NoticeImporter;
import com.keycheck.post.domain.KoreanVendor;
import java.nio.file.Path;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 지난 디스코드 공지를 한 번 가져오고 끝낸다. 로컬은 {@code NOTICE_IMPORT_DIR=... NOTICE_IMPORT_VENDOR=SWAGKEYS ./gradlew
 * bootRun --args='--spring.profiles.active=notice-import'}, 서버 컨테이너는 인자를 버리므로 환경변수
 * (SPRING_PROFILES_ACTIVE 포함)로 준다. 저장소(R2) 설정이 없으면 시작하지 않는다.
 */
@Slf4j
@Component
@Profile("notice-import")
@RequiredArgsConstructor
public class NoticeImportRunner implements ApplicationRunner {
  private final NoticeImportProperties properties;
  private final ObjectProvider<NoticeImporter> importer;

  @Override
  public void run(ApplicationArguments args) {
    if (!StringUtils.hasText(properties.getDir()) || !StringUtils.hasText(properties.getVendor())) {
      throw new IllegalStateException("NOTICE_IMPORT_DIR와 NOTICE_IMPORT_VENDOR를 설정해야 한다");
    }
    NoticeImporter noticeImporter = importer.getIfAvailable();
    if (noticeImporter == null) {
      throw new IllegalStateException("저장소(R2_*) 설정이 없어 가져올 수 없다");
    }
    KoreanVendor vendor = KoreanVendor.valueOf(properties.getVendor().trim().toUpperCase());
    ImportReport report = noticeImporter.importDir(Path.of(properties.getDir().trim()), vendor);
    log.info("공지 가져오기 결과: {}", report);
    if (report.isAborted()) {
      throw new IllegalStateException("저장소 장애로 가져오기를 끝내지 못했다. 다시 실행하면 이어서 끝낸다");
    }
  }
}

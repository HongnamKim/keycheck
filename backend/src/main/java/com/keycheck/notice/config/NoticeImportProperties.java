package com.keycheck.notice.config;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** 지난 공지 가져오기(notice-import 프로필) 설정. 서버 컨테이너는 인자를 받지 않으므로 환경변수로 준다. */
@Getter
@RequiredArgsConstructor
@ConfigurationProperties("keycheck.notice.import")
public class NoticeImportProperties {
  /** messages.json과 attachments/ 폴더가 든 폴더 */
  private final String dir;

  /** KoreanVendor 이름 (예: SWAGKEYS) */
  private final String vendor;
}

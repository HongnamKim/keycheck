package com.keycheck.image.config;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;

/** 축소본 저장소(Cloudflare R2, S3 호환) 접속 정보. 모두 비어 있을 수 있다 (설계 3.6). */
@Getter
@RequiredArgsConstructor
@ConfigurationProperties("keycheck.storage")
public class StorageProperties {
  /** S3 API 주소. R2는 https://<계정ID>.r2.cloudflarestorage.com */
  private final String endpoint;

  private final String bucket;
  private final String accessKeyId;
  private final String secretAccessKey;

  /** 브라우저가 축소본을 받는 공개 주소의 앞부분. 로컬은 버킷의 r2.dev 주소, 배포는 연결한 도메인 */
  private final String publicUrl;

  /** 저장소에 올릴 접속 정보(공개 주소를 뺀 넷)가 모두 있다 */
  public boolean isConfigured() {
    return StringUtils.hasText(endpoint)
        && StringUtils.hasText(bucket)
        && StringUtils.hasText(accessKeyId)
        && StringUtils.hasText(secretAccessKey);
  }
}

package com.keycheck.image.storage;

import lombok.RequiredArgsConstructor;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

/** S3 호환 API(Cloudflare R2)로 축소본을 올린다. */
@RequiredArgsConstructor
public class S3ThumbStorage implements ThumbStorage {
  private static final String CONTENT_TYPE = "image/webp";

  /** 객체 이름이 이미지 기준이라 한 번 올린 객체는 바뀌지 않는다. 길게 캐시한다. */
  private static final String CACHE_CONTROL = "public, max-age=31536000, immutable";

  private final S3Client client;
  private final String bucket;

  @Override
  public void put(String key, byte[] webp) {
    PutObjectRequest request =
        PutObjectRequest.builder()
            .bucket(bucket)
            .key(key)
            .contentType(CONTENT_TYPE)
            .cacheControl(CACHE_CONTROL)
            .build();
    try {
      client.putObject(request, RequestBody.fromBytes(webp));
    } catch (SdkException e) {
      // 접속·인증·서버 오류 모두 이미지 탓이 아니다. 메시지를 직접 만들지 않고 원인만 붙인다(키를 싣지 않는다).
      throw new StorageUnavailableException("축소본 저장소에 올리지 못했다", e);
    }
  }
}

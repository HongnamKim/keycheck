package com.keycheck.image.config;

import com.keycheck.image.storage.S3ThumbStorage;
import com.keycheck.image.storage.ThumbStorage;
import java.net.URI;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.type.AnnotatedTypeMetadata;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.checksums.RequestChecksumCalculation;
import software.amazon.awssdk.core.checksums.ResponseChecksumValidation;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

/** 접속 정보가 모두 있을 때만 축소본 저장소를 만든다. 없으면 축소본 작업이 돌지 않고 화면은 원본을 쓴다 (설계 3.6). */
@Configuration
@Conditional(StorageConfig.Configured.class)
public class StorageConfig {
  /** R2는 리전을 "auto"로 받는다. 버킷은 주소 경로로 고른다(버킷 이름을 호스트에 붙이지 않는다). */
  @Bean
  public S3Client thumbS3Client(StorageProperties properties) {
    return S3Client.builder()
        .endpointOverride(URI.create(properties.getEndpoint()))
        .region(Region.of("auto"))
        .forcePathStyle(true)
        .credentialsProvider(
            StaticCredentialsProvider.create(
                AwsBasicCredentials.create(
                    properties.getAccessKeyId(), properties.getSecretAccessKey())))
        // SDK 2.30부터는 기본으로 요청 체크섬(CRC32 등)을 계산해 aws-chunked 인코딩으로 보내는데,
        // R2가 이를 제대로 받지 못하는 경우가 있다. S3 API가 요구하는 요청에서만 계산·검증한다.
        .requestChecksumCalculation(RequestChecksumCalculation.WHEN_REQUIRED)
        .responseChecksumValidation(ResponseChecksumValidation.WHEN_REQUIRED)
        .build();
  }

  @Bean
  public ThumbStorage thumbStorage(S3Client thumbS3Client, StorageProperties properties) {
    return new S3ThumbStorage(thumbS3Client, properties.getBucket());
  }

  /**
   * StorageProperties#isConfigured. 키가 든 값을 조건식 문자열에 넣지 않도록(식이 잘못되면 오류 메시지에 실린다) 설정을 직접 묶어
   * 본다. 저장소가 있어야 하는 축소본 작업(ThumbnailWorker)도 이 조건을 쓴다.
   */
  public static class Configured implements Condition {
    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
      return Binder.get(context.getEnvironment())
          .bind("keycheck.storage", StorageProperties.class)
          .map(StorageProperties::isConfigured)
          .orElse(false);
    }
  }
}

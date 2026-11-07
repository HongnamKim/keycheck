package com.keycheck.image.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.keycheck.image.storage.ThumbStorage;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/** 저장소 빈은 접속 정보가 모두 있을 때만 만든다. 빈을 만들 뿐 저장소에 요청하지 않는다. */
class StorageConfigTest {
  private final ApplicationContextRunner runner =
      new ApplicationContextRunner()
          .withUserConfiguration(PropertiesConfig.class, StorageConfig.class);

  @EnableConfigurationProperties(StorageProperties.class)
  static class PropertiesConfig {}

  @Test
  @DisplayName("접속 정보 넷이 모두 있으면 저장소 빈을 만든다")
  void createsStorageWhenConfigured() {
    runner
        .withPropertyValues(
            "keycheck.storage.endpoint=http://127.0.0.1:9",
            "keycheck.storage.bucket=keycheck-test",
            "keycheck.storage.access-key-id=test-access-key-id",
            "keycheck.storage.secret-access-key=test-secret-access-key")
        .run(context -> assertThat(context).hasSingleBean(ThumbStorage.class));
  }

  @ParameterizedTest
  @ValueSource(strings = {"endpoint", "bucket", "access-key-id", "secret-access-key"})
  @DisplayName("접속 정보가 하나라도 비었으면 저장소 빈 없이 뜬다")
  void noStorageWhenAnyBlank(String blank) {
    Map<String, String> values =
        new LinkedHashMap<>(
            Map.of(
                "endpoint", "http://127.0.0.1:9",
                "bucket", "keycheck-test",
                "access-key-id", "test-access-key-id",
                "secret-access-key", "test-secret-access-key"));
    values.put(blank, "");

    runner
        .withPropertyValues(
            values.entrySet().stream()
                .map(entry -> "keycheck.storage." + entry.getKey() + "=" + entry.getValue())
                .toArray(String[]::new))
        .run(context -> assertThat(context).hasNotFailed().doesNotHaveBean(ThumbStorage.class));
  }

  @Test
  @DisplayName("접속 정보가 없으면 저장소 빈 없이 뜬다")
  void noStorageWhenNotConfigured() {
    runner.run(context -> assertThat(context).hasNotFailed().doesNotHaveBean(ThumbStorage.class));
  }
}

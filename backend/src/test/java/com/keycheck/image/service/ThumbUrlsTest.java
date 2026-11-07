package com.keycheck.image.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.keycheck.image.config.StorageProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ThumbUrlsTest {
  private ThumbUrls urls(String publicUrl) {
    return new ThumbUrls(new StorageProperties("", "", "", "", publicUrl));
  }

  @Test
  @DisplayName("공개 주소 끝의 /를 떼고 객체 이름을 붙인다")
  void joinsPublicUrlAndKey() {
    assertThat(urls("https://img.test/").url("thumbs/1.webp"))
        .isEqualTo("https://img.test/thumbs/1.webp");
    assertThat(urls("https://img.test").url("thumbs/1.webp"))
        .isEqualTo("https://img.test/thumbs/1.webp");
  }

  @Test
  @DisplayName("객체 이름이 없으면(축소본 없음) null")
  void nullKeyIsNull() {
    assertThat(urls("https://img.test/").url(null)).isNull();
  }

  @Test
  @DisplayName("공개 주소가 비었으면 null")
  void blankPublicUrlIsNull() {
    assertThat(urls("").url("thumbs/1.webp")).isNull();
    assertThat(urls(null).url("thumbs/1.webp")).isNull();
  }
}

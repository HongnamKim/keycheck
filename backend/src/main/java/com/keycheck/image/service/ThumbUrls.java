package com.keycheck.image.service;

import com.keycheck.image.config.StorageProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 축소본 객체 이름에 설정의 공개 주소 앞부분을 붙인다. DB에는 객체 이름만 두므로 저장소나 도메인이 바뀌어도 설정만 바꾼다 (설계 3.7).
 */
@Component
@RequiredArgsConstructor
public class ThumbUrls {
  private final StorageProperties properties;

  /** 축소본이 없거나(key가 null) 공개 주소 설정이 비었으면 null. 화면은 원본을 쓴다. */
  public String url(String key) {
    String publicUrl = properties.getPublicUrl();
    if (key == null || !StringUtils.hasText(publicUrl)) {
      return null;
    }
    return StringUtils.trimTrailingCharacter(publicUrl, '/') + "/" + key;
  }
}

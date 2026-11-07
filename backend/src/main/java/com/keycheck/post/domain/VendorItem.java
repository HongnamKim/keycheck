package com.keycheck.post.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Optional;
import lombok.EqualsAndHashCode;
import lombok.Getter;

/**
 * 판매처(벤더) 하나. 요약기가 첫 글 본문에서 뽑는다. post.vendors jsonb에 목록으로 저장된다.
 *
 * <p>region은 대문자 지역 코드(한국은 "KR"), 모르면 null. url은 첫 글 본문에 있던 그 벤더 판매 페이지 주소, 없으면 null.
 */
@Getter
@EqualsAndHashCode
public class VendorItem {
  private final String name;
  private final String region;
  private final String url;

  /** jsonb에서 다시 읽을 때 Jackson이 쓰는 생성자 */
  @JsonCreator
  public VendorItem(
      @JsonProperty("name") String name,
      @JsonProperty("region") String region,
      @JsonProperty("url") String url) {
    this.name = name;
    this.region = region;
    this.url = url;
  }

  /**
   * 한국 벤더인지. 지역이 KR이거나, 지역을 달리 적었어도(원문 표기 SK 등) 이름이 알려진 한국 벤더면 그렇다. 이름이 낯설면 지역 표기가 없을
   * 때만 판매 사이트 주소로 본다(요약기가 다른 지역 벤더에 한국 벤더 링크를 잘못 짝지은 경우를 한국 벤더로 보지 않게). jsonb에는 저장하지
   * 않는다.
   */
  @JsonIgnore
  public boolean isKorean() {
    return "KR".equals(region) || knownKorean().isPresent();
  }

  /** 알려진 한국 벤더면 그 벤더. 이름으로 먼저 찾고, 지역 표기가 없을 때만 판매 링크 주소로 찾는다 */
  public Optional<KoreanVendor> knownKorean() {
    return KoreanVendor.byName(name)
        .or(() -> region == null ? KoreanVendor.byUrl(url) : Optional.empty());
  }
}

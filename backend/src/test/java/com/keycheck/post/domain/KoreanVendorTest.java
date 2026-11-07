package com.keycheck.post.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.text.Normalizer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class KoreanVendorTest {
  @ParameterizedTest(name = "{0} → {1}")
  @CsvSource(
      delimiter = '|',
      value = {
        // 운영에서 실제로 나온 표기
        "GEON | GEONWORKS",
        "Geon | GEONWORKS",
        "GeonWork | GEONWORKS",
        "Geonwork | GEONWORKS",
        "GeonWorks | GEONWORKS",
        "Geonworks | GEONWORKS",
        "GEONWORKS | GEONWORKS",
        "Geon Works | GEONWORKS",
        "Geon works | GEONWORKS",
        "Geon.works | GEONWORKS",
        "' geon ' | GEONWORKS",
        "건웍스 | GEONWORKS",
        "ＧＥＯＮ | GEONWORKS",
        "Swagkeys（KR） | SWAGKEYS",
        "Swagkeys | SWAGKEYS",
        "SwagKeys | SWAGKEYS",
        "SWAGKeys | SWAGKEYS",
        "Swagkeys (KR) | SWAGKEYS",
        "Swagkey | SWAGKEYS",
        "Swag Keys | SWAGKEYS",
        "스웩키 | SWAGKEYS",
        "KLC | KLC_PLAYGROUND",
        "KLC Playground | KLC_PLAYGROUND",
        "Klc-playground | KLC_PLAYGROUND",
      })
  @DisplayName("대소문자·띄어쓰기·점·하이픈·괄호 속 표기가 달라도 알려진 한국 벤더 이름이면 그 벤더다")
  void byName(String name, KoreanVendor expected) {
    assertThat(KoreanVendor.byName(name)).contains(expected);
  }

  @ParameterizedTest
  @ValueSource(strings = {"iLumkb", "Funkeys", "NovelKeys", "Geonix", "Swag", "KLCX", "Geon Keys"})
  @NullAndEmptySource
  @DisplayName("이름이 통째로 같지 않으면(비슷하거나 일부만 같아도) 알려진 한국 벤더가 아니다")
  void byNameMiss(String name) {
    assertThat(KoreanVendor.byName(name)).isEmpty();
  }

  @ParameterizedTest(name = "{0} → {1}")
  @CsvSource(
      delimiter = '|',
      value = {
        "https://geon.works/ | GEONWORKS",
        "https://geon.works/products/group-buy-gmk-cyl-power-2048 | GEONWORKS",
        "https://www.geonworks.kr/a | GEONWORKS",
        "https://swagkeys.com/products/x | SWAGKEYS",
        "https://www.swagkey.kr | SWAGKEYS",
        "HTTPS://SHOP.SWAGKEYS.COM/x | SWAGKEYS",
        "https://klc-playground.com/p | KLC_PLAYGROUND",
      })
  @DisplayName("판매 링크가 알려진 한국 벤더 사이트(하위 도메인 포함)면 그 벤더다")
  void byUrl(String url, KoreanVendor expected) {
    assertThat(KoreanVendor.byUrl(url)).contains(expected);
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "https://geon.works.evil.example/x",
        "https://notgeon.works/",
        "https://kbdfans.com/collections/group-buy-live",
        "geon.works",
        "not a url",
      })
  @NullAndEmptySource
  @DisplayName("다른 사이트이거나 주소로 읽을 수 없으면 알려진 한국 벤더가 아니다")
  void byUrlMiss(String url) {
    assertThat(KoreanVendor.byUrl(url)).isEmpty();
  }

  @Test
  @DisplayName("자모가 나뉘어 저장된 한글 이름도 같은 벤더다")
  void byNameDecomposedHangul() {
    assertThat(KoreanVendor.byName(Normalizer.normalize("건웍스", Normalizer.Form.NFD)))
        .contains(KoreanVendor.GEONWORKS);
  }
}

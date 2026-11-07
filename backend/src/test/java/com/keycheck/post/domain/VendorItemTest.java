package com.keycheck.post.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class VendorItemTest {
  @Test
  @DisplayName("지역이 KR이거나, 지역을 달리 적었어도(원문 표기 SK 등) 이름이 알려진 한국 벤더면 한국 벤더다")
  void koreanByRegionOrName() {
    assertThat(new VendorItem("Swagkeys", "KR", null).isKorean()).isTrue();
    assertThat(new VendorItem("Unknown KR Shop", "KR", null).isKorean()).isTrue();
    assertThat(new VendorItem("Geon", "SK", "https://geon.works/").isKorean()).isTrue();
    assertThat(new VendorItem("iLumkb", "SG", "https://ilumkb.com/x").isKorean()).isFalse();
    assertThat(new VendorItem("NovelKeys", null, null).isKorean()).isFalse();
  }

  @Test
  @DisplayName("이름이 낯설면 지역 표기가 없을 때만 판매 사이트 주소로 한국 벤더를 알아본다")
  void koreanBySiteOnlyWithoutRegion() {
    assertThat(new VendorItem("건 웍스 스토어", null, "https://geon.works/products/x").isKorean())
        .isTrue();
    // 요약기가 다른 지역 벤더에 한국 벤더 링크를 잘못 짝지은 경우
    assertThat(new VendorItem("NovelKeys", "US", "https://geon.works/products/x").isKorean())
        .isFalse();
  }
}

package com.keycheck.post.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SubcategoryTest {
  @Test
  @DisplayName("키보드는 배열, 키캡은 제조사 세부 분류를 가진다")
  void subcategoriesPerCategory() {
    assertThat(Subcategory.of(Category.KEYBOARD))
        .containsExactly(
            Subcategory.KB_100,
            Subcategory.KB_96,
            Subcategory.KB_80,
            Subcategory.KB_75,
            Subcategory.KB_65,
            Subcategory.KB_60,
            Subcategory.KB_40,
            Subcategory.KB_ERGO,
            Subcategory.KB_ETC);
    assertThat(Subcategory.of(Category.KEYCAP))
        .containsExactly(
            Subcategory.KC_GMK,
            Subcategory.KC_SP,
            Subcategory.KC_MW,
            Subcategory.KC_KKB,
            Subcategory.KC_PBTFANS,
            Subcategory.KC_ETC);
    assertThat(Subcategory.of(Category.OTHER)).isEmpty();
    assertThat(Subcategory.of(Category.UNCLASSIFIED)).isEmpty();
  }

  @Test
  @DisplayName("주소용 값은 분류 안에서 겹치지 않는다")
  void slugsAreUniqueWithinCategory() {
    for (Category category : Category.values()) {
      assertThat(Subcategory.of(category).stream().map(Subcategory::getSlug))
          .doesNotHaveDuplicates();
    }
  }

  @Test
  @DisplayName("분류와 주소용 값으로 세부 분류를 찾는다. 분류와 맞지 않으면 없다")
  void findsBySlug() {
    assertThat(Subcategory.fromSlug(Category.KEYBOARD, "65")).contains(Subcategory.KB_65);
    assertThat(Subcategory.fromSlug(Category.KEYBOARD, "ergo")).contains(Subcategory.KB_ERGO);
    assertThat(Subcategory.fromSlug(Category.KEYBOARD, "etc")).contains(Subcategory.KB_ETC);
    assertThat(Subcategory.fromSlug(Category.KEYCAP, "pbtfans")).contains(Subcategory.KC_PBTFANS);
    assertThat(Subcategory.fromSlug(Category.KEYCAP, "etc")).contains(Subcategory.KC_ETC);
    assertThat(Subcategory.fromSlug(Category.KEYCAP, "65")).isEmpty();
    assertThat(Subcategory.fromSlug(Category.KEYBOARD, "gmk")).isEmpty();
    assertThat(Subcategory.fromSlug(Category.KEYBOARD, "")).isEmpty();
  }

  @Test
  @DisplayName("코드 값은 분류마다 정한 접두어로 시작한다")
  void codesHavePrefix() {
    assertThat(Arrays.stream(Subcategory.values()))
        .allSatisfy(
            sub ->
                assertThat(sub.name())
                    .startsWith(sub.getCategory() == Category.KEYBOARD ? "KB_" : "KC_"));
  }
}

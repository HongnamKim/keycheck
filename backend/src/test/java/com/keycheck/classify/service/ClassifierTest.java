package com.keycheck.classify.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.keycheck.post.domain.Category;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class ClassifierTest {
  private final Classifier classifier = new Classifier();

  // 샘플 8건의 짧은 이름과 기타 예시. 본문 없이 제목만으로 판정되는지 본다.
  @ParameterizedTest
  @CsvSource({
    "[IC] Della 960, KEYBOARD",
    "[IC] F8X, KEYBOARD",
    "[IC] RHINO60, KEYBOARD",
    "[IC] Constance 60, KEYBOARD",
    "[IC] GMK CYL Blanks, KEYCAP",
    "[IC] GMK Knight, KEYCAP",
    "[IC] DCS Nights, KEYCAP",
    "[IC] DCS 더블샷 한글, KEYCAP",
    "[IC] KAT Lich keycaps, KEYCAP",
    "[IC] ePBT Kingdom, KEYCAP",
    "[IC] Mountain Deskmat, OTHER",
    "[IC] Desk Mats collection, OTHER",
    "[IC] Linear Switches v2, OTHER",
    "[IC] Coiled Cables, OTHER",
    "[IC] Dragon Artisan, OTHER",
  })
  @DisplayName("제목으로 분류한다")
  void classifiesByTitle(String title, Category expected) {
    assertThat(classifier.classify(title, "")).isEqualTo(expected);
  }

  @Test
  @DisplayName("제목의 키캡 단어가 기타 단어보다 우선한다")
  void keycapTitleTermBeatsOtherTitleTerm() {
    assertThat(classifier.classify("[IC] GMK Ocean + matching deskmat", ""))
        .isEqualTo(Category.KEYCAP);
  }

  @Test
  @DisplayName("제목이 본문보다 우선한다")
  void titleBeatsBody() {
    String keyboardHeavyBody =
        "Render on a 65% keyboard. Plate: brass. PCB: hotswap. Gasket mount. Case weight 2kg.";

    assertThat(classifier.classify("[IC] GMK Knight", keyboardHeavyBody))
        .isEqualTo(Category.KEYCAP);
  }

  @Test
  @DisplayName("제목으로 못 정하면 본문에서 키캡 사양 단어가 많은 쪽을 고른다")
  void bodyWithMoreKeycapTerms() {
    String body =
        "Kits: Base kit, Alphas, Novelties, Spacebars. Two colorways. Shown on a TKL keyboard with a brass plate.";

    assertThat(classifier.classify("[IC] Sakura", body)).isEqualTo(Category.KEYCAP);
  }

  @Test
  @DisplayName("제목으로 못 정하면 본문에서 키보드 사양 단어가 많은 쪽을 고른다")
  void bodyWithMoreKeyboardTerms() {
    String body =
        "Layout 65%. Gasket mount, FR4 plate, hotswap PCB. Typing angle 7 degrees. Shown with GMK keycaps (base kit).";

    assertThat(classifier.classify("[IC] Aurora", body)).isEqualTo(Category.KEYBOARD);
  }

  @Test
  @DisplayName("본문 단어 수가 같거나 없으면 키보드로 본다")
  void tieOrNoTermsIsKeyboard() {
    assertThat(classifier.classify("[IC] Sakura", "plate and profile"))
        .isEqualTo(Category.KEYBOARD);
    assertThat(classifier.classify("[IC] Sakura", "Coming soon")).isEqualTo(Category.KEYBOARD);
  }

  @Test
  @DisplayName("같은 단어가 여러 번 나와도 한 종류로 센다")
  void repeatedTermCountsOnce() {
    String body = "Alphas alphas ALPHAS alphas. Plate, PCB.";

    assertThat(classifier.classify("[IC] Sakura", body)).isEqualTo(Category.KEYBOARD);
  }

  @Test
  @DisplayName("단어 경계로 비교한다")
  void matchesOnWordBoundaries() {
    // SALE의 SA, Kathmandu의 KAT, Switchblade의 Switch는 걸리지 않는다
    assertThat(classifier.classify("[IC] SALE Kathmandu Switchblade", ""))
        .isEqualTo(Category.KEYBOARD);
  }
}

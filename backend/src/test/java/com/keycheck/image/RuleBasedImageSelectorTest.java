package com.keycheck.image;

import static com.keycheck.image.RuleBasedImageSelector.REASON_BUTTON;
import static com.keycheck.image.RuleBasedImageSelector.REASON_DECORATION;
import static com.keycheck.image.RuleBasedImageSelector.REASON_SIGNATURE;
import static com.keycheck.image.RuleBasedImageSelector.REASON_SITE_ASSET;
import static org.assertj.core.api.Assertions.assertThat;

import com.keycheck.crawl.RawImage;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class RuleBasedImageSelectorTest {
  private final RuleBasedImageSelector selector = new RuleBasedImageSelector();

  private RawImage image(String url, int position) {
    return new RawImage(url, ImageSource.EXTERNAL, position, false);
  }

  @ParameterizedTest
  @CsvSource({
    "https://geekhack.org/Smileys/default/smiley.gif, site-asset",
    "https://geekhack.org/avatars/user.png, site-asset",
    "https://geekhack.org/Themes/default/images/on.png, site-asset",
    "https://geekhack.org/index.php?action=dlattach;attach=1;type=avatar, site-asset",
    "https://storage.ko-fi.com/cdn/kofi2.png, button",
    "https://img.shields.io/discord/123.svg, button",
    "https://discord.com/api/guilds/123/widget.png?style=banner2, button",
    "https://i.imgur.com/ProjectLogo.png, decoration",
    "https://example.com/img/BANNER_v2.jpg?w=1200, decoration",
    "https://example.com/divider.png, decoration",
  })
  @DisplayName("규칙에 맞는 이미지는 사유와 함께 제외한다")
  void excludesWithReason(String url, String reason) {
    Selection selection = selector.select(List.of(image(url, 0)));

    assertThat(selection.excludedReasons()).containsExactly(reason);
    assertThat(selection.representativeIndex()).isNull();
  }

  @Test
  @DisplayName("서명 영역 이미지는 제외한다")
  void excludesSignatureImage() {
    RawImage signature = new RawImage("https://i.imgur.com/render.jpg", ImageSource.EXTERNAL, 0, true);

    assertThat(selector.select(List.of(signature)).excludedReasons())
        .containsExactly(REASON_SIGNATURE);
  }

  @Test
  @DisplayName("첫 이미지가 배너면 두 번째 이미지를 대표로 고른다")
  void picksSecondWhenFirstIsBanner() {
    Selection selection =
        selector.select(
            List.of(
                image("https://i.imgur.com/banner.png", 0),
                image("https://i.imgur.com/render1.jpg", 1),
                image("https://i.imgur.com/render2.jpg", 2)));

    assertThat(selection.representativeIndex()).isEqualTo(1);
    assertThat(selection.excludedReasons()).containsExactly(REASON_DECORATION, null, null);
  }

  @Test
  @DisplayName("첨부 이미지와 쿼리 문자열만 있는 주소는 후보로 둔다")
  void attachmentUrlIsCandidate() {
    RawImage attachment =
        new RawImage(
            "https://geekhack.org/index.php?action=dlattach;topic=127200.0;attach=318371;image",
            ImageSource.ATTACHMENT,
            0,
            false);

    assertThat(selector.select(List.of(attachment)).representativeIndex()).isEqualTo(0);
  }

  @Test
  @DisplayName("후보가 없으면 대표 이미지도 없다")
  void noCandidateMeansNoRepresentative() {
    Selection selection =
        selector.select(
            List.of(
                image("https://storage.ko-fi.com/cdn/kofi2.png", 0),
                image("https://x.com/logo.svg", 1)));

    assertThat(selection.representativeIndex()).isNull();
    assertThat(selection.excludedReasons()).containsExactly(REASON_BUTTON, REASON_DECORATION);
  }

  @Test
  @DisplayName("이미지가 없으면 빈 결과를 돌려준다")
  void emptyImages() {
    Selection selection = selector.select(List.of());

    assertThat(selection.representativeIndex()).isNull();
    assertThat(selection.excludedReasons()).isEmpty();
  }

  @Test
  @DisplayName("사이트 이미지 규칙이 버튼·파일명 규칙보다 먼저 적용된다")
  void siteAssetRuleComesFirst() {
    Selection selection =
        selector.select(List.of(image("https://geekhack.org/Themes/default/images/logo.png", 0)));

    assertThat(selection.excludedReasons()).containsExactly(REASON_SITE_ASSET);
  }
}

package com.keycheck.image.service;

import com.keycheck.crawl.dto.RawImage;
import com.keycheck.image.dto.Selection;
import java.util.List;
import org.springframework.stereotype.Component;

/** 설계 4.5: 규칙으로 부적합한 이미지를 거르고, 남은 후보 중 첫 번째를 대표로 고른다. */
@Component
public class RuleBasedImageSelector implements RepresentativeImageSelector {
  public static final String REASON_SITE_ASSET = "site-asset";
  public static final String REASON_BUTTON = "button";
  public static final String REASON_DECORATION = "decoration";

  /** geekhack 이모티콘·아바타·테마 이미지 */
  private static final List<String> SITE_ASSET_PATTERNS =
      List.of("/smileys/", "/avatars/", "/themes/", ";type=avatar");

  /** 후원·디스코드·배지 버튼 */
  private static final List<String> BUTTON_PATTERNS =
      List.of(
          "ko-fi.com", "paypal", "patreon", "buymeacoffee", "shields.io", "discord.com/api/guilds");

  /** 파일명에 들어 있으면 장식용으로 보는 단어 */
  private static final List<String> DECORATION_FILE_KEYWORDS = List.of("logo", "banner", "divider");

  @Override
  public Selection select(List<RawImage> images) {
    List<String> reasons = images.stream().map(this::exclusionReason).toList();
    int firstCandidate = reasons.indexOf(null);
    return new Selection(firstCandidate >= 0 ? firstCandidate : null, reasons);
  }

  private String exclusionReason(RawImage image) {
    String url = image.getUrl().toLowerCase();
    if (SITE_ASSET_PATTERNS.stream().anyMatch(url::contains)) return REASON_SITE_ASSET;
    if (BUTTON_PATTERNS.stream().anyMatch(url::contains)) return REASON_BUTTON;
    if (DECORATION_FILE_KEYWORDS.stream().anyMatch(fileName(url)::contains)) {
      return REASON_DECORATION;
    }
    return null;
  }

  private String fileName(String url) {
    int query = url.indexOf('?');
    String path = query >= 0 ? url.substring(0, query) : url;
    return path.substring(path.lastIndexOf('/') + 1);
  }
}

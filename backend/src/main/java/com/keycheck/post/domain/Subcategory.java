package com.keycheck.post.domain;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 세부 분류. 키보드는 배열, 키캡은 제조사. API 응답은 코드 값(KB_65 등), 목록 필터 주소는 slug(65 등)를 쓴다. 기타·미분류 글에는
 * 없다(null).
 */
@Getter
@RequiredArgsConstructor
public enum Subcategory {
  /** 숫자패드와 기능열이 모두 있는 풀배열. 풀배열보다 큰 배열(배틀십 등) 포함 */
  KB_100(Category.KEYBOARD, "100"),
  /** 숫자패드를 붙인 압축 풀배열 */
  KB_96(Category.KEYBOARD, "96"),
  /** TKL (F13 TKL 포함) */
  KB_80(Category.KEYBOARD, "80"),
  KB_75(Category.KEYBOARD, "75"),
  KB_65(Category.KEYBOARD, "65"),
  /** HHKB 배열 포함. WK·WKL 같은 하단열 모양은 크기와 상관없다 */
  KB_60(Category.KEYBOARD, "60"),
  /** 50% 이하 (40%, 50% 등) */
  KB_40(Category.KEYBOARD, "40"),
  /** 앨리스·스플릿·기타 인체공학 배열 */
  KB_ERGO(Category.KEYBOARD, "ergo"),
  /** 매크로패드, 넘패드, 판단 불가 */
  KB_ETC(Category.KEYBOARD, "etc"),
  KC_GMK(Category.KEYCAP, "gmk"),
  /** Signature Plastics */
  KC_SP(Category.KEYCAP, "sp"),
  /** MWKEYS */
  KC_MW(Category.KEYCAP, "mw"),
  /** Key Kobo */
  KC_KKB(Category.KEYCAP, "kkb"),
  KC_PBTFANS(Category.KEYCAP, "pbtfans"),
  /** 그 밖의 제조사, 판단 불가 */
  KC_ETC(Category.KEYCAP, "etc");

  /** 이 세부 분류가 속한 분류 */
  private final Category category;

  /** 목록 필터 주소에 쓰는 짧은 값. 분류 안에서만 겹치지 않는다. */
  private final String slug;

  public static List<Subcategory> of(Category category) {
    return Arrays.stream(values()).filter(sub -> sub.category == category).toList();
  }

  public static Optional<Subcategory> fromSlug(Category category, String slug) {
    return of(category).stream().filter(sub -> sub.slug.equalsIgnoreCase(slug)).findFirst();
  }
}

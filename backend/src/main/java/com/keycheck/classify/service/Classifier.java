package com.keycheck.classify.service;

import com.keycheck.post.domain.Category;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * 설계 4.4: 제목으로 키캡·기타를 먼저 가려내고, 못 정하면 본문의 사양 단어로 판단한다. 그래도 못 정하면 키보드로 본다 (IC 게시판 글은 대부분 키보드다).
 */
@Component
public class Classifier {
  private static final List<Pattern> KEYCAP_TITLE_TERMS =
      terms(
          "GMK", "KAT", "MT3", "SA", "DSA", "DCS", "XDA", "e?PBT", "PBTfans", "keycaps?", "keysets?");

  private static final List<Pattern> OTHER_TITLE_TERMS =
      terms(
          "deskmats?", "desk ?mats?", "switch(?:es)?", "artisans?", "cables?", "stabili[sz]ers?");

  /** 키보드 사양 항목 단어 (종류 수를 센다) */
  private static final List<Pattern> KEYBOARD_BODY_TERMS =
      terms(
          "plates?",
          "PCBs?",
          "gaskets?",
          "mount(?:ed|ing)?",
          "typing angle",
          "weight",
          "hot-?swap(?:pable)?",
          "case");

  /** 키캡 사양 항목 단어 (종류 수를 센다) */
  private static final List<Pattern> KEYCAP_BODY_TERMS =
      terms(
          "base kits?",
          "alphas?",
          "modifiers?",
          "novelt(?:y|ies)",
          "spacebars?",
          "colou?rways?",
          "profile");

  public Category classify(String title, String bodyText) {
    if (countMatchingTerms(KEYCAP_TITLE_TERMS, title) > 0) return Category.KEYCAP;
    if (countMatchingTerms(OTHER_TITLE_TERMS, title) > 0) return Category.OTHER;

    long keyboardScore = countMatchingTerms(KEYBOARD_BODY_TERMS, bodyText);
    long keycapScore = countMatchingTerms(KEYCAP_BODY_TERMS, bodyText);
    return keycapScore > keyboardScore ? Category.KEYCAP : Category.KEYBOARD;
  }

  private static long countMatchingTerms(List<Pattern> terms, String text) {
    return terms.stream().filter(term -> term.matcher(text).find()).count();
  }

  /** 단어 경계·대소문자 무시 정규식. "SA"가 "SALE"에 걸리지 않게 한다. */
  private static List<Pattern> terms(String... patterns) {
    return Arrays.stream(patterns)
        .map(pattern -> Pattern.compile("\\b(?:" + pattern + ")\\b", Pattern.CASE_INSENSITIVE))
        .toList();
  }
}

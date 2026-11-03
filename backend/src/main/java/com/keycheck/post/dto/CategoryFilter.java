package com.keycheck.post.dto;

import com.keycheck.post.domain.Category;
import com.keycheck.post.domain.Subcategory;
import java.util.Arrays;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/** 목록 필터. API 값은 소문자(all, keyboard, keycap) */
@Getter
@RequiredArgsConstructor
public enum CategoryFilter {
  ALL(null),
  KEYBOARD(Category.KEYBOARD),
  KEYCAP(Category.KEYCAP);

  private final Category category;

  public static CategoryFilter from(String value) {
    return Arrays.stream(values())
        .filter(filter -> filter.name().equalsIgnoreCase(value))
        .findFirst()
        .orElseThrow(
            () -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "허용되지 않는 값: " + value));
  }

  /**
   * 이 분류의 세부 분류 필터. API 값은 Subcategory의 slug(keyboard: 65 등, keycap: gmk 등).
   *
   * @param slug 생략했으면 null
   * @return slug가 null이면 null (세부 분류로 거르지 않는다)
   * @throws ResponseStatusException 400: 전체(all)이거나 이 분류의 세부 분류가 아니면
   */
  public Subcategory subcategory(String slug) {
    if (slug == null) {
      return null;
    }
    if (category == null) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "세부 분류는 분류(keyboard, keycap)를 정해야 쓸 수 있다: " + slug);
    }
    return Subcategory.fromSlug(category, slug)
        .orElseThrow(
            () ->
                new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "허용되지 않는 세부 분류: " + slug));
  }
}

package com.keycheck.post.dto;

import com.keycheck.post.domain.Category;
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
}

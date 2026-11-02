package com.keycheck.post;

import java.util.Arrays;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/** 목록 정렬. API 값은 소문자(recent, views) */
@Getter
@RequiredArgsConstructor
public enum PostSort {
  RECENT(Sort.by(Sort.Order.desc("postedAt").nullsLast(), Sort.Order.desc("id"))),
  VIEWS(Sort.by(Sort.Order.desc("views"), Sort.Order.desc("id")));

  private final Sort sort;

  public static PostSort from(String value) {
    return Arrays.stream(values())
        .filter(sort -> sort.name().equalsIgnoreCase(value))
        .findFirst()
        .orElseThrow(
            () -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "허용되지 않는 값: " + value));
  }
}

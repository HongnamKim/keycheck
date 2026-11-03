package com.keycheck.post.dto;

import java.util.Arrays;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/** 목록 정렬. API 값은 소문자(recent, views) */
public enum PostSort {
  /** 글 번호 내림차순(작성순). 확보한 범위(커버리지) 안의 글만 */
  RECENT,
  /** 조회수 내림차순, 같으면 글 번호 내림차순. 저장된 글 전체 */
  VIEWS;

  public static PostSort from(String value) {
    return Arrays.stream(values())
        .filter(sort -> sort.name().equalsIgnoreCase(value))
        .findFirst()
        .orElseThrow(
            () -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "허용되지 않는 값: " + value));
  }
}

package com.keycheck.post.dto;

import com.keycheck.post.domain.Board;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/** 목록의 게시판 필터. API 값은 소문자(all, ic, gb) */
public final class BoardFilter {
  private static final String ALL = "all";

  private BoardFilter() {}

  /**
   * @return all이면 null (두 게시판 모두)
   * @throws ResponseStatusException 400: all, ic, gb가 아니면
   */
  public static Board from(String value) {
    if (ALL.equalsIgnoreCase(value)) {
      return null;
    }
    return Board.fromSlug(value)
        .orElseThrow(
            () -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "허용되지 않는 값: " + value));
  }
}

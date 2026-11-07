package com.keycheck.post.domain;

import java.util.Arrays;
import java.util.Optional;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 글을 읽어 오는 geekhack 게시판. API 응답은 코드 값(IC, GB), 목록 필터 주소는 slug(ic, gb)를 쓴다. geekhack 글 번호는 게시판
 * 공통이라 한 글은 한 게시판에만 있다(운영자가 옮기면 바뀐다).
 */
@Getter
@RequiredArgsConstructor
public enum Board {
  /** Interest Checks */
  IC(132, "ic"),
  /** Group Buys and Preorders */
  GB(70, "gb");

  /** geekhack 게시판 번호 (index.php?board=번호) */
  private final int geekhackBoardId;

  /** 목록 필터 주소에 쓰는 짧은 값 */
  private final String slug;

  public static Optional<Board> fromSlug(String slug) {
    return Arrays.stream(values()).filter(board -> board.slug.equalsIgnoreCase(slug)).findFirst();
  }
}

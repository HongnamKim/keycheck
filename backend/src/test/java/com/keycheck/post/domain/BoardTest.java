package com.keycheck.post.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class BoardTest {
  @Test
  @DisplayName("IC는 geekhack 132번(Interest Checks), GB는 70번(Group Buys and Preorders) 게시판이다")
  void geekhackBoardIds() {
    assertThat(Board.IC.getGeekhackBoardId()).isEqualTo(132);
    assertThat(Board.GB.getGeekhackBoardId()).isEqualTo(70);
  }

  @Test
  @DisplayName("주소에 쓰는 값은 소문자(ic, gb)다")
  void slugs() {
    assertThat(Board.IC.getSlug()).isEqualTo("ic");
    assertThat(Board.GB.getSlug()).isEqualTo("gb");
  }

  @Test
  @DisplayName("주소 값으로 게시판을 찾는다. 대소문자는 가리지 않고, 없는 값이면 비어 있다")
  void fromSlug() {
    assertThat(Board.fromSlug("gb")).contains(Board.GB);
    assertThat(Board.fromSlug("IC")).contains(Board.IC);
    assertThat(Board.fromSlug("all")).isEmpty();
    assertThat(Board.fromSlug(null)).isEmpty();
  }
}

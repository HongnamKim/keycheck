package com.keycheck.crawl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class BoardListParserTest {
  private final BoardListParser parser = new BoardListParser();
  private String fixture;

  @BeforeEach
  void loadFixture() throws IOException {
    try (InputStream in = getClass().getResourceAsStream("/fixtures/board-list.html")) {
      fixture = new String(in.readAllBytes(), StandardCharsets.UTF_8);
    }
  }

  @Test
  @DisplayName("고정글과 안내 행을 빼고 일반 글의 필드를 읽는다")
  void readsNormalTopics() {
    assertThat(parser.parse(fixture))
        .extracting(
            BoardTopic::getTopicId,
            BoardTopic::getTitle,
            BoardTopic::getAuthor,
            BoardTopic::getReplies,
            BoardTopic::getViews)
        .containsExactly(
            tuple(2000L, "[IC] Foo & Bar — A \"Test\" Board", "alice", 12, 4347),
            tuple(3000L, "[IC] Multi Page Topic", "bob", 78, 82388));
  }

  @Test
  @DisplayName("형식이 다른 행은 건너뛰고 나머지를 읽는다")
  void skipsMalformedRow() {
    String broken = fixture.replace("12 Replies", "알 수 없음");

    assertThat(parser.parse(broken)).extracting(BoardTopic::getTopicId).containsExactly(3000L);
  }

  @Test
  @DisplayName("글 목록 표가 없는 페이지는 예외를 던진다")
  void throwsWhenTableMissing() {
    // 차단·오류 페이지를 "글 0건"으로 착각하지 않게 한다
    assertThatThrownBy(() -> parser.parse("<html><body>Too many requests</body></html>"))
        .isInstanceOf(IllegalStateException.class);
  }
}

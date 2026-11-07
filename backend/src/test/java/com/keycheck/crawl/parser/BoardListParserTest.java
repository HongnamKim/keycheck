package com.keycheck.crawl.parser;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import com.keycheck.MutableClock;
import com.keycheck.crawl.dto.BoardTopic;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class BoardListParserTest {
  // MutableClock은 2026-11-01T00:00:00Z. 시카고에서는 10월 31일 19:00(CDT, UTC-5)이다.
  private final BoardListParser parser =
      new BoardListParser(
          new GeekhackTimeParser(ZoneId.of("America/Chicago"), new MutableClock()));
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
  @DisplayName("마지막 게시물 시각을 geekhack 시간대로 읽는다")
  void readsLastPostAt() {
    // 2000: 11월 1일 22:21:35 CST(UTC-6, 서머타임 끝난 날), 3000: 10월 31일 20:00:00 CDT(UTC-5)
    assertThat(parser.parse(fixture))
        .extracting(BoardTopic::getLastPostAt)
        .containsExactly(
            Instant.parse("2026-11-02T04:21:35Z"), Instant.parse("2026-11-01T01:00:00Z"));
  }

  @Test
  @DisplayName("Today·Yesterday 표기는 geekhack 시간대의 오늘·어제 날짜로 읽는다")
  void readsRelativeLastPostAt() {
    String relative =
        fixture
            .replace("Sun, 01 November 2026, 22:21:35", "<strong>Today</strong> at 10:40:10")
            .replace("Sat, 31 October 2026, 20:00:00", "<strong>Yesterday</strong> at 10:40:10");

    assertThat(parser.parse(relative))
        .extracting(BoardTopic::getLastPostAt)
        .containsExactly(
            Instant.parse("2026-10-31T15:40:10Z"), Instant.parse("2026-10-30T15:40:10Z"));
  }

  @Test
  @DisplayName("마지막 게시물 시각을 읽지 못하면 null로 두고 행은 읽는다")
  void unreadableLastPostAtIsNull() {
    String broken =
        fixture
            .replace("Sun, 01 November 2026, 22:21:35", "알 수 없음")
            .replace("Sat, 31 October 2026, 20:00:00", "");
    String missing = fixture.replace("class=\"lastpost", "class=\"unknown");

    assertThat(parser.parse(broken))
        .extracting(BoardTopic::getTopicId, BoardTopic::getLastPostAt)
        .containsExactly(tuple(2000L, null), tuple(3000L, null));
    assertThat(parser.parse(missing))
        .extracting(BoardTopic::getTopicId, BoardTopic::getLastPostAt)
        .containsExactly(tuple(2000L, null), tuple(3000L, null));
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

  @Test
  @DisplayName("쪽 번호 목록에서 지금 쪽을 0부터 센 번호로 읽는다")
  void readsCurrentPage() {
    assertThat(parser.parseCurrentPage(fixture)).isZero();
    assertThat(parser.parseCurrentPage(fixture.replace("[<strong>1</strong>]", "[<strong>12</strong>]")))
        .isEqualTo(11);
  }

  @Test
  @DisplayName("쪽 번호 목록이 없으면 예외를 던진다")
  void throwsWhenPageIndexMissing() {
    assertThatThrownBy(() -> parser.parseCurrentPage("<html><body>Too many requests</body></html>"))
        .isInstanceOf(IllegalStateException.class);
  }
}

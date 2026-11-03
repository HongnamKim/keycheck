package com.keycheck.crawl.parser;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import com.keycheck.MutableClock;
import com.keycheck.crawl.dto.RawImage;
import com.keycheck.crawl.dto.TopicPage;
import com.keycheck.crawl.dto.TopicStats;
import com.keycheck.image.domain.ImageSource;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class TopicParserTest {
  private static final String FULL_DATE = "Tue, 13 October 2026, 08:29:35";

  // MutableClock은 2026-11-01T00:00:00Z. 시카고에서는 10월 31일 19:00(CDT, UTC-5)이다.
  private final TopicParser parser =
      new TopicParser(new GeekhackTimeParser(ZoneId.of("America/Chicago"), new MutableClock()));
  private String external;
  private String attachment;

  @BeforeEach
  void loadFixtures() throws IOException {
    external = read("/fixtures/topic-external.html");
    attachment = read("/fixtures/topic-attachment.html");
  }

  private String read(String path) throws IOException {
    try (InputStream in = getClass().getResourceAsStream(path)) {
      return new String(in.readAllBytes(), StandardCharsets.UTF_8);
    }
  }

  @Test
  @DisplayName("첫 글의 본문 텍스트와 조회수를 읽는다")
  void readsBodyAndViews() {
    TopicPage page = parser.parseFirstPage(external);

    assertThat(page.getBodyText()).isEqualTo("Test Board A small board — made up.");
    assertThat(page.getViews()).isEqualTo(37883);
  }

  @Test
  @DisplayName("본문 이미지만 순서대로 읽고 아바타와 서명 이미지는 읽지 않는다")
  void readsOnlyBodyImages() {
    assertThat(parser.parseFirstPage(external).getImages())
        .extracting(RawImage::getUrl, RawImage::getSource, RawImage::getPosition)
        .containsExactly(
            tuple("https://cdn.geekhack.test/Smileys/solosmileys/blank.gif", ImageSource.EXTERNAL, 0),
            tuple("https://i.imgur.com/first.jpg", ImageSource.EXTERNAL, 1),
            tuple("https://i.imgur.com/second.png", ImageSource.EXTERNAL, 2));
  }

  @Test
  @DisplayName("첨부 이미지는 세션 값을 지우고 첨부 영역은 원본 주소를 읽으며 댓글 이미지는 읽지 않는다")
  void readsAttachments() {
    assertThat(parser.parseFirstPage(attachment).getImages())
        .extracting(RawImage::getUrl, RawImage::getSource, RawImage::getPosition)
        .containsExactly(
            tuple(
                "https://geekhack.test/index.php?action=dlattach;topic=2000.0;attach=1;image",
                ImageSource.ATTACHMENT,
                0),
            tuple(
                "https://geekhack.test/index.php?action=dlattach;topic=2000.0;attach=2;image",
                ImageSource.ATTACHMENT,
                1));
  }

  @Test
  @DisplayName("작성일을 geekhack 시간대로 해석한다")
  void parsesFullDate() {
    // 10월 13일은 서머타임(UTC-5)
    assertThat(parser.parseFirstPage(external).getPostedAt())
        .isEqualTo(Instant.parse("2026-10-13T13:29:35Z"));
  }

  @Test
  @DisplayName("Today 표기는 geekhack 시간대의 오늘 날짜로 해석한다")
  void parsesToday() {
    String html = external.replace(FULL_DATE, "<strong>Today</strong> at 10:40:10");

    assertThat(parser.parseFirstPage(html).getPostedAt())
        .isEqualTo(Instant.parse("2026-10-31T15:40:10Z"));
  }

  @Test
  @DisplayName("Yesterday 표기는 geekhack 시간대의 어제 날짜로 해석한다")
  void parsesYesterday() {
    String html = external.replace(FULL_DATE, "<strong>Yesterday</strong> at 10:40:10");

    assertThat(parser.parseFirstPage(html).getPostedAt())
        .isEqualTo(Instant.parse("2026-10-30T15:40:10Z"));
  }

  @Test
  @DisplayName("마지막 페이지의 마지막 댓글 번호가 댓글 수다, 본문 속 Reply 글자는 무시한다")
  void readsRepliesFromLastReplyNumber() {
    TopicStats stats = parser.parseStats(attachment);

    assertThat(stats.getReplies()).isEqualTo(2);
    assertThat(stats.getViews()).isEqualTo(512);
  }

  @Test
  @DisplayName("댓글이 없으면 댓글 수는 0이다")
  void noRepliesIsZero() {
    TopicStats stats = parser.parseStats(external);

    assertThat(stats.getReplies()).isEqualTo(0);
    assertThat(stats.getViews()).isEqualTo(37883);
  }

  @Test
  @DisplayName("글 영역이 없는 페이지는 예외를 던진다")
  void throwsWhenNoPosts() {
    // 삭제된 글이나 차단·오류 페이지
    String html = "<html><body>The topic is missing or off limits</body></html>";

    assertThatThrownBy(() -> parser.parseFirstPage(html)).isInstanceOf(IllegalStateException.class);
    assertThatThrownBy(() -> parser.parseStats(html)).isInstanceOf(IllegalStateException.class);
  }
}

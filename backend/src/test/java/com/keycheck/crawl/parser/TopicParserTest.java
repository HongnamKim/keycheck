package com.keycheck.crawl.parser;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import com.keycheck.MutableClock;
import com.keycheck.crawl.dto.RawImage;
import com.keycheck.crawl.dto.TopicPage;
import com.keycheck.crawl.dto.TopicStats;
import com.keycheck.image.domain.ImageSource;
import com.keycheck.post.domain.BodyLink;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
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
  @DisplayName("첫 글 본문의 http·https 링크를 순서대로 한 번씩 모으고 첨부·geekhack 글·호스트 없는 주소·댓글 링크는 뺀다")
  void collectsBodyLinks() {
    String html =
        topic(
            """
            <a href="https://swagkeys.com/products/x">Swagkeys (KR)</a>
            <a href="https://novelkeys.com/x">NovelKeys</a>
            <a href="https://swagkeys.com/products/x">Swagkeys again</a>
            <a href="mailto:a@b.c">메일</a>
            <a href="javascript:void(0)">x</a>
            <a href="https://geekhack.org/index.php?topic=1.0">다른 글</a>
            <a href="https://geekhack.org/index.php?action=dlattach;topic=1.0;attach=2">첨부</a>
            <a href="https://geekhack.test/index.php?action=dlattach;topic=1.0;attach=3">첨부</a>
            <a href="https:foo">호스트 없음</a>
            <a href="http:///x">빈 호스트</a>
            <a href="https://x_y.geekhack.org/index.php?topic=1.0">밑줄 든 geekhack</a>
            """,
            "<a href=\"https://comment.example\">댓글 링크</a>");

    assertThat(parser.parseFirstPage(html).getLinks())
        .containsExactly(
            new BodyLink("Swagkeys (KR)", "https://swagkeys.com/products/x"),
            new BodyLink("NovelKeys", "https://novelkeys.com/x"));
  }

  @Test
  @DisplayName("링크는 200개까지, 주소는 2,000자까지, 글자는 200자까지 모으고 글자가 없으면 주소를 글자로 쓴다")
  void capsBodyLinks() {
    String atLimit = url(2000);
    String overLimit = url(2001);
    StringBuilder body =
        new StringBuilder()
            .append(link(overLimit, "넘는 주소"))
            .append(link(atLimit, "딱 맞는 주소"))
            .append(link("https://banner.example/", "<img src=\"https://i.imgur.com/b.jpg\">"))
            .append(link("https://long-text.example/", "가".repeat(199) + "😀".repeat(100)));
    for (int i = 0; i < 250; i++) {
      body.append(link("https://shop.example/" + i, "상점 " + i));
    }

    List<BodyLink> links = parser.parseFirstPage(topic(body.toString(), "")).getLinks();

    assertThat(links).hasSize(200);
    assertThat(links).extracting(BodyLink::getUrl).doesNotContain(overLimit);
    assertThat(links.get(0)).isEqualTo(new BodyLink("딱 맞는 주소", atLimit));
    assertThat(links.get(1))
        .isEqualTo(new BodyLink("https://banner.example/", "https://banner.example/"));
    // 서로게이트 쌍인 이모지를 반으로 가르지 않는다
    assertThat(links.get(2).getText()).isEqualTo("가".repeat(199) + "😀");
    assertThat(links.get(3)).isEqualTo(new BodyLink("상점 0", "https://shop.example/0"));
    // 앞의 세 개 다음에 상점 링크 197개
    assertThat(links.get(199).getUrl()).isEqualTo("https://shop.example/196");
  }

  @Test
  @DisplayName("같은 주소가 글자 없는 링크(이미지 링크)로 먼저 나오고 뒤에 글자 링크로 다시 나오면 처음 자리에 뒤의 글자를 쓴다")
  void prefersLaterTextForImageLink() {
    String logo = "<img src=\"https://i.imgur.com/logo.png\">";
    String html =
        topic(
            link("https://swagkeys.com/p", logo)
                + link("https://novelkeys.com/x", "NovelKeys")
                + link("https://swagkeys.com/p", "Swagkeys (KR)")
                + link("https://novelkeys.com/x", logo)
                + link("https://swagkeys.com/p", "Swagkeys again"),
            "");

    assertThat(parser.parseFirstPage(html).getLinks())
        .containsExactly(
            new BodyLink("Swagkeys (KR)", "https://swagkeys.com/p"),
            new BodyLink("NovelKeys", "https://novelkeys.com/x"));
  }

  /** 길이가 length인 주소 */
  private static String url(int length) {
    String prefix = "https://long.example/";
    return prefix + "a".repeat(length - prefix.length());
  }

  private static String link(String href, String content) {
    return "<a href=\"" + href + "\">" + content + "</a>";
  }

  /** 첫 글과 댓글 하나가 있는 글 페이지. 구조만 흉내 낸 가짜 HTML이다. */
  private static String topic(String firstBody, String replyBody) {
    return """
        <div id="forumposts">
          <h3 class="catbg">Topic: [GB] Links (Read 10 times)</h3>
          <div class="post_wrapper">
            <div class="keyinfo">
              <div class="smalltext">&#171; <strong> on:</strong> %s &#187;</div>
            </div>
            <div class="inner">%s</div>
          </div>
          <div class="post_wrapper">
            <div class="keyinfo">
              <div class="smalltext">&#171; <strong>Reply #1 on:</strong> %s &#187;</div>
            </div>
            <div class="inner">%s</div>
          </div>
        </div>
        """
        .formatted(FULL_DATE, firstBody, FULL_DATE, replyBody);
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

package com.keycheck.notice.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.keycheck.TestcontainersConfiguration;
import com.keycheck.notice.domain.Notice;
import com.keycheck.notice.domain.NoticeOrigin;
import com.keycheck.notice.dto.DiscordMessage;
import com.keycheck.notice.repository.NoticeRepository;
import com.keycheck.post.domain.KoreanVendor;
import com.keycheck.post.domain.SummaryStatus;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class NoticeIngestServiceTest {
  private static final long CHANNEL = 555L;
  // 2026-11-06T10:00:00Z 근처 snowflake를 id로 쓴다
  private static final long BASE = ((1793959200000L - 1420070400000L) << 22);
  private static final long MINUTE = 60_000L << 22;

  @Autowired private NoticeIngestService service;
  @Autowired private NoticeRepository noticeRepository;
  @Autowired private TransactionTemplate tx;

  private final JsonMapper mapper = JsonMapper.builder().build();

  @BeforeEach
  void setUp() {
    noticeRepository.deleteAll();
  }

  private DiscordMessage message(String json) {
    return DiscordMessage.from(mapper.readTree(json));
  }

  private String plain(long id, String content, String attachmentsJson) {
    return "{\"id\":\""
        + id
        + "\",\"type\":0,\"channel_id\":\"555\",\"content\":\""
        + content
        + "\",\"attachments\":"
        + attachmentsJson
        + "}";
  }

  private static String attachment(long id) {
    return "{\"id\":\""
        + id
        + "\",\"filename\":\"a.png\",\"url\":\"https://cdn.discordapp.com/a/"
        + id
        + ".png\",\"proxy_url\":\"https://media.discordapp.net/a/"
        + id
        + ".png\"}";
  }

  private Optional<Notice> ingest(DiscordMessage m, NoticeOrigin origin) {
    return service.ingest(KoreanVendor.SWAGKEYS, CHANNEL, m, origin);
  }

  @Test
  @DisplayName("크로스포스트는 원래 id로 저장하고 사본 id를 남긴다")
  void 크로스포스트는_원래_id로_저장하고_사본_id를_남긴다() {
    long original = BASE + 1;
    long copy = BASE + 99;
    DiscordMessage m =
        message(
            "{\"id\":\""
                + copy
                + "\",\"type\":0,\"flags\":2,\"content\":\"판매 공지\",\"attachments\":[],"
                + "\"message_reference\":{\"message_id\":\""
                + original
                + "\"}}");

    Notice saved = ingest(m, NoticeOrigin.BOT).orElseThrow();

    assertThat(saved.getSourceMessageId()).isEqualTo(original);
    assertThat(saved.getChannelMessageId()).isEqualTo(copy);
    assertThat(saved.isHasText()).isTrue();
    assertThat(saved.getSummaryStatus()).isEqualTo(SummaryStatus.PENDING);
  }

  @Test
  @DisplayName("이미 있는 원래 id는 건너뛴다")
  void 이미_있는_원래_id는_건너뛴다() {
    long original = BASE + 1;
    ingest(message(plain(original, "백필", "[]")), NoticeOrigin.BACKFILL).orElseThrow();
    DiscordMessage again =
        message(
            "{\"id\":\""
                + (BASE + 50)
                + "\",\"type\":0,\"flags\":2,\"content\":\"백필\",\"attachments\":[],"
                + "\"message_reference\":{\"message_id\":\""
                + original
                + "\"}}");

    assertThat(ingest(again, NoticeOrigin.BOT)).isEmpty();
    assertThat(noticeRepository.count()).isEqualTo(1);
  }

  @Test
  @DisplayName("시스템 메시지는 버린다")
  void 시스템_메시지는_버린다() {
    DiscordMessage m =
        message("{\"id\":\"" + (BASE + 1) + "\",\"type\":12,\"content\":\"\",\"attachments\":[]}");

    assertThat(ingest(m, NoticeOrigin.BOT)).isEmpty();
    assertThat(noticeRepository.count()).isZero();
  }

  @Test
  @DisplayName("원문 첨부 주소를 R2 주소로 바꾼다")
  void 원문_첨부_주소를_R2_주소로_바꾼다() {
    DiscordMessage m = message(plain(BASE + 1, "글", "[" + attachment(777L) + "]"));

    Notice saved = ingest(m, NoticeOrigin.BOT).orElseThrow();

    tx.executeWithoutResult(
        s -> {
          Notice found = noticeRepository.findById(saved.getId()).orElseThrow();
          JsonNode first = mapper.readTree(found.getRaw()).path("attachments").get(0);
          assertThat(first.path("url").asString()).isEqualTo("https://img.test/notices/777.webp");
          assertThat(first.path("proxy_url").asString())
              .isEqualTo("https://img.test/notices/777.webp");
          assertThat(found.getAttachments()).singleElement().satisfies(a -> {
            assertThat(a.getSourceUrl()).isEqualTo("https://cdn.discordapp.com/a/777.png");
          });
        });
  }

  @Test
  @DisplayName("가져오기 첨부는 디스코드 주소를 남기지 않는다")
  void 가져오기_첨부는_디스코드_주소를_남기지_않는다() {
    DiscordMessage m = message(plain(BASE + 1, "글", "[" + attachment(778L) + "]"));

    Notice saved = ingest(m, NoticeOrigin.BACKFILL).orElseThrow();

    tx.executeWithoutResult(
        s ->
            assertThat(
                    noticeRepository.findById(saved.getId()).orElseThrow().getAttachments())
                .singleElement()
                .satisfies(a -> assertThat(a.getSourceUrl()).isNull()));
  }

  @Test
  @DisplayName("사진만 있는 메시지는 앞 공지에 묶는다")
  void 사진만_있는_메시지는_앞_공지에_묶는다() {
    Notice text = ingest(message(plain(BASE, "글 공지", "[]")), NoticeOrigin.BOT).orElseThrow();

    Notice photo =
        ingest(
                message(plain(BASE + MINUTE, "<@&1234> @KR", "[" + attachment(1) + "," + attachment(2) + "]")),
                NoticeOrigin.BOT)
            .orElseThrow();

    assertThat(photo.isHasText()).isFalse();
    assertThat(photo.getParent().getId()).isEqualTo(text.getId());
    assertThat(photo.getSummaryStatus()).isEqualTo(SummaryStatus.DONE);
  }

  @Test
  @DisplayName("요약이 끝난 공지에 사진이 오면 다시 요약 대상이 된다")
  void 요약이_끝난_공지에_사진이_오면_다시_요약_대상이_된다() {
    Notice text = ingest(message(plain(BASE, "글 공지", "[]")), NoticeOrigin.BOT).orElseThrow();
    noticeRepository.updateSummaryStatus(text.getId(), SummaryStatus.DONE);

    ingest(message(plain(BASE + MINUTE, "@SWG", "[" + attachment(3) + "]")), NoticeOrigin.BACKFILL)
        .orElseThrow();

    assertThat(noticeRepository.findById(text.getId()).orElseThrow().getSummaryStatus())
        .isEqualTo(SummaryStatus.PENDING);
  }

  @Test
  @DisplayName("24시간이 넘으면 묶지 않는다")
  void _24시간이_넘으면_묶지_않는다() {
    ingest(message(plain(BASE, "글 공지", "[]")), NoticeOrigin.BOT).orElseThrow();

    Notice photo =
        ingest(
                message(plain(BASE + 25 * 60 * MINUTE, "<@&1234>", "[" + attachment(4) + "]")),
                NoticeOrigin.BOT)
            .orElseThrow();

    assertThat(photo.getParent()).isNull();
    assertThat(photo.getSummaryStatus()).isEqualTo(SummaryStatus.DONE);
  }

  @Test
  @DisplayName("본문도 첨부도 없으면 요약하지 않는다")
  void 본문도_첨부도_없으면_요약하지_않는다() {
    Notice saved = ingest(message(plain(BASE, "<@123>  ", "[]")), NoticeOrigin.BOT).orElseThrow();

    assertThat(saved.isHasText()).isFalse();
    assertThat(saved.getSummaryStatus()).isEqualTo(SummaryStatus.DONE);
  }
}

package com.keycheck.notice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.keycheck.MutableClock;
import com.keycheck.TestcontainersConfiguration;
import com.keycheck.notice.client.DiscordClient;
import com.keycheck.notice.client.DiscordRateLimitedException;
import com.keycheck.notice.config.NoticeProperties;
import com.keycheck.notice.dto.DiscordMessage;
import com.keycheck.notice.repository.NoticeChannelCursorRepository;
import com.keycheck.notice.repository.NoticeRepository;
import com.keycheck.post.domain.KoreanVendor;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class NoticeCollectorTest {
  private static final String SWAG = "9001";
  private static final String GEON = "9002";
  private static final long BASE = ((1793959200000L - 1420070400000L) << 22);

  @MockitoBean private DiscordClient client;
  @Autowired private NoticeIngestService ingestService;
  @Autowired private NoticeChannelCursorRepository cursorRepository;
  @Autowired private NoticeRepository noticeRepository;

  private final MutableClock clock = new MutableClock();
  private final JsonMapper mapper = JsonMapper.builder().build();

  @BeforeEach
  void setUp() {
    noticeRepository.deleteAll();
    cursorRepository.deleteAll();
    clock.set(Instant.parse("2026-11-08T00:00:00Z"));
  }

  private NoticeCollector collector(Map<KoreanVendor, String> channels) {
    NoticeProperties properties =
        new NoticeProperties(
            "token", "http://x", "ua", channels, Duration.ofHours(24),
            new NoticeProperties.Summary(Duration.ofMinutes(10)));
    return new NoticeCollector(client, ingestService, cursorRepository, properties, clock);
  }

  private DiscordMessage msg(long id) {
    return DiscordMessage.from(
        mapper.readTree(
            "{\"id\":\"" + id + "\",\"type\":0,\"content\":\"글 " + id + "\",\"attachments\":[]}"));
  }

  @Test
  @DisplayName("채널마다 커서부터 이어 받고 커서를 전진한다")
  void 채널마다_커서부터_이어_받고_커서를_전진한다() {
    when(client.messagesAfter(eq(SWAG), isNull())).thenReturn(List.of(msg(BASE + 1), msg(BASE + 2)));
    when(client.messagesAfter(eq(SWAG), eq(String.valueOf(BASE + 2)))).thenReturn(List.of());
    NoticeCollector collector = collector(Map.of(KoreanVendor.SWAGKEYS, SWAG));

    collector.collect();
    collector.collect();

    assertThat(noticeRepository.count()).isEqualTo(2);
    assertThat(cursorRepository.findById(9001L).orElseThrow().getLastMessageId())
        .isEqualTo(BASE + 2);
    verify(client, times(1)).messagesAfter(SWAG, null);
    verify(client, times(1)).messagesAfter(SWAG, String.valueOf(BASE + 2));
  }

  @Test
  @DisplayName("100개가 차면 이어서 더 받는다")
  void _100개가_차면_이어서_더_받는다() {
    List<DiscordMessage> first = new ArrayList<>();
    for (int i = 1; i <= 100; i++) {
      first.add(msg(BASE + i));
    }
    when(client.messagesAfter(eq(SWAG), isNull())).thenReturn(first);
    when(client.messagesAfter(eq(SWAG), eq(String.valueOf(BASE + 100))))
        .thenReturn(List.of(msg(BASE + 101)));

    collector(Map.of(KoreanVendor.SWAGKEYS, SWAG)).collect();

    assertThat(noticeRepository.count()).isEqualTo(101);
    assertThat(cursorRepository.findById(9001L).orElseThrow().getLastMessageId())
        .isEqualTo(BASE + 101);
  }

  @Test
  @DisplayName("429면 기다릴 시간까지 조회하지 않는다")
  void _429면_기다릴_시간까지_조회하지_않는다() {
    when(client.messagesAfter(any(), any()))
        .thenThrow(new DiscordRateLimitedException(Duration.ofSeconds(30)));
    NoticeCollector collector = collector(Map.of(KoreanVendor.SWAGKEYS, SWAG));

    collector.collect();
    Mockito.clearInvocations(client);
    clock.advance(Duration.ofSeconds(10));
    collector.collect();
    verify(client, never()).messagesAfter(any(), any());

    clock.advance(Duration.ofSeconds(21));
    collector.collect();
    verify(client, times(1)).messagesAfter(any(), any());
  }

  @Test
  @DisplayName("채널 id가 비면 그 판매처는 건너뛴다")
  void 채널_id가_비면_그_판매처는_건너뛴다() {
    when(client.messagesAfter(any(), any())).thenReturn(List.of());
    Map<KoreanVendor, String> channels = new java.util.EnumMap<>(KoreanVendor.class);
    channels.put(KoreanVendor.SWAGKEYS, SWAG);
    channels.put(KoreanVendor.GEONWORKS, "");

    collector(channels).collect();

    verify(client, times(1)).messagesAfter(any(), any());
    verify(client, never()).messagesAfter(eq(GEON), any());
  }
}

package com.keycheck.notice.service;

import com.keycheck.notice.client.DiscordClient;
import com.keycheck.notice.client.DiscordRateLimitedException;
import com.keycheck.notice.client.DiscordUnavailableException;
import com.keycheck.notice.config.NoticeProperties;
import com.keycheck.notice.domain.NoticeChannelCursor;
import com.keycheck.notice.domain.NoticeOrigin;
import com.keycheck.notice.dto.DiscordMessage;
import com.keycheck.notice.repository.NoticeChannelCursorRepository;
import com.keycheck.post.domain.KoreanVendor;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/** 판매처 채널마다 커서 뒤의 새 메시지를 받아 공지로 저장한다. */
@Slf4j
@Component
@RequiredArgsConstructor
public class NoticeCollector {
  private static final int PAGE_SIZE = 100;

  private final DiscordClient client;
  private final NoticeIngestService ingestService;
  private final NoticeChannelCursorRepository cursorRepository;
  private final NoticeProperties properties;
  private final Clock clock;

  private final AtomicBoolean tokenWarned = new AtomicBoolean();
  private volatile Instant pausedUntil;

  public void collect() {
    if (!StringUtils.hasText(properties.getBotToken())) {
      if (tokenWarned.compareAndSet(false, true)) {
        log.warn("디스코드 봇 토큰이 없어 공지를 수집하지 않아요");
      }
      return;
    }
    Instant now = clock.instant();
    if (pausedUntil != null && now.isBefore(pausedUntil)) {
      return;
    }
    for (Map.Entry<KoreanVendor, String> entry : properties.configuredChannels().entrySet()) {
      try {
        collectChannel(entry.getKey(), entry.getValue());
      } catch (DiscordRateLimitedException e) {
        pausedUntil = clock.instant().plus(e.getRetryAfter());
        log.warn("디스코드 요청 한도 초과, {}까지 쉬어요", pausedUntil);
        return;
      } catch (DiscordUnavailableException e) {
        log.warn("디스코드 채널 {} 조회 실패: {}", entry.getValue(), e.getMessage());
      }
    }
  }

  private void collectChannel(KoreanVendor vendor, String channelIdText) {
    long channelId = Long.parseLong(channelIdText);
    NoticeChannelCursor cursor = cursorRepository.findById(channelId).orElse(null);
    while (true) {
      String after = cursor == null ? null : String.valueOf(cursor.getLastMessageId());
      List<DiscordMessage> batch = client.messagesAfter(channelIdText, after);
      long max = 0;
      for (DiscordMessage message : batch) {
        ingestService.ingest(vendor, channelId, message, NoticeOrigin.BOT);
        max = Math.max(max, Long.parseLong(message.getId()));
      }
      if (max > 0) {
        if (cursor == null) {
          cursor = NoticeChannelCursor.create(channelId, max);
        } else {
          cursor.advance(max);
        }
        cursor = cursorRepository.save(cursor);
      }
      if (batch.size() < PAGE_SIZE) {
        return;
      }
    }
  }
}

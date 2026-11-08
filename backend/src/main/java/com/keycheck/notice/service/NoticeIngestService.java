package com.keycheck.notice.service;

import com.keycheck.image.service.ThumbUrls;
import com.keycheck.notice.config.NoticeProperties;
import com.keycheck.notice.domain.Notice;
import com.keycheck.notice.domain.NoticeKeys;
import com.keycheck.notice.domain.NoticeOrigin;
import com.keycheck.notice.domain.Snowflakes;
import com.keycheck.notice.dto.DiscordAttachment;
import com.keycheck.notice.dto.DiscordMessage;
import com.keycheck.notice.repository.NoticeRepository;
import com.keycheck.post.domain.KoreanVendor;
import com.keycheck.post.domain.SummaryStatus;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

/** 디스코드 메시지 하나를 공지로 저장한다. 같은 원래 메시지는 한 번만 저장한다. */
@Service
@RequiredArgsConstructor
public class NoticeIngestService {
  private static final int TYPE_DEFAULT = 0;

  // API 형태(<@&123>, <@123>)와 손으로 바꾼 백필 형태(@KR)의 멘션
  private static final Pattern MENTION = Pattern.compile("<@[!&]?\\d+>|@\\S+");

  private final NoticeRepository noticeRepository;
  private final NoticeProperties properties;
  private final ThumbUrls thumbUrls;

  /**
   * @return 저장한 공지. 버리거나(시스템 메시지) 이미 있으면 empty
   */
  @Transactional
  public Optional<Notice> ingest(
      KoreanVendor vendor, long channelId, DiscordMessage message, NoticeOrigin origin) {
    if (message.getType() != TYPE_DEFAULT) {
      return Optional.empty();
    }
    long sourceId = Long.parseLong(message.sourceMessageId());
    if (noticeRepository.existsBySourceMessageId(sourceId)) {
      return Optional.empty();
    }
    Instant postedAt = Snowflakes.time(sourceId);
    String content = message.getContent() == null ? "" : message.getContent();
    boolean hasText = !MENTION.matcher(content).replaceAll("").isBlank();
    List<DiscordAttachment> attachments = message.getAttachments();

    Long channelMessageId = message.isCrosspost() ? Long.parseLong(message.getId()) : null;
    Notice notice =
        Notice.create(
            sourceId,
            channelMessageId,
            channelId,
            vendor,
            origin,
            postedAt,
            content,
            hasText,
            rewrittenRaw(message));
    for (int i = 0; i < attachments.size(); i++) {
      DiscordAttachment a = attachments.get(i);
      notice.addAttachment(
          Long.parseLong(a.getId()),
          i,
          a.getFilename(),
          origin == NoticeOrigin.BACKFILL ? null : a.getUrl());
    }

    if (!hasText && !attachments.isEmpty()) {
      Optional<Notice> parent =
          noticeRepository.findLatestTextNotice(
              vendor, postedAt.minus(properties.getAttachWindow()), postedAt);
      parent.ifPresent(
          p -> {
            notice.setParent(p);
            if (p.getSummaryStatus() == SummaryStatus.DONE) {
              p.setSummaryStatus(SummaryStatus.PENDING);
            }
          });
      notice.setSummaryStatus(SummaryStatus.DONE);
    } else if (!hasText) {
      notice.setSummaryStatus(SummaryStatus.DONE);
    }
    return Optional.of(noticeRepository.save(notice));
  }

  /** 원문 JSON의 첨부 주소를 우리 저장소 주소로 바꾼다. 공개 주소가 없으면 빈 문자열 */
  private String rewrittenRaw(DiscordMessage message) {
    JsonNode copy = message.getRaw().deepCopy();
    JsonNode attachments = copy.path("attachments");
    if (attachments.isArray()) {
      for (JsonNode a : attachments) {
        if (!(a instanceof ObjectNode node) || !node.hasNonNull("id")) {
          continue;
        }
        String url = thumbUrls.url(NoticeKeys.archive(node.get("id").asLong()));
        String value = url == null ? "" : url;
        if (node.has("url")) {
          node.put("url", value);
        }
        if (node.has("proxy_url")) {
          node.put("proxy_url", value);
        }
      }
    }
    return copy.toString();
  }
}

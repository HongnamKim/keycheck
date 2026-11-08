package com.keycheck.notice.dto;

import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import tools.jackson.databind.JsonNode;

/** 디스코드 메시지에서 쓰는 부분. 디스코드 API 응답과 손으로 바꾼 백필 JSON(flags·message_reference 없음)을 모두 읽는다 */
@Getter
@RequiredArgsConstructor
public class DiscordMessage {
  private static final int FLAG_CROSSPOSTED = 2;

  private final String id;
  private final int type;
  private final String channelId;
  private final String content;
  private final int flags;
  private final String referencedMessageId;
  private final List<DiscordAttachment> attachments;
  private final JsonNode raw;

  public static DiscordMessage from(JsonNode node) {
    List<DiscordAttachment> attachments = new ArrayList<>();
    JsonNode attachmentNodes = node.path("attachments");
    if (attachmentNodes.isArray()) {
      for (JsonNode a : attachmentNodes) {
        attachments.add(
            new DiscordAttachment(
                text(a, "id"), text(a, "filename"), text(a, "url")));
      }
    }
    return new DiscordMessage(
        text(node, "id"),
        node.path("type").asInt(0),
        text(node, "channel_id"),
        text(node, "content"),
        node.path("flags").asInt(0),
        text(node.path("message_reference"), "message_id"),
        List.copyOf(attachments),
        node);
  }

  /** 다른 채널(공지 채널)에서 퍼 온 메시지 */
  public boolean isCrosspost() {
    return (flags & FLAG_CROSSPOSTED) != 0;
  }

  /** 크로스포스트면 원래 메시지 id, 아니면 자기 id */
  public String sourceMessageId() {
    return isCrosspost() && referencedMessageId != null ? referencedMessageId : id;
  }

  private static String text(JsonNode node, String field) {
    JsonNode value = node.get(field);
    return value == null || value.isNull() ? null : value.asString();
  }
}

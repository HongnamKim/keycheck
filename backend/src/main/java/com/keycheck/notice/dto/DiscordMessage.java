package com.keycheck.notice.dto;

import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import tools.jackson.databind.JsonNode;

/**
 * 디스코드 메시지에서 쓰는 부분. 디스코드 API 응답과 손으로 바꾼 백필 JSON(flags·message_reference 없음)을 모두 읽는다. 사용자가
 * 전달(forward)한 메시지는 바깥이 비어 있고 원래 공지가 message_snapshots 안에 있어, 글·첨부를 안쪽에서 읽는다. 종류는 바깥 것(전달은
 * 늘 보통 메시지)을 써서 원래 공지가 답글이어도 받는다.
 */
@Getter
@RequiredArgsConstructor
public class DiscordMessage {
  private static final int FLAG_CROSSPOSTED = 2;

  /** message_reference.type: 전달 */
  private static final int REFERENCE_FORWARD = 1;

  private final String id;
  private final int type;
  private final String channelId;
  private final String content;
  private final int flags;
  private final String referencedMessageId;

  /** 사용자가 다른 채널의 메시지를 전달한 것 (글·첨부는 원래 메시지 것) */
  private final boolean forward;

  private final List<DiscordAttachment> attachments;
  private final JsonNode raw;

  public static DiscordMessage from(JsonNode node) {
    JsonNode reference = node.path("message_reference");
    JsonNode snapshot = node.path("message_snapshots").path(0).path("message");
    boolean forward = reference.path("type").asInt(0) == REFERENCE_FORWARD && snapshot.isObject();
    JsonNode body = forward ? snapshot : node;
    List<DiscordAttachment> attachments = new ArrayList<>();
    JsonNode attachmentNodes = body.path("attachments");
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
        text(body, "content"),
        node.path("flags").asInt(0),
        text(reference, "message_id"),
        forward,
        List.copyOf(attachments),
        node);
  }

  /** 다른 채널(공지 채널)에서 퍼 온 메시지 */
  public boolean isCrosspost() {
    return (flags & FLAG_CROSSPOSTED) != 0;
  }

  /** 크로스포스트나 전달이면 원래 메시지 id, 아니면 자기 id */
  public String sourceMessageId() {
    return (isCrosspost() || forward) && referencedMessageId != null ? referencedMessageId : id;
  }

  private static String text(JsonNode node, String field) {
    JsonNode value = node.get(field);
    return value == null || value.isNull() ? null : value.asString();
  }
}

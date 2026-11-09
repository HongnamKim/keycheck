package com.keycheck.notice.dto;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class DiscordMessageTest {
  private final JsonMapper mapper = JsonMapper.builder().build();

  private DiscordMessage parse(String json) {
    return DiscordMessage.from(mapper.readTree(json));
  }

  @Test
  @DisplayName("크로스포스트는 원래 메시지 id를 쓴다")
  void crosspostUsesReferencedMessageId() {
    DiscordMessage crosspost =
        parse(
            "{\"id\":\"100\",\"type\":0,\"channel_id\":\"1\",\"content\":\"x\",\"flags\":2,"
                + "\"message_reference\":{\"message_id\":\"999\"},\"attachments\":[]}");
    DiscordMessage plain =
        parse("{\"id\":\"100\",\"type\":0,\"channel_id\":\"1\",\"content\":\"x\",\"flags\":0}");

    assertThat(crosspost.isCrosspost()).isTrue();
    assertThat(crosspost.sourceMessageId()).isEqualTo("999");
    assertThat(plain.isCrosspost()).isFalse();
    assertThat(plain.sourceMessageId()).isEqualTo("100");
  }

  @Test
  @DisplayName("전달된 메시지는 안쪽 원래 공지의 글·첨부와 원래 메시지 id를 쓴다")
  void forwardUsesSnapshot() {
    DiscordMessage forward =
        parse(
            "{\"id\":\"100\",\"type\":0,\"channel_id\":\"1\",\"content\":\"\",\"flags\":16384,"
                + "\"attachments\":[],"
                + "\"message_reference\":{\"type\":1,\"channel_id\":\"7\",\"message_id\":\"999\",\"guild_id\":\"8\"},"
                + "\"message_snapshots\":[{\"message\":{\"type\":0,\"content\":\"원래 공지\",\"flags\":0,"
                + "\"timestamp\":\"2026-11-09T07:19:17.409000+00:00\","
                + "\"attachments\":[{\"id\":\"5\",\"filename\":\"p.png\",\"url\":\"https://cdn.test/p.png\"}]}}]}");

    assertThat(forward.isForward()).isTrue();
    assertThat(forward.isCrosspost()).isFalse();
    assertThat(forward.getId()).isEqualTo("100");
    assertThat(forward.sourceMessageId()).isEqualTo("999");
    assertThat(forward.getType()).isZero();
    assertThat(forward.getContent()).isEqualTo("원래 공지");
    assertThat(forward.getAttachments()).extracting(DiscordAttachment::getId).containsExactly("5");
  }

  @Test
  @DisplayName("원래 공지가 답글이어도 전달 메시지의 종류(보통 메시지)를 쓴다")
  void forwardOfReplyIsDefaultType() {
    DiscordMessage forward =
        parse(
            "{\"id\":\"100\",\"type\":0,\"content\":\"\",\"flags\":16384,\"attachments\":[],"
                + "\"message_reference\":{\"type\":1,\"message_id\":\"999\"},"
                + "\"message_snapshots\":[{\"message\":{\"type\":19,\"content\":\"답글 공지\",\"attachments\":[]}}]}");

    assertThat(forward.getType()).isZero();
    assertThat(forward.getContent()).isEqualTo("답글 공지");
  }

  @Test
  @DisplayName("전달 표시가 없거나 안쪽 메시지가 없으면 일반 메시지로 읽는다")
  void notForwardWithoutSnapshotOrForwardType() {
    DiscordMessage reply =
        parse(
            "{\"id\":\"100\",\"type\":0,\"content\":\"답글\",\"flags\":0,\"attachments\":[],"
                + "\"message_reference\":{\"type\":0,\"message_id\":\"999\"}}");
    DiscordMessage emptyForward =
        parse(
            "{\"id\":\"101\",\"type\":0,\"content\":\"\",\"flags\":0,\"attachments\":[],"
                + "\"message_reference\":{\"type\":1,\"message_id\":\"999\"}}");

    assertThat(reply.isForward()).isFalse();
    assertThat(reply.sourceMessageId()).isEqualTo("100");
    assertThat(reply.getContent()).isEqualTo("답글");
    assertThat(emptyForward.isForward()).isFalse();
    assertThat(emptyForward.sourceMessageId()).isEqualTo("101");
  }

  @Test
  @DisplayName("API 응답의 첨부파일을 읽는다")
  void parsesAttachments() {
    DiscordMessage message =
        parse(
            "{\"id\":\"1\",\"type\":0,\"channel_id\":\"2\",\"content\":\"hi\",\"flags\":0,"
                + "\"attachments\":[{\"id\":\"7\",\"filename\":\"a.png\",\"url\":\"https://cdn.test/a.png\","
                + "\"proxy_url\":\"https://p.test/a.png\",\"size\":10,\"content_type\":\"image/png\","
                + "\"width\":1,\"height\":1}]}");

    assertThat(message.getAttachments()).hasSize(1);
    assertThat(message.getAttachments().get(0).getFilename()).isEqualTo("a.png");
    assertThat(message.getAttachments().get(0).getUrl()).isEqualTo("https://cdn.test/a.png");
    assertThat(message.getRaw().get("id").asString()).isEqualTo("1");
  }

  @Test
  @DisplayName("flags와 message_reference가 없는 백필 JSON도 읽는다")
  void parsesBackfillShape() {
    DiscordMessage message =
        parse(
            "{\"id\":\"5\",\"type\":0,\"channel_id\":\"2\",\"timestamp\":\"2025-01-01T00:00:00+00:00\","
                + "\"edited\":null,\"author\":{\"username\":\"u\"},\"content\":\"본문\","
                + "\"attachments\":[{\"id\":\"9\",\"filename\":\"b.png\",\"url\":\"https://cdn.test/b.png\","
                + "\"local_file\":\"b.png\"}]}");

    assertThat(message.getFlags()).isZero();
    assertThat(message.isCrosspost()).isFalse();
    assertThat(message.getReferencedMessageId()).isNull();
    assertThat(message.sourceMessageId()).isEqualTo("5");
    assertThat(message.getContent()).isEqualTo("본문");
    assertThat(message.getAttachments()).extracting(DiscordAttachment::getId).containsExactly("9");
  }
}

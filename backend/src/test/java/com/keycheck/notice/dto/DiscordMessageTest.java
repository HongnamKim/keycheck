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

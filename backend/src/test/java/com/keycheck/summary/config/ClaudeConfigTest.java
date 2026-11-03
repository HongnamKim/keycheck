package com.keycheck.summary.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.anthropic.client.AnthropicClient;
import com.anthropic.models.messages.MessageCreateParams;
import com.keycheck.summary.FakeAnthropicServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ClaudeConfigTest {
  private FakeAnthropicServer server;

  @BeforeEach
  void setUp() throws Exception {
    server = new FakeAnthropicServer();
    server.responseBody =
        """
        {"id": "msg_1", "type": "message", "role": "assistant", "model": "claude-opus-5-5",
         "content": [{"type": "text", "text": "OK"}], "stop_reason": "end_turn",
         "usage": {"input_tokens": 1, "output_tokens": 1}}
        """;
  }

  @AfterEach
  void tearDown() {
    server.close();
  }

  @Test
  @DisplayName("설정에 API 키가 있으면 그 키로 요청한다")
  void usesConfiguredApiKey() {
    AnthropicClient client = server.client(ClaudeConfig.clientBuilder("sk-ant-test"));
    try {
      client
          .messages()
          .create(
              MessageCreateParams.builder()
                  .model("claude-opus-5-5")
                  .maxTokens(16)
                  .addUserMessage("hi")
                  .build());
    } finally {
      client.close();
    }

    // SDK 3.4.0은 API 키를 Authorization: Bearer 헤더로 보낸다 (anthropic-version 2026-10-21)
    assertThat(server.requestHeaders.getFirst("Authorization")).isEqualTo("Bearer sk-ant-test");
  }

  @Test
  @DisplayName("설정에 API 키가 없거나 비었으면 SDK 기본 인증 순서(fromEnv)를 따른다")
  void blankApiKeyFallsBackToSdkDefault() {
    // fromEnv는 이 컴퓨터의 로그인 프로필 등을 읽고 토큰을 받으러 갈 수 있어 테스트에서 만들지 않는다
    assertThat(ClaudeConfig.usesConfiguredKey(null)).isFalse();
    assertThat(ClaudeConfig.usesConfiguredKey("")).isFalse();
    assertThat(ClaudeConfig.usesConfiguredKey("  ")).isFalse();
    assertThat(ClaudeConfig.usesConfiguredKey("sk-ant-test")).isTrue();
  }
}

package com.keycheck.notice.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withResourceNotFound;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.keycheck.notice.dto.DiscordMessage;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class DiscordClientTest {
  private final RestClient.Builder builder =
      RestClient.builder()
          .baseUrl("https://discord.test/api/v10")
          .defaultHeader(HttpHeaders.AUTHORIZATION, "Bot test-token");
  private final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
  private final DiscordClient client = new DiscordClient(builder.build());

  private static String message(String id) {
    return "{\"id\":\"" + id + "\",\"type\":0,\"channel_id\":\"111\",\"content\":\"c" + id
        + "\",\"flags\":0,\"attachments\":[]}";
  }

  @Test
  @DisplayName("after가 없으면 최근 100개를 요청한다")
  void requestsLatest100WithoutAfter() {
    server
        .expect(requestTo("https://discord.test/api/v10/channels/111/messages?limit=100"))
        .andExpect(header(HttpHeaders.AUTHORIZATION, "Bot test-token"))
        .andRespond(withSuccess("[" + message("10") + "]", MediaType.APPLICATION_JSON));

    List<DiscordMessage> result = client.messagesAfter("111", null);

    assertThat(result).hasSize(1);
    server.verify();
  }

  @Test
  @DisplayName("after 조회 결과를 오래된 순으로 돌려준다")
  void returnsOldestFirst() {
    server
        .expect(requestTo("https://discord.test/api/v10/channels/111/messages?limit=100&after=5"))
        .andRespond(
            withSuccess(
                "[" + message("30") + "," + message("20") + "," + message("10") + "]",
                MediaType.APPLICATION_JSON));

    List<DiscordMessage> result = client.messagesAfter("111", "5");

    assertThat(result).extracting(DiscordMessage::getId).containsExactly("10", "20", "30");
  }

  @Test
  @DisplayName("429면 기다릴 시간을 담아 던진다")
  void throwsRateLimitedWithRetryAfter() {
    server
        .expect(requestTo("https://discord.test/api/v10/channels/111/messages?limit=100"))
        .andRespond(
            withStatus(HttpStatus.TOO_MANY_REQUESTS)
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"message\":\"rate limited\",\"retry_after\":1.5,\"global\":false}"));

    assertThatThrownBy(() -> client.messagesAfter("111", null))
        .isInstanceOfSatisfying(
            DiscordRateLimitedException.class,
            e -> assertThat(e.getRetryAfter()).isEqualTo(Duration.ofMillis(1500)));
  }

  @Test
  @DisplayName("429에 본문이 없으면 Retry-After 헤더를 쓴다")
  void usesRetryAfterHeaderWhenBodyMissing() {
    server
        .expect(requestTo("https://discord.test/api/v10/channels/111/messages?limit=100"))
        .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS).header("Retry-After", "7"));

    assertThatThrownBy(() -> client.messagesAfter("111", null))
        .isInstanceOfSatisfying(
            DiscordRateLimitedException.class,
            e -> assertThat(e.getRetryAfter()).isEqualTo(Duration.ofSeconds(7)));
  }

  @Test
  @DisplayName("429에 정보가 전혀 없으면 60초를 기다린다")
  void defaultsRetryAfterTo60Seconds() {
    server
        .expect(requestTo("https://discord.test/api/v10/channels/111/messages?limit=100"))
        .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));

    assertThatThrownBy(() -> client.messagesAfter("111", null))
        .isInstanceOfSatisfying(
            DiscordRateLimitedException.class,
            e -> assertThat(e.getRetryAfter()).isEqualTo(Duration.ofSeconds(60)));
  }

  @Test
  @DisplayName("메시지 하나를 id로 조회한다")
  void fetchesSingleMessage() {
    server
        .expect(requestTo("https://discord.test/api/v10/channels/111/messages/20"))
        .andRespond(withSuccess(message("20"), MediaType.APPLICATION_JSON));

    assertThat(client.message("111", "20")).map(DiscordMessage::getId).contains("20");
  }

  @Test
  @DisplayName("메시지가 없으면 empty")
  void returnsEmptyWhenNotFound() {
    server
        .expect(requestTo("https://discord.test/api/v10/channels/111/messages/20"))
        .andRespond(withResourceNotFound());

    Optional<DiscordMessage> result = client.message("111", "20");

    assertThat(result).isEmpty();
  }

  @Test
  @DisplayName("서버 오류는 사용할 수 없음으로 던진다")
  void throwsUnavailableOnServerError() {
    server
        .expect(requestTo("https://discord.test/api/v10/channels/111/messages?limit=100"))
        .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));

    assertThatThrownBy(() -> client.messagesAfter("111", null))
        .isInstanceOf(DiscordUnavailableException.class)
        .hasMessageNotContaining("test-token");
  }
}

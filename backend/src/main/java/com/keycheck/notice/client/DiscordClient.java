package com.keycheck.notice.client;

import com.keycheck.notice.dto.DiscordMessage;
import java.math.BigInteger;
import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** 디스코드 REST API(읽기 전용) 클라이언트. 봇 토큰은 RestClient의 기본 헤더에만 있다. */
@Slf4j
@RequiredArgsConstructor
public class DiscordClient {
  private static final int PAGE_SIZE = 100;
  private static final Duration DEFAULT_RETRY_AFTER = Duration.ofSeconds(60);
  private static final JsonMapper MAPPER = JsonMapper.builder().build();

  private final RestClient restClient;

  /** afterId(없으면 최근)보다 새 메시지를 id 오름차순(오래된 것 먼저)으로 돌려준다. 디스코드는 새 것부터 준다 */
  public List<DiscordMessage> messagesAfter(String channelId, String afterId) {
    String path =
        "/channels/" + channelId + "/messages?limit=" + PAGE_SIZE
            + (StringUtils.hasText(afterId) ? "&after=" + afterId : "");
    JsonNode body = get(path);
    if (body == null) {
      log.warn("디스코드 채널을 찾지 못함(404 또는 빈 응답): channelId={}. 채널 ID 설정을 확인하세요", channelId);
      return List.of();
    }
    if (!body.isArray()) {
      return List.of();
    }
    return body.valueStream()
        .map(DiscordMessage::from)
        .sorted(Comparator.comparing(m -> new BigInteger(m.getId())))
        .toList();
  }

  /** 메시지가 지워졌으면 empty. 만료된 첨부 주소를 새로 받을 때 쓴다 */
  public Optional<DiscordMessage> message(String channelId, String messageId) {
    JsonNode body = get("/channels/" + channelId + "/messages/" + messageId);
    return body == null ? Optional.empty() : Optional.of(DiscordMessage.from(body));
  }

  private JsonNode get(String path) {
    try {
      String body = restClient.get().uri(path).retrieve().body(String.class);
      return body == null || body.isBlank() ? null : MAPPER.readTree(body);
    } catch (RestClientResponseException e) {
      HttpStatus status = HttpStatus.resolve(e.getStatusCode().value());
      if (status == HttpStatus.TOO_MANY_REQUESTS) {
        throw new DiscordRateLimitedException(retryAfter(e));
      }
      if (status == HttpStatus.NOT_FOUND) {
        return null;
      }
      throw new DiscordUnavailableException("디스코드 응답 오류 " + e.getStatusCode().value());
    } catch (RestClientException e) {
      throw new DiscordUnavailableException(
          "디스코드 연결 실패 " + e.getClass().getSimpleName());
    } catch (RuntimeException e) {
      throw new DiscordUnavailableException(
          "디스코드 응답을 읽지 못함 " + e.getClass().getSimpleName());
    }
  }

  private static Duration retryAfter(RestClientResponseException e) {
    try {
      JsonNode node = MAPPER.readTree(e.getResponseBodyAsString());
      JsonNode seconds = node == null ? null : node.get("retry_after");
      if (seconds != null && seconds.isNumber()) {
        return Duration.ofMillis(Math.round(seconds.asDouble() * 1000));
      }
    } catch (RuntimeException ignored) {
      // 본문이 JSON이 아니면 헤더를 본다
    }
    String header = e.getResponseHeaders() == null ? null : e.getResponseHeaders().getFirst("Retry-After");
    if (header != null) {
      try {
        return Duration.ofMillis(Math.round(Double.parseDouble(header.trim()) * 1000));
      } catch (NumberFormatException ignored) {
        // 숫자가 아니면 기본값
      }
    }
    return DEFAULT_RETRY_AFTER;
  }
}

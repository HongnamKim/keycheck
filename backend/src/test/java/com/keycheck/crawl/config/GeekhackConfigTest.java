package com.keycheck.crawl.config;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

import com.keycheck.crawl.client.GeekhackClient;
import java.io.IOException;
import java.net.ServerSocket;
import java.time.Duration;
import java.time.ZoneId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.ResourceAccessException;

class GeekhackConfigTest {

  @Test
  @DisplayName("geekhack이 연결을 받고 응답하지 않으면 무기한 기다리지 않고 예외를 낸다")
  void requestTimesOutWhenServerDoesNotRespond() throws IOException {
    // 연결은 받아 두기만 하고(backlog) 응답하지 않는 서버
    try (ServerSocket server = new ServerSocket(0)) {
      GeekhackProperties properties =
          new GeekhackProperties(
              "http://localhost:" + server.getLocalPort(),
              "keycheck-test",
              Duration.ofSeconds(2),
              ZoneId.of("America/Chicago"));
      GeekhackClient client = new GeekhackConfig().geekhackClient(properties);

      assertTimeoutPreemptively(
          Duration.ofSeconds(30),
          () ->
              assertThatThrownBy(() -> client.fetchTopicLastPage(1L))
                  .isInstanceOf(ResourceAccessException.class));
    }
  }
}

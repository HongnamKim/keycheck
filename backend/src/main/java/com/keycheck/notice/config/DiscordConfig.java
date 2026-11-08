package com.keycheck.notice.config;

import com.keycheck.notice.client.DiscordClient;
import java.net.http.HttpClient;
import java.time.Duration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
public class DiscordConfig {
  private static final Duration TIMEOUT = Duration.ofSeconds(10);

  @Bean
  public DiscordClient discordClient(NoticeProperties properties) {
    JdkClientHttpRequestFactory requestFactory =
        new JdkClientHttpRequestFactory(HttpClient.newBuilder().connectTimeout(TIMEOUT).build());
    requestFactory.setReadTimeout(TIMEOUT);
    return new DiscordClient(
        RestClient.builder()
            .requestFactory(requestFactory)
            .baseUrl(properties.getApiBaseUrl())
            .defaultHeader(HttpHeaders.USER_AGENT, properties.getUserAgent())
            .defaultHeader(HttpHeaders.AUTHORIZATION, "Bot " + properties.getBotToken())
            .build());
  }
}

package com.keycheck.crawl;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.web.client.RestClient;

@Configuration
public class GeekhackConfig {

  @Bean
  public RequestGate requestGate(GeekhackProperties properties, Clock clock) {
    return new RequestGate(properties.minInterval(), clock);
  }

  @Bean
  public GeekhackClient geekhackClient(GeekhackProperties properties) {
    return new GeekhackClient(
        RestClient.builder()
            .baseUrl(properties.baseUrl())
            .defaultHeader(HttpHeaders.USER_AGENT, properties.userAgent())
            .build());
  }
}

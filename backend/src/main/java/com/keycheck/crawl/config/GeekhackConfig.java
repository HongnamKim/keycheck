package com.keycheck.crawl.config;

import com.keycheck.crawl.client.GeekhackClient;
import com.keycheck.crawl.client.RequestGate;
import com.keycheck.crawl.parser.TopicParser;
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.web.client.RestClient;

@Configuration
public class GeekhackConfig {

  @Bean
  public RequestGate requestGate(GeekhackProperties properties, Clock clock) {
    return new RequestGate(properties.getMinInterval(), clock);
  }

  @Bean
  public TopicParser topicParser(GeekhackProperties properties, Clock clock) {
    return new TopicParser(properties.getTimeZone(), clock);
  }

  @Bean
  public GeekhackClient geekhackClient(GeekhackProperties properties) {
    return new GeekhackClient(
        RestClient.builder()
            .baseUrl(properties.getBaseUrl())
            .defaultHeader(HttpHeaders.USER_AGENT, properties.getUserAgent())
            .build());
  }
}

package com.keycheck.crawl.config;

import com.keycheck.crawl.client.GeekhackClient;
import com.keycheck.crawl.client.RequestGate;
import com.keycheck.crawl.parser.GeekhackTimeParser;
import com.keycheck.crawl.parser.TopicParser;
import java.net.http.HttpClient;
import java.time.Clock;
import java.time.Duration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
public class GeekhackConfig {
  /** 상세 화면의 조회수 갱신(사용자 요청)에서도 부르므로, 응답이 없으면 오래 묶이지 않고 실패하게 한다 */
  private static final Duration TIMEOUT = Duration.ofSeconds(10);

  @Bean
  public RequestGate requestGate(GeekhackProperties properties, Clock clock) {
    return new RequestGate(properties.getMinInterval(), clock);
  }

  @Bean
  public GeekhackTimeParser geekhackTimeParser(GeekhackProperties properties, Clock clock) {
    return new GeekhackTimeParser(properties.getTimeZone(), clock);
  }

  @Bean
  public TopicParser topicParser(GeekhackTimeParser timeParser) {
    return new TopicParser(timeParser);
  }

  @Bean
  public GeekhackClient geekhackClient(GeekhackProperties properties) {
    JdkClientHttpRequestFactory requestFactory =
        new JdkClientHttpRequestFactory(HttpClient.newBuilder().connectTimeout(TIMEOUT).build());
    requestFactory.setReadTimeout(TIMEOUT);
    return new GeekhackClient(
        RestClient.builder()
            .requestFactory(requestFactory)
            .baseUrl(properties.getBaseUrl())
            .defaultHeader(HttpHeaders.USER_AGENT, properties.getUserAgent())
            .build());
  }
}

package com.keycheck.summary.config;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

@Configuration
@ConditionalOnProperty(name = "keycheck.summarizer", havingValue = "claude")
public class ClaudeConfig {
  /**
   * @param apiKey 환경변수나 backend/.env의 ANTHROPIC_API_KEY. 없으면 빈 문자열.
   */
  @Bean
  public AnthropicClient anthropicClient(
      @Value("${ANTHROPIC_API_KEY:}") String apiKey, ClaudeProperties properties) {
    return clientBuilder(apiKey).timeout(properties.getTimeout()).build();
  }

  /**
   * Spring 설정에 키가 있으면 그 키를 쓰고, 없으면 SDK 기본 인증 순서(환경변수, 로그인 프로필, Workload Identity
   * Federation)를 따른다.
   */
  public static AnthropicOkHttpClient.Builder clientBuilder(String apiKey) {
    AnthropicOkHttpClient.Builder builder = AnthropicOkHttpClient.builder();
    return usesConfiguredKey(apiKey) ? builder.apiKey(apiKey) : builder.fromEnv();
  }

  static boolean usesConfiguredKey(String apiKey) {
    return StringUtils.hasText(apiKey);
  }
}

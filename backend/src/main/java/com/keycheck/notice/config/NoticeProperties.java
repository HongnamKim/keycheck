package com.keycheck.notice.config;

import com.keycheck.post.domain.KoreanVendor;
import java.time.Duration;
import java.util.EnumMap;
import java.util.Map;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;

@Getter
@RequiredArgsConstructor
@ConfigurationProperties("keycheck.notice")
public class NoticeProperties {
  private final String botToken;
  private final String apiBaseUrl;
  private final String userAgent;

  /** 판매처별 디스코드 채널 id. 값이 비면 그 판매처는 수집하지 않는다 */
  private final Map<KoreanVendor, String> channels;

  /** 크로스포스트 첨부를 원본 메시지에 붙여 주는 대기 시간 */
  private final Duration attachWindow;

  private final Summary summary;

  @Getter
  @RequiredArgsConstructor
  public static class Summary {
    /** 공지를 요약하기 전에 기다리는 시간 */
    private final Duration wait;
  }

  /** 채널 id가 설정된 판매처만 */
  public Map<KoreanVendor, String> configuredChannels() {
    Map<KoreanVendor, String> result = new EnumMap<>(KoreanVendor.class);
    if (channels != null) {
      channels.forEach(
          (vendor, id) -> {
            if (StringUtils.hasText(id)) {
              result.put(vendor, id.trim());
            }
          });
    }
    return result;
  }
}

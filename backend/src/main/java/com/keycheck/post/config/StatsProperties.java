package com.keycheck.post.config;

import java.time.Duration;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@RequiredArgsConstructor
@ConfigurationProperties("keycheck.stats")
public class StatsProperties {
  /** 조회수 갱신이 한 시간(정각 기준) 동안 geekhack에 요청하는 상한. 다 쓰면 저장값을 준다 */
  private final int hourlyBudget;

  /** 마지막 댓글(없으면 작성일)이 이 기간보다 오래된 글은 inactiveInterval마다만 갱신한다 */
  private final Duration inactiveAfter;

  /** 오래된 글의 갱신 간격(그 밖의 글은 1시간) */
  private final Duration inactiveInterval;
}

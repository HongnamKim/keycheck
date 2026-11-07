package com.keycheck.crawl.config;

import java.time.Duration;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@RequiredArgsConstructor
@ConfigurationProperties("keycheck.fill")
public class FillProperties {
  /** 틀 글 채우기(상세 열기·미리 읽기)를 동시에 하는 상한. 넘치면 기다리지 않고 틀 그대로 둔다 */
  private final int maxConcurrent;

  /** 상세 열기에서 채우기에 실패한 글은 이 시간 동안 geekhack에 다시 요청하지 않는다(미리 읽기는 따로 실패 횟수를 센다) */
  private final Duration failureCooldown;
}

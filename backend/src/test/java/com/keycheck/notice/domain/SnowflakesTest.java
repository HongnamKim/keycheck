package com.keycheck.notice.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SnowflakesTest {
  @Test
  @DisplayName("원래 메시지 id로 작성 시각을 구한다")
  void 원래_메시지_id로_작성_시각을_구한다() {
    assertThat(Snowflakes.time(1568198861929971713L))
        .isEqualTo(Instant.parse("2026-11-06T09:42:24.410Z"));
  }
}

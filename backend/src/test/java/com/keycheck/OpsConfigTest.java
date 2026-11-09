package com.keycheck;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.keycheck.crawl.config.FillProperties;
import com.keycheck.post.config.StatsProperties;
import com.keycheck.summary.config.SummaryProperties;
import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class OpsConfigTest {
  @Autowired private MockMvc mockMvc;
  @Autowired private SummaryProperties summaryProperties;
  @Autowired private FillProperties fillProperties;
  @Autowired private StatsProperties statsProperties;

  @Test
  @DisplayName("상태 확인 주소는 UP만 알려 주고 세부 항목은 숨긴다")
  void healthShowsStatusOnly() throws Exception {
    mockMvc
        .perform(get("/actuator/health"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("UP"))
        .andExpect(jsonPath("$.components").doesNotExist());
  }

  @Test
  @DisplayName("상태 확인 말고 다른 actuator 주소는 열지 않는다")
  void otherActuatorEndpointsAreNotExposed() throws Exception {
    mockMvc.perform(get("/actuator/env")).andExpect(status().isNotFound());
  }

  @Test
  @DisplayName("비용·악용 상한은 application.yml의 기본값으로 묶인다(키가 빠지면 0이 되어 조용히 멈추지 않게)")
  void limitDefaultsAreBound() {
    assertThat(summaryProperties.getDailyLimit()).isEqualTo(2000);
    assertThat(summaryProperties.getConcurrency()).isEqualTo(2);
    assertThat(fillProperties.getMaxConcurrent()).isEqualTo(3);
    assertThat(fillProperties.getFailureCooldown()).isEqualTo(Duration.ofMinutes(10));
    assertThat(statsProperties.getHourlyBudget()).isEqualTo(60);
    assertThat(statsProperties.getInactiveAfter()).isEqualTo(Duration.ofDays(30));
    assertThat(statsProperties.getInactiveInterval()).isEqualTo(Duration.ofHours(24));
  }
}

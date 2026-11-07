package com.keycheck;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

// 운영 서버는 환경변수 SPRINGDOC_ENABLED=false 하나로 API 문서와 Swagger UI를 함께 끈다
@SpringBootTest(properties = "SPRINGDOC_ENABLED=false")
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class SwaggerDisabledTest {
  @Autowired private MockMvc mockMvc;

  @Test
  @DisplayName("SPRINGDOC_ENABLED=false면 API 문서를 내주지 않는다")
  void apiDocsAreHidden() throws Exception {
    mockMvc.perform(get("/v3/api-docs")).andExpect(status().isNotFound());
  }

  @Test
  @DisplayName("SPRINGDOC_ENABLED=false면 Swagger UI 페이지도 열리지 않는다")
  void swaggerUiIsHidden() throws Exception {
    mockMvc.perform(get("/swagger-ui/index.html")).andExpect(status().isNotFound());
  }
}

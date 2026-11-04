package com.keycheck;

import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
class OpenApiTest {
  @Autowired private MockMvc mockMvc;

  @Test
  @DisplayName("OpenAPI 문서에 글 API가 들어 있다")
  void openApiDocsContainPostApis() throws Exception {
    mockMvc
        .perform(get("/v3/api-docs"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.info.title").value("키체크 API"))
        .andExpect(jsonPath("$.paths['/api/posts'].get.summary").value("글 목록"))
        .andExpect(jsonPath("$.paths['/api/posts/{id}'].get.summary").value("글 상세"))
        .andExpect(jsonPath("$.paths['/api/posts'].get.parameters[0].schema.enum").isArray())
        .andExpect(jsonPath("$.paths['/api/posts'].get.parameters[1].name").value("sub"))
        .andExpect(jsonPath("$.paths['/api/posts'].get.parameters[1].schema.enum").isArray())
        .andExpect(jsonPath("$.paths['/api/posts'].get.parameters[4].name").value("board"))
        .andExpect(
            jsonPath("$.paths['/api/posts'].get.parameters[4].schema.enum")
                .value(contains("all", "ic", "gb")));
  }

  @Test
  @DisplayName("Swagger UI 페이지가 열린다")
  void swaggerUiOpens() throws Exception {
    mockMvc.perform(get("/swagger-ui/index.html")).andExpect(status().isOk());
  }
}

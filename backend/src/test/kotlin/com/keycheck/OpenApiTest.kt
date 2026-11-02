package com.keycheck

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Import
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration::class)
class OpenApiTest(@Autowired private val mockMvc: MockMvc) {

    @Test
    fun `OpenAPI 문서에 글 API가 들어 있다`() {
        mockMvc.get("/v3/api-docs").andExpect {
            status { isOk() }
            jsonPath("$.info.title") { value("키체크 API") }
            jsonPath("$.paths['/api/posts'].get.summary") { value("글 목록") }
            jsonPath("$.paths['/api/posts/{id}'].get.summary") { value("글 상세") }
            jsonPath("$.paths['/api/posts'].get.parameters[0].schema.enum") { isArray() }
        }
    }

    @Test
    fun `Swagger UI 페이지가 열린다`() {
        mockMvc.get("/swagger-ui/index.html").andExpect {
            status { isOk() }
        }
    }
}

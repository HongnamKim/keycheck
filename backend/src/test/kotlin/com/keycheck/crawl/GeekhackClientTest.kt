package com.keycheck.crawl

import org.junit.jupiter.api.Test
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.header
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import org.springframework.web.client.RestClient
import kotlin.test.assertEquals

class GeekhackClientTest {

    private val builder = RestClient.builder()
        .baseUrl("https://geekhack.test")
        .defaultHeader(HttpHeaders.USER_AGENT, "keycheck-test")
    private val server = MockRestServiceServer.bindTo(builder).build()
    private val client = GeekhackClient(builder.build())

    @Test
    fun `게시판 페이지를 ISO-8859-1로 디코딩해 받아온다`() {
        server.expect(requestTo("https://geekhack.test/index.php?board=132.50"))
            .andExpect(header(HttpHeaders.USER_AGENT, "keycheck-test"))
            .andRespond(withSuccess("Café".toByteArray(Charsets.ISO_8859_1), MediaType.TEXT_HTML))

        assertEquals("Café", client.fetchBoardPage(1))
        server.verify()
    }

    @Test
    fun `글 페이지 주소로 요청한다`() {
        server.expect(requestTo("https://geekhack.test/index.php?topic=123456.0"))
            .andRespond(withSuccess("<html></html>", MediaType.TEXT_HTML))

        assertEquals("<html></html>", client.fetchTopic(123456L))
        server.verify()
    }
}

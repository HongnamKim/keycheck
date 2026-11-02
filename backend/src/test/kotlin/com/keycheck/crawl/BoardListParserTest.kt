package com.keycheck.crawl

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class BoardListParserTest {

    private val parser = BoardListParser()
    private val fixture = checkNotNull(javaClass.getResource("/fixtures/board-list.html")).readText()

    @Test
    fun `고정글과 안내 행을 빼고 일반 글의 필드를 읽는다`() {
        assertEquals(
            listOf(
                BoardTopic(2000, "[IC] Foo & Bar — A \"Test\" Board", "alice", 12, 4347),
                BoardTopic(3000, "[IC] Multi Page Topic", "bob", 78, 82388),
            ),
            parser.parse(fixture),
        )
    }

    @Test
    fun `형식이 다른 행은 건너뛰고 나머지를 읽는다`() {
        val broken = fixture.replace("12 Replies", "알 수 없음")

        assertEquals(listOf(3000L), parser.parse(broken).map { it.topicId })
    }

    @Test
    fun `글 목록 표가 없는 페이지는 예외를 던진다`() {
        // 차단·오류 페이지를 "글 0건"으로 착각하지 않게 한다
        assertFailsWith<IllegalStateException> {
            parser.parse("<html><body>Too many requests</body></html>")
        }
    }
}

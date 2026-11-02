package com.keycheck.summary

import kotlin.test.Test
import kotlin.test.assertEquals

class FakeSummarizerTest {

    private val summarizer = FakeSummarizer()

    @Test
    fun `제목과 본문 앞 100자로 가짜 요약을 만든다`() {
        val result = summarizer.summarize(PostSource("[IC] Example", "가".repeat(150)))

        assertEquals(listOf("[가짜 요약] [IC] Example", "가".repeat(100)), result.lines)
        assertEquals(emptyList(), result.specs)
    }

    @Test
    fun `본문이 짧으면 본문 전체를 쓴다`() {
        val result = summarizer.summarize(PostSource("제목", "짧은 본문"))

        assertEquals("짧은 본문", result.lines[1])
    }
}

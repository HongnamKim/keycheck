package com.keycheck.summary

import com.keycheck.post.SpecItem

/** 요약 구현체는 설정 keycheck.summarizer로 고른다. 실제 AI 구현체는 나중에 추가한다. */
interface Summarizer {
    fun summarize(source: PostSource): SummaryResult
}

data class PostSource(val title: String, val bodyText: String)

/**
 * @property lines 한국어 요약 문장 목록
 * @property specs 스펙표 (레이아웃, 마운트, 소재 등)
 */
data class SummaryResult(val lines: List<String>, val specs: List<SpecItem>)

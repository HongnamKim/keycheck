package com.keycheck.summary

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component

/** 개발용 가짜 요약기. 번역하지 않고 제목과 본문 앞부분으로 고정 형식의 결과를 만든다. */
@Component
@ConditionalOnProperty(name = ["keycheck.summarizer"], havingValue = "fake", matchIfMissing = true)
class FakeSummarizer : Summarizer {

    override fun summarize(source: PostSource) = SummaryResult(
        lines = listOf("[가짜 요약] ${source.title}", source.bodyText.take(BODY_PREVIEW_LENGTH)),
        specs = emptyList(),
    )

    companion object {
        private const val BODY_PREVIEW_LENGTH = 100
    }
}

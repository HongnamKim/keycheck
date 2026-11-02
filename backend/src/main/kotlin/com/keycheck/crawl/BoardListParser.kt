package com.keycheck.crawl

import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

/** 게시판 목록 HTML 파서. 구조는 docs/geekhack-html-notes.md 2장 참고. */
@Component
class BoardListParser {

    private val log = LoggerFactory.getLogger(javaClass)

    /** @throws IllegalStateException 글 목록 표가 없는 페이지(차단·오류 페이지 등) */
    fun parse(html: String): List<BoardTopic> {
        val table = Jsoup.parse(html).selectFirst("#messageindex table.table_grid")
            ?: throw IllegalStateException("게시판 목록 표를 찾지 못했다")

        return table.select("tbody td.subject")
            .filterNot { it.className().contains("sticky") }
            .mapNotNull { parseRow(it.parent()!!) }
    }

    private fun parseRow(row: Element): BoardTopic? {
        val link = row.selectFirst("td.subject span[id^=msg_] > a")
        val topicId = link?.let { TOPIC_ID.find(it.attr("href")) }?.groupValues?.get(1)?.toLong()
        val author = row.selectFirst("td.subject p > a")?.text()
        val stats = row.selectFirst("td.stats")?.let { STATS.find(it.text()) }

        if (link == null || topicId == null || author == null || stats == null) {
            log.warn("게시판 행을 해석하지 못해 건너뜀: {}", row.text())
            return null
        }

        val (replies, views) = stats.destructured
        return BoardTopic(topicId, link.text(), author, replies.toInt(), views.toInt())
    }

    companion object {
        private val TOPIC_ID = Regex("""topic=(\d+)\.""")
        private val STATS = Regex("""(\d+)\s+Repl\w*\s+(\d+)\s+View\w*""")
    }
}

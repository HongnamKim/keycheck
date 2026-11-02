package com.keycheck.crawl

import org.springframework.web.client.RestClient

/**
 * geekhack HTML을 받아오는 클라이언트.
 * 간격 제한은 직접 쓰지 않는다. 호출자가 RequestGate를 통과한 뒤 부른다.
 */
class GeekhackClient(private val restClient: RestClient) {

    /** page는 0부터 */
    fun fetchBoardPage(page: Int): String = get("/index.php?board=$BOARD_ID.${page * TOPICS_PER_PAGE}")

    fun fetchTopic(topicId: Long): String = get("/index.php?topic=$topicId.0")

    private fun get(path: String): String {
        val bytes = restClient.get().uri(path).retrieve().body(ByteArray::class.java) ?: ByteArray(0)
        return String(bytes, RESPONSE_CHARSET)
    }

    companion object {
        const val BOARD_ID = 132
        const val TOPICS_PER_PAGE = 50

        /** 페이지는 ISO-8859-1로 선언하지만 실제 바이트는 Windows-1252다 (docs/geekhack-html-notes.md) */
        private val RESPONSE_CHARSET = charset("windows-1252")
    }
}

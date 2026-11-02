package com.keycheck.crawl

import org.springframework.web.client.RestClient

/**
 * geekhack HTML을 받아오는 클라이언트.
 * 간격 제한은 직접 쓰지 않는다. 호출자가 RequestGate를 통과한 뒤 부른다.
 */
class GeekhackClient(private val restClient: RestClient) {

    /** page는 0부터. 한 페이지의 글 수(TOPICS_PER_PAGE)는 Task 0에서 실제 사이트로 확인한다. */
    fun fetchBoardPage(page: Int): String = get("/index.php?board=$BOARD_ID.${page * TOPICS_PER_PAGE}")

    fun fetchTopic(topicId: Long): String = get("/index.php?topic=$topicId.0")

    private fun get(path: String): String {
        val bytes = restClient.get().uri(path).retrieve().body(ByteArray::class.java) ?: ByteArray(0)
        return String(bytes, Charsets.ISO_8859_1)
    }

    companion object {
        const val BOARD_ID = 132
        const val TOPICS_PER_PAGE = 50
    }
}

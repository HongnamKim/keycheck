package com.keycheck.crawl

/** 게시판 목록의 글 한 행 */
data class BoardTopic(
    val topicId: Long,
    val title: String,
    val author: String,
    val replies: Int,
    val views: Int,
)

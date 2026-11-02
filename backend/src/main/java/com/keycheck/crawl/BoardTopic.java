package com.keycheck.crawl;

/** 게시판 목록의 글 한 행 */
public record BoardTopic(long topicId, String title, String author, int replies, int views) {}

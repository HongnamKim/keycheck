package com.keycheck.post

import org.springframework.data.jpa.repository.JpaRepository

interface PostRepository : JpaRepository<Post, Long> {
    fun findByTopicId(topicId: Long): Post?
}

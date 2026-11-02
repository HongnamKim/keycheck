package com.keycheck.post

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository

interface PostRepository : JpaRepository<Post, Long> {
    fun findByTopicId(topicId: Long): Post?

    fun findByHiddenFalse(pageable: Pageable): Page<Post>

    fun findByHiddenFalseAndCategory(category: Category, pageable: Pageable): Page<Post>
}

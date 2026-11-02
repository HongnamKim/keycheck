package com.keycheck.post

import com.keycheck.image.ImageSource
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table

@Entity
@Table(name = "post_image")
class PostImage(
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "post_id")
    val post: Post,

    @Column(columnDefinition = "text")
    val url: String,

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    val source: ImageSource,

    val position: Int,

    /** 대표 이미지 후보에서 빠진 이유. 후보면 null. */
    @Column(length = 100)
    var excludedReason: String?,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null
        protected set
}

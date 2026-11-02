package com.keycheck.post

import com.keycheck.image.ImageSource
import jakarta.persistence.CascadeType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.OneToMany
import jakarta.persistence.OrderBy
import jakarta.persistence.Table
import org.hibernate.annotations.CreationTimestamp
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.annotations.UpdateTimestamp
import org.hibernate.type.SqlTypes
import java.time.Instant

@Entity
@Table(name = "post")
class Post(
    @Column(unique = true)
    val topicId: Long,

    @Column(length = 500)
    var title: String,

    @Column(length = 200)
    var author: String,

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    var category: Category,

    var postedAt: Instant?,

    var views: Int,

    var replies: Int,

    var statsCheckedAt: Instant,

    @Column(length = 500)
    val originalUrl: String,

    /** 요약용 원문 텍스트. API로 노출하지 않는다. */
    @Column(columnDefinition = "text")
    val bodyText: String,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null
        protected set

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    var summaryStatus: SummaryStatus = SummaryStatus.PENDING

    @JdbcTypeCode(SqlTypes.JSON)
    var summaryLines: List<String>? = null

    @JdbcTypeCode(SqlTypes.JSON)
    var specs: List<SpecItem>? = null

    /** 대표 이미지(post_image.id). 순환 참조를 피하려고 연관관계 대신 ID만 둔다. */
    var representativeImageId: Long? = null

    var hidden: Boolean = false

    @OneToMany(mappedBy = "post", cascade = [CascadeType.ALL], orphanRemoval = true)
    @OrderBy("position")
    val images: MutableList<PostImage> = mutableListOf()

    @CreationTimestamp
    var createdAt: Instant? = null
        protected set

    @UpdateTimestamp
    var updatedAt: Instant? = null
        protected set

    fun addImage(url: String, source: ImageSource, position: Int, excludedReason: String? = null): PostImage {
        val image = PostImage(this, url, source, position, excludedReason)
        images.add(image)
        return image
    }
}

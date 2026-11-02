package com.keycheck.post.dto

import com.keycheck.post.Category
import com.keycheck.post.SpecItem
import com.keycheck.post.SummaryStatus
import java.time.Instant

data class PostCardResponse(
    val id: Long,
    val title: String,
    val category: Category,
    val representativeImageUrl: String?,
    val summaryStatus: SummaryStatus,
    /** summaryStatus가 DONE일 때만 값이 있다 */
    val summaryFirstLine: String?,
    val views: Int,
    val replies: Int,
    val statsCheckedAt: Instant,
    val postedAt: Instant?,
)

data class PostPageResponse(
    val items: List<PostCardResponse>,
    val page: Int,
    val totalPages: Int,
)

data class PostDetailResponse(
    val id: Long,
    val title: String,
    val author: String,
    val category: Category,
    val representativeImageUrl: String?,
    val summaryStatus: SummaryStatus,
    /** summaryStatus가 DONE이 아니면 빈 목록 */
    val summaryLines: List<String>,
    val specs: List<SpecItem>,
    val originalUrl: String,
    val views: Int,
    val replies: Int,
    val statsCheckedAt: Instant,
    val postedAt: Instant?,
)

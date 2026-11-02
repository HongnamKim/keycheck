package com.keycheck.post

import com.keycheck.post.dto.PostCardResponse
import com.keycheck.post.dto.PostDetailResponse
import com.keycheck.post.dto.PostPageResponse
import org.springframework.data.domain.PageRequest
import org.springframework.data.repository.findByIdOrNull
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException

@Service
@Transactional(readOnly = true)
class PostQueryService(
    private val postRepository: PostRepository,
    private val postImageRepository: PostImageRepository,
) {

    fun getPosts(filter: CategoryFilter, sort: PostSort, page: Int): PostPageResponse {
        val pageable = PageRequest.of(page, PAGE_SIZE, sort.sort)
        val posts = filter.category
            ?.let { postRepository.findByHiddenFalseAndCategory(it, pageable) }
            ?: postRepository.findByHiddenFalse(pageable)

        val imageUrls = representativeImageUrls(posts.content)
        return PostPageResponse(
            items = posts.content.map { it.toCard(imageUrls[it.representativeImageId]) },
            page = page,
            totalPages = posts.totalPages,
        )
    }

    fun getPost(id: Long): PostDetailResponse {
        val post = postRepository.findByIdOrNull(id)?.takeIf { !it.hidden }
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND)
        return post.toDetail(representativeImageUrls(listOf(post))[post.representativeImageId])
    }

    private fun representativeImageUrls(posts: List<Post>): Map<Long, String> {
        val ids = posts.mapNotNull { it.representativeImageId }
        if (ids.isEmpty()) return emptyMap()
        return postImageRepository.findAllById(ids).associate { it.id!! to it.url }
    }

    private fun Post.toCard(imageUrl: String?) = PostCardResponse(
        id = id!!,
        title = title,
        category = category,
        representativeImageUrl = imageUrl,
        summaryStatus = summaryStatus,
        summaryFirstLine = summaryLines?.firstOrNull()?.takeIf { summaryStatus == SummaryStatus.DONE },
        views = views,
        replies = replies,
        statsCheckedAt = statsCheckedAt,
        postedAt = postedAt,
    )

    private fun Post.toDetail(imageUrl: String?) = PostDetailResponse(
        id = id!!,
        title = title,
        author = author,
        category = category,
        representativeImageUrl = imageUrl,
        summaryStatus = summaryStatus,
        summaryLines = if (summaryStatus == SummaryStatus.DONE) summaryLines.orEmpty() else emptyList(),
        specs = specs.orEmpty(),
        originalUrl = originalUrl,
        views = views,
        replies = replies,
        statsCheckedAt = statsCheckedAt,
        postedAt = postedAt,
    )

    companion object {
        const val PAGE_SIZE = 20
    }
}

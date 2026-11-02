package com.keycheck.post

import com.keycheck.post.dto.PostDetailResponse
import com.keycheck.post.dto.PostPageResponse
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException

@RestController
@RequestMapping("/api/posts")
class PostController(private val postQueryService: PostQueryService) {

    @GetMapping
    fun getPosts(
        @RequestParam(defaultValue = "all") category: String,
        @RequestParam(defaultValue = "recent") sort: String,
        @RequestParam(defaultValue = "0") page: Int,
    ): PostPageResponse {
        if (page < 0) throw ResponseStatusException(HttpStatus.BAD_REQUEST, "page는 0 이상")
        return postQueryService.getPosts(CategoryFilter.from(category), PostSort.from(sort), page)
    }

    @GetMapping("/{id}")
    fun getPost(@PathVariable id: Long): PostDetailResponse = postQueryService.getPost(id)
}

package com.keycheck.post

import com.keycheck.TestcontainersConfiguration
import com.keycheck.image.ImageSource
import org.hamcrest.Matchers.contains
import org.hamcrest.Matchers.hasSize
import org.hamcrest.Matchers.nullValue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Import
import org.springframework.http.HttpHeaders
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import java.time.Instant

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration::class)
class PostControllerTest(
    @Autowired private val mockMvc: MockMvc,
    @Autowired private val postRepository: PostRepository,
) {

    @BeforeEach
    fun setUp() {
        postRepository.deleteAll()
    }

    private fun save(
        topicId: Long,
        category: Category = Category.KEYBOARD,
        postedAt: Instant? = Instant.parse("2026-10-01T00:00:00Z"),
        views: Int = 100,
        hidden: Boolean = false,
        summaryStatus: SummaryStatus = SummaryStatus.PENDING,
        withImage: Boolean = false,
    ): Post {
        val post = Post(
            topicId = topicId,
            title = "글 $topicId",
            author = "maker",
            category = category,
            postedAt = postedAt,
            views = views,
            replies = 5,
            statsCheckedAt = Instant.parse("2026-11-01T00:00:00Z"),
            originalUrl = "https://geekhack.org/index.php?topic=$topicId.0",
            bodyText = "비공개 본문",
        )
        post.hidden = hidden
        post.summaryStatus = summaryStatus
        if (summaryStatus == SummaryStatus.DONE) {
            post.summaryLines = listOf("첫 줄", "둘째 줄")
            post.specs = listOf(SpecItem("레이아웃", "65%"))
        }
        if (withImage) post.addImage("https://i.imgur.com/$topicId.jpg", ImageSource.EXTERNAL, 0)
        postRepository.saveAndFlush(post)
        if (withImage) {
            post.representativeImageId = post.images[0].id
            postRepository.saveAndFlush(post)
        }
        return post
    }

    @Test
    fun `기본 목록은 숨김을 빼고 최근 작성순, 작성일 없는 글은 마지막이다`() {
        save(1, postedAt = Instant.parse("2026-10-01T00:00:00Z"))
        save(2, postedAt = Instant.parse("2026-10-03T00:00:00Z"))
        save(3, postedAt = null)
        save(4, postedAt = Instant.parse("2026-10-05T00:00:00Z"), hidden = true)

        mockMvc.get("/api/posts").andExpect {
            status { isOk() }
            jsonPath("$.items[*].title") { value(contains("글 2", "글 1", "글 3")) }
            jsonPath("$.page") { value(0) }
            jsonPath("$.totalPages") { value(1) }
        }
    }

    @Test
    fun `조회수순으로 정렬한다`() {
        save(1, views = 10)
        save(2, views = 300)
        save(3, views = 50)

        mockMvc.get("/api/posts?sort=views").andExpect {
            jsonPath("$.items[*].title") { value(contains("글 2", "글 3", "글 1")) }
        }
    }

    @Test
    fun `분류로 거른다`() {
        save(1, category = Category.KEYBOARD)
        save(2, category = Category.KEYCAP)
        save(3, category = Category.OTHER)

        mockMvc.get("/api/posts?category=keycap").andExpect {
            jsonPath("$.items[*].title") { value(contains("글 2")) }
        }
        mockMvc.get("/api/posts?category=all").andExpect {
            jsonPath("$.items", hasSize<Any>(3))
        }
    }

    @Test
    fun `카드에 대표 이미지와 요약 첫 줄을 담는다, 요약 미완료면 첫 줄은 null`() {
        save(1, summaryStatus = SummaryStatus.DONE, withImage = true, postedAt = Instant.parse("2026-10-02T00:00:00Z"))
        save(2, summaryStatus = SummaryStatus.PENDING)

        mockMvc.get("/api/posts").andExpect {
            jsonPath("$.items[0].representativeImageUrl") { value("https://i.imgur.com/1.jpg") }
            jsonPath("$.items[0].summaryFirstLine") { value("첫 줄") }
            jsonPath("$.items[0].statsCheckedAt") { value("2026-11-01T00:00:00Z") }
            jsonPath("$.items[1].representativeImageUrl") { value(nullValue()) }
            jsonPath("$.items[1].summaryFirstLine") { value(nullValue()) }
            jsonPath("$.items[1].summaryStatus") { value("PENDING") }
        }
    }

    @Test
    fun `한 페이지는 20건이다`() {
        (1L..21L).forEach { save(it) }

        mockMvc.get("/api/posts?page=1").andExpect {
            jsonPath("$.items", hasSize<Any>(1))
            jsonPath("$.page") { value(1) }
            jsonPath("$.totalPages") { value(2) }
        }
    }

    @Test
    fun `잘못된 분류·정렬·페이지는 400`() {
        mockMvc.get("/api/posts?category=switch").andExpect { status { isBadRequest() } }
        mockMvc.get("/api/posts?sort=oldest").andExpect { status { isBadRequest() } }
        mockMvc.get("/api/posts?page=-1").andExpect { status { isBadRequest() } }
    }

    @Test
    fun `상세는 요약·스펙·원문 링크를 주고 원문 본문은 주지 않는다`() {
        val post = save(1, summaryStatus = SummaryStatus.DONE, withImage = true)

        mockMvc.get("/api/posts/${post.id}").andExpect {
            status { isOk() }
            jsonPath("$.summaryLines") { value(contains("첫 줄", "둘째 줄")) }
            jsonPath("$.specs[0].label") { value("레이아웃") }
            jsonPath("$.originalUrl") { value("https://geekhack.org/index.php?topic=1.0") }
            jsonPath("$.representativeImageUrl") { value("https://i.imgur.com/1.jpg") }
            jsonPath("$.bodyText") { doesNotExist() }
        }
    }

    @Test
    fun `숨김 글과 없는 글의 상세는 404`() {
        val hidden = save(1, hidden = true)

        mockMvc.get("/api/posts/${hidden.id}").andExpect { status { isNotFound() } }
        mockMvc.get("/api/posts/999999").andExpect { status { isNotFound() } }
    }

    @Test
    fun `로컬 프론트 주소의 CORS 요청을 허용한다`() {
        mockMvc.get("/api/posts") {
            header(HttpHeaders.ORIGIN, "http://localhost:3000")
        }.andExpect {
            header { string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:3000") }
        }
    }
}

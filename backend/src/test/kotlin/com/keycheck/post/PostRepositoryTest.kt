package com.keycheck.post

import com.keycheck.TestcontainersConfiguration
import com.keycheck.image.ImageSource
import jakarta.persistence.EntityManager
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.context.annotation.Import
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

// ddl-auto=validate: Flyway로 만든 스키마와 엔티티 매핑이 일치하는지도 함께 검증한다.
@DataJpaTest(properties = ["spring.jpa.hibernate.ddl-auto=validate"])
@Import(TestcontainersConfiguration::class)
class PostRepositoryTest(
    @Autowired private val postRepository: PostRepository,
    @Autowired private val em: EntityManager,
) {

    private fun newPost(topicId: Long = 123456L) = Post(
        topicId = topicId,
        title = "[IC] Example 65% Keyboard",
        author = "maker",
        category = Category.KEYBOARD,
        postedAt = Instant.parse("2026-10-01T12:00:00Z"),
        views = 1234,
        replies = 56,
        statsCheckedAt = Instant.parse("2026-11-01T00:00:00Z"),
        originalUrl = "https://geekhack.org/index.php?topic=123456.0",
        bodyText = "본문 텍스트",
    )

    @Test
    fun `글과 이미지, jsonb 필드를 저장하고 다시 읽는다`() {
        val post = newPost()
        post.summaryStatus = SummaryStatus.DONE
        post.summaryLines = listOf("첫 줄 요약", "둘째 줄 요약")
        post.specs = listOf(SpecItem("레이아웃", "65%"), SpecItem("마운트", "가스켓"))
        post.addImage("https://example.com/banner.png", ImageSource.EXTERNAL, 0, "banner")
        val render = post.addImage("https://geekhack.org/index.php?action=dlattach;topic=123456.0;attach=1", ImageSource.ATTACHMENT, 1)
        postRepository.saveAndFlush(post)

        post.representativeImageId = render.id
        postRepository.saveAndFlush(post)
        em.clear()

        val found = postRepository.findById(post.id!!).orElseThrow()
        assertEquals(Category.KEYBOARD, found.category)
        assertEquals(SummaryStatus.DONE, found.summaryStatus)
        assertEquals(listOf("첫 줄 요약", "둘째 줄 요약"), found.summaryLines)
        assertEquals(listOf(SpecItem("레이아웃", "65%"), SpecItem("마운트", "가스켓")), found.specs)
        assertEquals(listOf(0, 1), found.images.map { it.position })
        assertEquals("banner", found.images[0].excludedReason)
        assertEquals(ImageSource.ATTACHMENT, found.images[1].source)
        assertEquals(render.id, found.representativeImageId)
        assertNotNull(found.createdAt)
        assertNotNull(found.updatedAt)
    }

    @Test
    fun `topicId로 글을 찾는다`() {
        postRepository.saveAndFlush(newPost(topicId = 777L))
        em.clear()

        assertEquals("maker", postRepository.findByTopicId(777L)?.author)
        assertNull(postRepository.findByTopicId(888L))
    }
}

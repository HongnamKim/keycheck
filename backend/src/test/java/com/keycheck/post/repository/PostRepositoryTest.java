package com.keycheck.post.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.keycheck.TestcontainersConfiguration;
import com.keycheck.image.domain.ImageSource;
import com.keycheck.post.domain.Category;
import com.keycheck.post.domain.Post;
import com.keycheck.post.domain.PostImage;
import com.keycheck.post.domain.SpecItem;
import com.keycheck.post.domain.SummaryStatus;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;

// ddl-auto=validate: Flyway로 만든 스키마와 엔티티 매핑이 일치하는지도 함께 검증한다.
@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=validate")
@Import(TestcontainersConfiguration.class)
class PostRepositoryTest {
  @Autowired private PostRepository postRepository;
  @Autowired private EntityManager em;

  private Post newPost(long topicId) {
    return Post.create(
        topicId,
        "[IC] Example 65% Keyboard",
        "maker",
        Category.KEYBOARD,
        Instant.parse("2026-10-01T12:00:00Z"),
        1234,
        56,
        Instant.parse("2026-11-01T00:00:00Z"),
        "https://geekhack.org/index.php?topic=" + topicId + ".0",
        "본문 텍스트");
  }

  @Test
  @DisplayName("글과 이미지, jsonb 필드를 저장하고 다시 읽는다")
  void savesAndReadsPostWithImagesAndJson() {
    Post post = newPost(123456L);
    post.setSummaryStatus(SummaryStatus.DONE);
    post.setSummaryLines(List.of("첫 줄 요약", "둘째 줄 요약"));
    post.setSpecs(List.of(new SpecItem("레이아웃", "65%"), new SpecItem("마운트", "가스켓")));
    post.addImage("https://example.com/banner.png", ImageSource.EXTERNAL, 0, "banner");
    PostImage render =
        post.addImage(
            "https://geekhack.org/index.php?action=dlattach;topic=123456.0;attach=1",
            ImageSource.ATTACHMENT,
            1,
            null);
    postRepository.saveAndFlush(post);

    post.setRepresentativeImageId(render.getId());
    postRepository.saveAndFlush(post);
    em.clear();

    Post found = postRepository.findById(post.getId()).orElseThrow();
    assertThat(found.getCategory()).isEqualTo(Category.KEYBOARD);
    assertThat(found.getSummaryStatus()).isEqualTo(SummaryStatus.DONE);
    assertThat(found.getSummaryLines()).containsExactly("첫 줄 요약", "둘째 줄 요약");
    assertThat(found.getSpecs())
        .containsExactly(new SpecItem("레이아웃", "65%"), new SpecItem("마운트", "가스켓"));
    assertThat(found.getImages()).extracting(PostImage::getPosition).containsExactly(0, 1);
    assertThat(found.getImages().get(0).getExcludedReason()).isEqualTo("banner");
    assertThat(found.getImages().get(1).getSource()).isEqualTo(ImageSource.ATTACHMENT);
    assertThat(found.getRepresentativeImageId()).isEqualTo(render.getId());
    assertThat(found.getCreatedAt()).isNotNull();
    assertThat(found.getUpdatedAt()).isNotNull();
  }

  @Test
  @DisplayName("topicId로 글을 찾는다")
  void findsByTopicId() {
    postRepository.saveAndFlush(newPost(777L));
    em.clear();

    assertThat(postRepository.findByTopicId(777L))
        .hasValueSatisfying(post -> assertThat(post.getAuthor()).isEqualTo("maker"));
    assertThat(postRepository.findByTopicId(888L)).isEmpty();
  }
}

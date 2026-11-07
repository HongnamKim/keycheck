package com.keycheck.post.controller;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.keycheck.TestcontainersConfiguration;
import com.keycheck.crawl.client.GeekhackClient;
import com.keycheck.crawl.client.RequestGate;
import com.keycheck.image.domain.ImageSource;
import com.keycheck.post.domain.Board;
import com.keycheck.post.domain.Category;
import com.keycheck.post.domain.Post;
import com.keycheck.post.repository.PostRepository;
import java.time.Instant;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** 축소본이 있으면 카드·상세·하단 목록이 테스트 공개 주소(https://img.test)를 붙인 축소본 주소를 준다 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class PostThumbApiTest {
  @Autowired private MockMvc mockMvc;
  @Autowired private PostRepository postRepository;
  @Autowired private JdbcTemplate jdbcTemplate;

  // 실제 geekhack을 호출하지 않는다
  @MockitoBean private GeekhackClient client;
  @MockitoBean private RequestGate gate;

  /** 대표 이미지에 640·1280 축소본이 있고, 본문 이미지는 아직 없다 */
  private Post withThumbs;

  /** 대표 이미지가 있지만 축소본이 아직 없다 */
  private Post withoutThumbs;

  /** 모든 글이 커버리지 안에 있고 끝까지 받은 상태 */
  @BeforeEach
  void setUp() {
    postRepository.deleteAll();
    jdbcTemplate.update(
        "update board_backfill set next_page = 1, floor_topic_id = 0, exhausted = true");
    withoutThumbs =
        save(
            1,
            post -> post.addImage("https://i.imgur.com/plain.jpg", ImageSource.EXTERNAL, 0, null));
    withThumbs =
        save(
            2,
            post -> {
              post.addImage("https://i.imgur.com/rep.jpg", ImageSource.EXTERNAL, 0, null);
              post.addImage("https://i.imgur.com/body.jpg", ImageSource.EXTERNAL, 1, null);
            });
    jdbcTemplate.update(
        "update post_image set thumb_key = 'thumbs/5.webp', thumb_large_key = 'thumbs/5-1280.webp'"
            + " where id = ?",
        withThumbs.getRepresentativeImageId());
  }

  /** 기본값으로 글을 만들고 customize로 이미지를 더한 뒤 저장한다. 첫 이미지를 대표로 정한다. */
  private Post save(long topicId, Consumer<Post> customize) {
    Post post =
        Post.create(
            Board.IC,
            topicId,
            "글 " + topicId,
            "maker",
            Category.KEYBOARD,
            Instant.parse("2026-10-01T00:00:00Z"),
            100,
            5,
            Instant.parse("2026-11-01T00:00:00Z"),
            "https://geekhack.org/index.php?topic=" + topicId + ".0",
            "본문");
    customize.accept(post);
    postRepository.saveAndFlush(post);
    post.setRepresentativeImageId(post.getImages().get(0).getId());
    return postRepository.saveAndFlush(post);
  }

  @Test
  @DisplayName("목록 카드는 대표 이미지 원본 주소와 함께 축소본 주소를 주고, 축소본이 없으면 null")
  void cardHasRepresentativeThumbUrl() throws Exception {
    mockMvc
        .perform(get("/api/posts"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items[*].title", contains("글 2", "글 1")))
        .andExpect(jsonPath("$.items[0].representativeImageUrl").value("https://i.imgur.com/rep.jpg"))
        .andExpect(
            jsonPath("$.items[0].representativeThumbUrl").value("https://img.test/thumbs/5.webp"))
        .andExpect(
            jsonPath("$.items[1].representativeImageUrl").value("https://i.imgur.com/plain.jpg"))
        .andExpect(jsonPath("$.items[1].representativeThumbUrl").value(nullValue()));
  }

  @Test
  @DisplayName("목록·하단 목록 카드는 대표 이미지 1280 축소본 주소를 주고, 없으면 null")
  void cardHasLargeThumb() throws Exception {
    mockMvc
        .perform(get("/api/posts"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items[*].title", contains("글 2", "글 1")))
        .andExpect(
            jsonPath("$.items[0].representativeLargeUrl")
                .value("https://img.test/thumbs/5-1280.webp"))
        .andExpect(jsonPath("$.items[1].representativeLargeUrl").value(nullValue()));
    mockMvc
        .perform(get("/api/posts/" + withoutThumbs.getId() + "/neighbors?board=ic&size=5"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.before[*].title", contains("글 2")))
        .andExpect(
            jsonPath("$.before[0].representativeLargeUrl")
                .value("https://img.test/thumbs/5-1280.webp"));
    mockMvc
        .perform(get("/api/posts/" + withThumbs.getId() + "/neighbors?board=ic&size=5"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.after[*].title", contains("글 1")))
        .andExpect(jsonPath("$.after[0].representativeLargeUrl").value(nullValue()));
  }

  @Test
  @DisplayName("상세는 대표 이미지의 640·1280 축소본 주소와, 갤러리 이미지마다 원본·축소본 주소를 준다")
  void detailHasThumbUrls() throws Exception {
    mockMvc
        .perform(get("/api/posts/" + withThumbs.getId()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.representativeImageUrl").value("https://i.imgur.com/rep.jpg"))
        .andExpect(jsonPath("$.representativeThumbUrl").value("https://img.test/thumbs/5.webp"))
        .andExpect(
            jsonPath("$.representativeLargeUrl").value("https://img.test/thumbs/5-1280.webp"))
        .andExpect(jsonPath("$.images", hasSize(2)))
        .andExpect(jsonPath("$.images[0].url").value("https://i.imgur.com/rep.jpg"))
        .andExpect(jsonPath("$.images[0].thumbUrl").value("https://img.test/thumbs/5.webp"))
        .andExpect(jsonPath("$.images[1].url").value("https://i.imgur.com/body.jpg"))
        .andExpect(jsonPath("$.images[1].thumbUrl").value(nullValue()))
        .andExpect(jsonPath("$.imageUrls").doesNotExist());
  }

  @Test
  @DisplayName("대표 이미지의 축소본이 아직 없으면 상세의 축소본 주소는 null")
  void detailWithoutThumbsHasNullThumbUrls() throws Exception {
    mockMvc
        .perform(get("/api/posts/" + withoutThumbs.getId()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.representativeImageUrl").value("https://i.imgur.com/plain.jpg"))
        .andExpect(jsonPath("$.representativeThumbUrl").value(nullValue()))
        .andExpect(jsonPath("$.representativeLargeUrl").value(nullValue()))
        .andExpect(jsonPath("$.images[0].url").value("https://i.imgur.com/plain.jpg"))
        .andExpect(jsonPath("$.images[0].thumbUrl").value(nullValue()));
  }

  @Test
  @DisplayName("하단 목록 카드도 대표 이미지 축소본 주소를 준다")
  void neighborCardHasRepresentativeThumbUrl() throws Exception {
    mockMvc
        .perform(get("/api/posts/" + withoutThumbs.getId() + "/neighbors?board=ic&size=5"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.before[*].title", contains("글 2")))
        .andExpect(
            jsonPath("$.before[0].representativeThumbUrl").value("https://img.test/thumbs/5.webp"));
  }
}

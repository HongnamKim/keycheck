package com.keycheck.post.controller;

import static org.hamcrest.Matchers.contains;
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

/** 카드·상세의 상품 이름(productName). 이름은 요약기가 뽑으므로 테스트는 DB에 직접 넣는다. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class PostProductNameApiTest {
  @Autowired private MockMvc mockMvc;
  @Autowired private PostRepository postRepository;
  @Autowired private JdbcTemplate jdbcTemplate;

  // 실제 geekhack을 호출하지 않는다
  @MockitoBean private GeekhackClient client;
  @MockitoBean private RequestGate gate;

  /** 모든 글이 커버리지 안에 있고 끝까지 받은 상태 */
  @BeforeEach
  void setUp() {
    postRepository.deleteAll();
    jdbcTemplate.update(
        "update board_backfill set next_page = 1, floor_topic_id = 0, exhausted = true");
  }

  /** 본문을 읽은 공동구매 글(저장 전) */
  private static Post newPost(long topicId) {
    return Post.create(
        Board.GB,
        topicId,
        "[GB] 글 " + topicId + " | Oct 30th -> Nov 13th",
        "maker",
        Category.KEYCAP,
        Instant.parse("2026-10-01T00:00:00Z"),
        100,
        5,
        Instant.parse("2026-11-01T00:00:00Z"),
        "https://geekhack.org/index.php?topic=" + topicId + ".0",
        "본문");
  }

  private Post save(long topicId) {
    return postRepository.saveAndFlush(newPost(topicId));
  }

  private void setProductName(Post post, String productName) {
    jdbcTemplate.update(
        "update post set product_name = ? where id = ?", productName, post.getId());
  }

  @Test
  @DisplayName("목록 카드는 상품 이름을 주고 제목은 원래대로 두며, 이름이 없으면 null")
  void cardHasProductName() throws Exception {
    save(1);
    setProductName(save(2), "SWG Nightshift");

    mockMvc
        .perform(get("/api/posts?board=gb"))
        .andExpect(status().isOk())
        .andExpect(
            jsonPath(
                "$.items[*].title",
                contains("[GB] 글 2 | Oct 30th -> Nov 13th", "[GB] 글 1 | Oct 30th -> Nov 13th")))
        .andExpect(jsonPath("$.items[0].productName").value("SWG Nightshift"))
        .andExpect(jsonPath("$.items[1].productName").value(nullValue()));
  }

  @Test
  @DisplayName("상세는 상품 이름을 주고 제목은 원래대로 두며, 이름이 없으면 null")
  void detailHasProductName() throws Exception {
    Post named = save(1);
    setProductName(named, "SWG Nightshift");
    Post unnamed = save(2);

    mockMvc
        .perform(get("/api/posts/" + named.getId()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.title").value("[GB] 글 1 | Oct 30th -> Nov 13th"))
        .andExpect(jsonPath("$.productName").value("SWG Nightshift"));
    mockMvc
        .perform(get("/api/posts/" + unnamed.getId()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.productName").value(nullValue()));
  }

  @Test
  @DisplayName("하이라이트 칸의 카드도 상품 이름을 준다")
  void highlightCardHasProductName() throws Exception {
    // 하이라이트 후보가 되도록 대표 이미지를 정한다
    Post post = newPost(1);
    post.addImage("https://i.imgur.com/1.jpg", ImageSource.EXTERNAL, 0, null);
    postRepository.saveAndFlush(post);
    post.setRepresentativeImageId(post.getImages().get(0).getId());
    postRepository.saveAndFlush(post);
    setProductName(post, "SWG Nightshift");

    mockMvc
        .perform(get("/api/posts/highlights"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.slots[0].post.id").value(post.getId()))
        .andExpect(jsonPath("$.slots[0].post.productName").value("SWG Nightshift"));
  }
}

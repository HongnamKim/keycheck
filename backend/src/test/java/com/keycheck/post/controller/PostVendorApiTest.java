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

/** 카드의 한국 벤더 여부(hasKoreanVendor)와 상세의 벤더 목록(vendors). 벤더는 요약기가 뽑으므로 테스트는 DB에 직접 넣는다. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class PostVendorApiTest {
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
        "[GB] 글 " + topicId,
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

  private void setVendors(Post post, String vendorsJson) {
    jdbcTemplate.update(
        "update post set vendors = ?::jsonb where id = ?", vendorsJson, post.getId());
  }

  @Test
  @DisplayName("목록 카드는 지역이 KR인 벤더가 있으면 hasKoreanVendor가 true, KR이 없거나 벤더가 없으면 false")
  void cardHasKoreanVendor() throws Exception {
    Post korean = save(1);
    setVendors(
        korean,
        "[{\"name\": \"NovelKeys\", \"region\": \"US\", \"url\": null},"
            + " {\"name\": \"Swagkeys\", \"region\": \"KR\", \"url\": null}]");
    Post usOnly = save(2);
    setVendors(usOnly, "[{\"name\": \"NovelKeys\", \"region\": \"US\", \"url\": null}]");
    // 아직 뽑지 않은 글(null)
    Post notExtracted = save(3);
    // 가짜 요약(빈 목록)
    Post empty = save(4);
    setVendors(empty, "[]");

    mockMvc
        .perform(get("/api/posts?board=gb"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items", hasSize(4)))
        .andExpect(jsonPath("$.items[0].id").value(empty.getId()))
        .andExpect(jsonPath("$.items[0].hasKoreanVendor").value(false))
        .andExpect(jsonPath("$.items[1].id").value(notExtracted.getId()))
        .andExpect(jsonPath("$.items[1].hasKoreanVendor").value(false))
        .andExpect(jsonPath("$.items[2].id").value(usOnly.getId()))
        .andExpect(jsonPath("$.items[2].hasKoreanVendor").value(false))
        .andExpect(jsonPath("$.items[3].id").value(korean.getId()))
        .andExpect(jsonPath("$.items[3].hasKoreanVendor").value(true));
  }

  @Test
  @DisplayName("상세 vendors는 한국 벤더를 앞에 두고, 한국 벤더끼리와 나머지끼리는 저장 순서를 지킨다")
  void detailVendorsKoreanFirst() throws Exception {
    Post post = save(1);
    // 각 무리의 저장 순서가 이름순·지역순(오름·내림) 어느 것과도 다르게 넣는다
    setVendors(
        post,
        "[{\"name\": \"Z\", \"region\": \"EU\", \"url\": \"https://z.example/p\"},"
            + " {\"name\": \"Y\", \"region\": \"KR\", \"url\": \"https://y.example/p\"},"
            + " {\"name\": \"A\", \"region\": \"US\", \"url\": null},"
            + " {\"name\": \"M\", \"region\": \"CN\", \"url\": null},"
            + " {\"name\": \"B\", \"region\": \"KR\", \"url\": null}]");

    mockMvc
        .perform(get("/api/posts/" + post.getId()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.vendors[*].name", contains("Y", "B", "Z", "A", "M")))
        .andExpect(jsonPath("$.vendors[*].region", contains("KR", "KR", "EU", "US", "CN")))
        .andExpect(jsonPath("$.vendors[0].url").value("https://y.example/p"))
        .andExpect(jsonPath("$.vendors[1].url").value(nullValue()))
        .andExpect(jsonPath("$.vendors[2].url").value("https://z.example/p"))
        .andExpect(jsonPath("$.vendors[3].url").value(nullValue()))
        .andExpect(jsonPath("$.vendors[4].url").value(nullValue()))
        // 본문 링크는 내보내지 않는다
        .andExpect(jsonPath("$.bodyLinks").doesNotExist());
  }

  @Test
  @DisplayName("벤더를 아직 뽑지 않은 글(null)의 상세 vendors는 빈 배열")
  void detailVendorsEmptyWhenNotExtracted() throws Exception {
    Post post = save(1);

    mockMvc
        .perform(get("/api/posts/" + post.getId()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.vendors", hasSize(0)));
  }

  @Test
  @DisplayName("하이라이트 칸의 카드도 hasKoreanVendor를 준다")
  void highlightCardHasKoreanVendor() throws Exception {
    // 하이라이트 후보가 되도록 대표 이미지를 정한다
    Post post = newPost(1);
    post.addImage("https://i.imgur.com/1.jpg", ImageSource.EXTERNAL, 0, null);
    postRepository.saveAndFlush(post);
    post.setRepresentativeImageId(post.getImages().get(0).getId());
    postRepository.saveAndFlush(post);
    setVendors(post, "[{\"name\": \"Swagkeys\", \"region\": \"KR\", \"url\": null}]");

    mockMvc
        .perform(get("/api/posts/highlights"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.slots[0].post.id").value(post.getId()))
        .andExpect(jsonPath("$.slots[0].post.hasKoreanVendor").value(true));
  }

  @Test
  @DisplayName("상세 아래 목록(neighbors)의 카드도 hasKoreanVendor를 준다")
  void neighborCardHasKoreanVendor() throws Exception {
    Post below = save(1);
    setVendors(below, "[{\"name\": \"NovelKeys\", \"region\": \"US\", \"url\": null}]");
    Post current = save(2);
    Post above = save(3);
    setVendors(above, "[{\"name\": \"Swagkeys\", \"region\": \"KR\", \"url\": null}]");

    mockMvc
        .perform(get("/api/posts/" + current.getId() + "/neighbors?board=gb"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.before[0].id").value(above.getId()))
        .andExpect(jsonPath("$.before[0].hasKoreanVendor").value(true))
        .andExpect(jsonPath("$.after[0].id").value(below.getId()))
        .andExpect(jsonPath("$.after[0].hasKoreanVendor").value(false));
  }
}

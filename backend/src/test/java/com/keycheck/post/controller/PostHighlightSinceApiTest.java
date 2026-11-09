package com.keycheck.post.controller;

import static org.hamcrest.Matchers.contains;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.keycheck.MutableClock;
import com.keycheck.TestcontainersConfiguration;
import com.keycheck.crawl.client.GeekhackClient;
import com.keycheck.crawl.client.RequestGate;
import com.keycheck.image.domain.ImageSource;
import com.keycheck.post.domain.Board;
import com.keycheck.post.domain.Category;
import com.keycheck.post.domain.Post;
import com.keycheck.post.repository.PostRepository;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** 수집 기간 경계(since-topic-id = 1000)보다 작은 글은 하이라이트 칸과 날짜 줄 숫자에서 빠진다 */
@SpringBootTest(properties = "keycheck.collect.since-topic-id=1000")
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, PostHighlightSinceApiTest.ClockConfig.class})
class PostHighlightSinceApiTest {
  /** 한국 시간 2026-11-13 12:00 */
  private static final Instant NOW = Instant.parse("2026-11-13T03:00:00Z");

  /** NOW의 한국 날짜 */
  private static final LocalDate TODAY = LocalDate.of(2026, 11, 13);

  @Autowired private MockMvc mockMvc;
  @Autowired private PostRepository postRepository;
  @Autowired private JdbcTemplate jdbcTemplate;
  @Autowired private MutableClock clock;

  // 실제 geekhack을 호출하지 않는다
  @MockitoBean private GeekhackClient client;
  @MockitoBean private RequestGate gate;

  @BeforeEach
  void setUp() {
    clock.set(NOW);
    postRepository.deleteAll();
  }

  @AfterEach
  void noGeekhackCalls() {
    verifyNoInteractions(client, gate);
  }

  /** 하이라이트 후보: 대표 이미지가 있는 본문 읽은 글. 작성일은 시계에서 daysAgo일 전. */
  private Post save(Board board, long topicId, int daysAgo, int views, int replies) {
    Post post =
        Post.create(
            board,
            topicId,
            "글 " + topicId,
            "maker",
            Category.KEYBOARD,
            clock.instant().minus(Duration.ofDays(daysAgo)),
            views,
            replies,
            clock.instant(),
            "https://geekhack.org/index.php?topic=" + topicId + ".0",
            "본문");
    post.addImage("https://i.imgur.com/" + topicId + ".jpg", ImageSource.EXTERNAL, 0, null);
    postRepository.saveAndFlush(post);
    post.setRepresentativeImageId(post.getImages().get(0).getId());
    return postRepository.saveAndFlush(post);
  }

  @Test
  @DisplayName("경계 이전 글은 마감 띠를 포함해 어떤 칸에도 들어가지 않고 날짜 줄 숫자에도 세지 않는다")
  void excludesBeforeSince() throws Exception {
    // 후보라면 칸마다 1위가 되도록 조회수·댓글을 크게 주고, 공동구매 글에는 가까운 마감도 넣는다
    Post beforeGb = save(Board.GB, 999, 2, 9000, 900);
    jdbcTemplate.update(
        "update post set gb_end_date = ? where id = ?", TODAY.plusDays(3), beforeGb.getId());
    save(Board.IC, 998, 2, 9000, 900);
    save(Board.GB, 1000, 3, 100, 0);
    save(Board.IC, 1001, 3, 100, 0);

    mockMvc
        .perform(get("/api/posts/highlights"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.slots[*].slot", contains("FEATURE", "TALL")))
        .andExpect(jsonPath("$.slots[*].post.title", contains("글 1000", "글 1001")))
        .andExpect(jsonPath("$.newIcCount").value(1))
        .andExpect(jsonPath("$.newGbCount").value(1));
  }

  @TestConfiguration(proxyBeanMethods = false)
  static class ClockConfig {
    @Bean
    @Primary
    MutableClock mutableClock() {
      return new MutableClock();
    }
  }
}

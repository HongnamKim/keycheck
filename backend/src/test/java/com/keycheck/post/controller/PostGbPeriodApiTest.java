package com.keycheck.post.controller;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.keycheck.TestcontainersConfiguration;
import com.keycheck.crawl.client.GeekhackClient;
import com.keycheck.crawl.client.RequestGate;
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

/** 카드의 공동구매 기간(gbStartDate·gbEndDate). 기간은 요약기가 뽑으므로 테스트는 DB에 직접 넣는다. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class PostGbPeriodApiTest {
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

  private Post saveGb(long topicId) {
    return postRepository.saveAndFlush(
        Post.create(
            Board.GB,
            topicId,
            "글 " + topicId,
            "maker",
            Category.KEYBOARD,
            Instant.parse("2026-10-01T00:00:00Z"),
            100,
            5,
            Instant.parse("2026-11-01T00:00:00Z"),
            "https://geekhack.org/index.php?topic=" + topicId + ".0",
            "본문"));
  }

  @Test
  @DisplayName("목록 카드는 공동구매 기간을 YYYY-MM-DD로 주고, 기간이 없으면 둘 다 null")
  void cardHasGbPeriod() throws Exception {
    saveGb(1);
    Post withPeriod = saveGb(2);
    jdbcTemplate.update(
        "update post set gb_start_date = '2026-10-30', gb_end_date = '2026-11-13' where id = ?",
        withPeriod.getId());

    mockMvc
        .perform(get("/api/posts?board=gb"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items[*].title", contains("글 2", "글 1")))
        .andExpect(jsonPath("$.items[0].gbStartDate").value("2026-10-30"))
        .andExpect(jsonPath("$.items[0].gbEndDate").value("2026-11-13"))
        .andExpect(jsonPath("$.items[1].gbStartDate").value(nullValue()))
        .andExpect(jsonPath("$.items[1].gbEndDate").value(nullValue()));
  }

  @Test
  @DisplayName("GB에서 IC로 옮겨져 예전 기간이 남은 글도 카드의 기간은 둘 다 null")
  void movedToIcCardHasNoGbPeriod() throws Exception {
    Post moved = saveGb(1);
    jdbcTemplate.update(
        "update post set board = 'IC', gb_start_date = '2026-10-30', gb_end_date = '2026-11-13'"
            + " where id = ?",
        moved.getId());

    mockMvc
        .perform(get("/api/posts?board=ic"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items[*].title", contains("글 1")))
        .andExpect(jsonPath("$.items[0].board").value("IC"))
        .andExpect(jsonPath("$.items[0].gbStartDate").value(nullValue()))
        .andExpect(jsonPath("$.items[0].gbEndDate").value(nullValue()));
  }
}

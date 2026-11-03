package com.keycheck.post.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.keycheck.TestcontainersConfiguration;
import com.keycheck.crawl.client.GeekhackClient;
import com.keycheck.crawl.client.RequestGate;
import com.keycheck.post.domain.Category;
import com.keycheck.post.domain.Post;
import com.keycheck.post.repository.PostRepository;
import jakarta.persistence.EntityManagerFactory;
import java.time.Instant;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
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

/** 카드·상세의 작성일 추정(estimatedPostedAt)과 마지막 게시물 시각(lastPostAt) */
@SpringBootTest(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class PostDateApiTest {
  private static final Instant T0 = Instant.parse("2026-01-01T00:00:00Z");
  private static final Instant LAST_POST_AT = Instant.parse("2026-10-01T00:00:00Z");

  @Autowired private MockMvc mockMvc;
  @Autowired private PostRepository postRepository;
  @Autowired private JdbcTemplate jdbcTemplate;
  @Autowired private EntityManagerFactory entityManagerFactory;

  // 실제 geekhack을 호출하지 않는다. 목 RequestGate는 간격을 주지 않으므로 틀 글을 채우지 않는다.
  @MockitoBean private GeekhackClient client;
  @MockitoBean private RequestGate gate;

  /** 모든 글이 커버리지 안에 있고 끝까지 받은 상태 */
  @BeforeEach
  void setUp() {
    postRepository.deleteAll();
    jdbcTemplate.update(
        "update board_backfill set next_page = 1, floor_topic_id = 0, exhausted = true");
  }

  private Post saveFull(long topicId, Instant postedAt, boolean hidden) {
    Post post =
        Post.create(
            topicId,
            "글 " + topicId,
            "maker",
            Category.KEYBOARD,
            postedAt,
            0,
            0,
            Instant.parse("2026-11-01T00:00:00Z"),
            "https://geekhack.org/index.php?topic=" + topicId + ".0",
            "본문");
    post.updateLastPostAt(LAST_POST_AT);
    post.setHidden(hidden);
    return postRepository.saveAndFlush(post);
  }

  private Post saveSkeleton(long topicId, Instant lastPostAt) {
    Post post =
        Post.createSkeleton(
            topicId,
            "틀 " + topicId,
            "maker",
            0,
            0,
            Instant.parse("2026-11-01T00:00:00Z"),
            "https://geekhack.org/index.php?topic=" + topicId + ".0");
    post.updateLastPostAt(lastPostAt);
    return postRepository.saveAndFlush(post);
  }

  @Test
  @DisplayName("작성일이 있는 글은 estimatedPostedAt이 null이고 lastPostAt을 준다")
  void fullPostHasNoEstimate() throws Exception {
    saveFull(100, T0, false);

    mockMvc
        .perform(get("/api/posts"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items[0].postedAt").value(T0.toString()))
        .andExpect(jsonPath("$.items[0].estimatedPostedAt").value(nullValue()))
        .andExpect(jsonPath("$.items[0].lastPostAt").value(LAST_POST_AT.toString()));
  }

  @Test
  @DisplayName("틀 글은 앞뒤 기준점(숨김 글 포함) 사이를 글 번호로 보간한 작성일을 준다")
  void skeletonEstimatedBetweenAnchors() throws Exception {
    saveFull(100, T0, false);
    saveFull(200, T0.plusSeconds(100 * 3600), true);
    saveSkeleton(150, null);

    mockMvc
        .perform(get("/api/posts"))
        .andExpect(jsonPath("$.items[0].title").value("틀 150"))
        .andExpect(jsonPath("$.items[0].postedAt").value(nullValue()))
        .andExpect(jsonPath("$.items[0].estimatedPostedAt").value("2026-01-03T02:00:00Z"))
        .andExpect(jsonPath("$.items[0].lastPostAt").value(nullValue()));
  }

  @Test
  @DisplayName("한쪽에만 기준점이 있으면 그쪽 끝 기준점까지의 전체 기울기로 외삽한다")
  void skeletonExtrapolatedWithOverallSlope() throws Exception {
    // 가까운 99·100은 같은 시각이지만, 가장 먼 0부터는 글 번호 하나에 1시간
    saveFull(0, T0, true);
    saveFull(99, T0.plusSeconds(100 * 3600), true);
    saveFull(100, T0.plusSeconds(100 * 3600), true);
    saveSkeleton(150, null);

    mockMvc
        .perform(get("/api/posts"))
        .andExpect(jsonPath("$.items", hasSize(1)))
        .andExpect(jsonPath("$.items[0].estimatedPostedAt").value("2026-01-07T06:00:00Z"));
  }

  @Test
  @DisplayName("추정 작성일은 마지막 게시물 시각보다 늦지 않다")
  void estimateCappedAtLastPostAt() throws Exception {
    saveFull(100, T0, false);
    saveFull(200, T0.plusSeconds(100 * 3600), false);
    Instant lastPostAt = T0.plusSeconds(3600);
    saveSkeleton(150, lastPostAt);

    mockMvc
        .perform(get("/api/posts?sort=views"))
        .andExpect(jsonPath("$.items[?(@.title == '틀 150')].estimatedPostedAt")
            .value(lastPostAt.toString()))
        .andExpect(jsonPath("$.items[?(@.title == '틀 150')].lastPostAt")
            .value(lastPostAt.toString()));
  }

  @Test
  @DisplayName("기준점이 없으면 estimatedPostedAt은 null")
  void noAnchorsNoEstimate() throws Exception {
    saveSkeleton(150, LAST_POST_AT);
    saveSkeleton(160, LAST_POST_AT);

    mockMvc
        .perform(get("/api/posts"))
        .andExpect(jsonPath("$.items", hasSize(2)))
        .andExpect(jsonPath("$.items[*].estimatedPostedAt", everyItem(nullValue())))
        .andExpect(jsonPath("$.items[*].lastPostAt", everyItem(notNullValue())));
  }

  @Test
  @DisplayName("채우지 못한 틀 글 상세도 추정 작성일과 lastPostAt을 준다")
  void detailHasEstimate() throws Exception {
    saveFull(100, T0, false);
    saveFull(200, T0.plusSeconds(100 * 3600), false);
    Post skeleton = saveSkeleton(150, LAST_POST_AT);

    mockMvc
        .perform(get("/api/posts/" + skeleton.getId()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.detailLoaded").value(false))
        .andExpect(jsonPath("$.postedAt").value(nullValue()))
        .andExpect(jsonPath("$.estimatedPostedAt").value("2026-01-03T02:00:00Z"))
        .andExpect(jsonPath("$.lastPostAt").value(LAST_POST_AT.toString()));
  }

  @Test
  @DisplayName("작성일이 있는 글 상세는 estimatedPostedAt이 null")
  void fullDetailHasNoEstimate() throws Exception {
    saveFull(100, T0, false);
    Post post = saveFull(150, T0.plusSeconds(3600), false);
    saveFull(200, T0.plusSeconds(100 * 3600), false);

    mockMvc
        .perform(get("/api/posts/" + post.getId()))
        .andExpect(jsonPath("$.postedAt").value(T0.plusSeconds(3600).toString()))
        .andExpect(jsonPath("$.estimatedPostedAt").value(nullValue()));
  }

  @Test
  @DisplayName("목록의 쿼리 수는 틀 글 카드 수에 비례하지 않는다")
  void queryCountDoesNotGrowWithCards() throws Exception {
    // 기준점은 숨김이라 카드에는 틀 글만 나온다
    saveFull(100, T0, true);
    saveFull(101, T0.plusSeconds(3600), true);
    saveFull(1000, T0.plusSeconds(900 * 3600), true);
    saveFull(1001, T0.plusSeconds(901 * 3600), true);
    for (long topicId = 500; topicId < 502; topicId++) {
      saveSkeleton(topicId, null);
    }
    long twoCards = statementsFor("/api/posts", 2);

    for (long topicId = 502; topicId < 520; topicId++) {
      saveSkeleton(topicId, null);
    }
    long twentyCards = statementsFor("/api/posts", 20);

    assertThat(twoCards).isPositive();
    assertThat(twentyCards).isEqualTo(twoCards);
  }

  private long statementsFor(String url, int cards) throws Exception {
    Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
    statistics.clear();
    mockMvc
        .perform(get(url))
        .andExpect(jsonPath("$.items", hasSize(cards)))
        .andExpect(jsonPath("$.items[*].estimatedPostedAt", everyItem(notNullValue())));
    return statistics.getPrepareStatementCount();
  }
}

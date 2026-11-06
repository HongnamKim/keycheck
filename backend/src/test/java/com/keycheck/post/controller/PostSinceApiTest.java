package com.keycheck.post.controller;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.Mockito.verifyNoInteractions;
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

/** 수집 기간 경계(since-topic-id = 1000)보다 작은 글은 목록·하단 목록·상세에서 빠진다 */
@SpringBootTest(properties = "keycheck.collect.since-topic-id=1000")
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class PostSinceApiTest {
  private static final Instant CHECKED_AT = Instant.parse("2026-11-01T00:00:00Z");

  @Autowired private MockMvc mockMvc;
  @Autowired private PostRepository postRepository;
  @Autowired private JdbcTemplate jdbcTemplate;

  // 실제 geekhack을 호출하지 않는다
  @MockitoBean private GeekhackClient client;
  @MockitoBean private RequestGate gate;

  /** 경계 바로 아래 틀 글. 상세가 열리면 geekhack에서 채우려 한다. */
  private Post before;

  private Post atSince;
  private Post after;

  @BeforeEach
  void setUp() {
    postRepository.deleteAll();
    jdbcTemplate.update(
        "update board_backfill set next_page = 0, floor_topic_id = null, exhausted = false");
    // 과거 글 확보가 일어나지 않게 IC는 끝까지 받은 상태로 둔다
    jdbcTemplate.update(
        "update board_backfill set next_page = 1, floor_topic_id = 0, exhausted = true"
            + " where board = 'IC'");
    // 조회수는 경계 아래 글이 가장 크다
    before =
        postRepository.saveAndFlush(
            Post.createSkeleton(
                Board.IC,
                999L,
                "글 999",
                "maker",
                300,
                0,
                CHECKED_AT,
                "https://geekhack.org/index.php?topic=999.0"));
    atSince = save(1000, 100);
    after = save(1001, 50);
    // 경계 아래 분류된 글(조회수 가장 작음). 미분류 틀 글이 걸러지는 분류 필터와 조회수순 하단 목록의 아래쪽에서도 빠지는지 본다
    save(998, 10);
  }

  private Post save(long topicId, int views) {
    return postRepository.saveAndFlush(
        Post.create(
            Board.IC,
            topicId,
            "글 " + topicId,
            "maker",
            Category.KEYBOARD,
            Instant.parse("2026-10-01T00:00:00Z"),
            views,
            0,
            CHECKED_AT,
            "https://geekhack.org/index.php?topic=" + topicId + ".0",
            "본문"));
  }

  @Test
  @DisplayName("최근순 목록은 경계 글부터 주고 그 아래 글에서 끝난다")
  void recentListExcludesBeforeSince() throws Exception {
    mockMvc
        .perform(get("/api/posts?board=ic&sort=recent"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items[*].title", contains("글 1001", "글 1000")))
        .andExpect(jsonPath("$.hasMore").value(false));
  }

  @Test
  @DisplayName("경계가 생기기 전에 경계 아래까지 받아 둔 게시판의 첫 목록 응답에서 바로 끝난다")
  void recentListEndsWhenBackfillAlreadyBelowSince() throws Exception {
    jdbcTemplate.update(
        "update board_backfill set floor_topic_id = 900, exhausted = false where board = 'IC'");

    mockMvc
        .perform(get("/api/posts?board=ic&sort=recent"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items[*].title", contains("글 1001", "글 1000")))
        .andExpect(jsonPath("$.hasMore").value(false));
    verifyNoInteractions(client, gate);
  }

  @Test
  @DisplayName("두 게시판 모두 경계 아래까지 받아 둔 all 목록은 빈 쪽을 hasMore=true로 주지 않는다")
  void allListDoesNotWaitWhenBothBackfillsAlreadyBelowSince() throws Exception {
    jdbcTemplate.update("update board_backfill set floor_topic_id = 900, exhausted = false");

    mockMvc
        .perform(get("/api/posts?sort=recent"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items[*].title", contains("글 1001", "글 1000")));
    mockMvc
        .perform(get("/api/posts?sort=recent&cursor=1000"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items", hasSize(0)))
        .andExpect(jsonPath("$.hasMore").value(false));
    verifyNoInteractions(client, gate);
  }

  @Test
  @DisplayName("분류 필터의 두 게시판 목록(게시판마다 floor로 조회)도 경계 아래 글을 뺀다")
  void filteredRecentListExcludesBeforeSince() throws Exception {
    mockMvc
        .perform(get("/api/posts?category=keyboard&sort=recent"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items[*].title", contains("글 1001", "글 1000")))
        .andExpect(jsonPath("$.hasMore").value(false));
  }

  @Test
  @DisplayName("조회수순 목록에서 경계 아래 글은 조회수가 가장 커도 빠진다")
  void viewsListExcludesBeforeSince() throws Exception {
    mockMvc
        .perform(get("/api/posts?sort=views"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items[*].title", contains("글 1000", "글 1001")))
        .andExpect(jsonPath("$.hasMore").value(false));
  }

  @Test
  @DisplayName("하단 목록은 최근순·조회수순 모두 경계 아래 글을 빼고, 경계 아래 글을 기준으로 하면 404")
  void neighborsExcludeBeforeSince() throws Exception {
    mockMvc
        .perform(get("/api/posts/" + after.getId() + "/neighbors?board=ic&sort=recent&size=5"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.before", hasSize(0)))
        .andExpect(jsonPath("$.after[*].title", contains("글 1000")));
    mockMvc
        .perform(get("/api/posts/" + atSince.getId() + "/neighbors?sort=views&size=5"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.before", hasSize(0)))
        .andExpect(jsonPath("$.after[*].title", contains("글 1001")));
    // 조회수순 위쪽에도 경계 글 그 글은 나오고, 조회수가 가장 큰 경계 아래 글은 빠진다
    mockMvc
        .perform(get("/api/posts/" + after.getId() + "/neighbors?sort=views&size=5"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.before[*].title", contains("글 1000")));
    mockMvc
        .perform(get("/api/posts/" + before.getId() + "/neighbors"))
        .andExpect(status().isNotFound());
  }

  @Test
  @DisplayName("경계 아래 글의 상세는 geekhack에서 채우지 않고 404, 경계 글은 200")
  void detailOfBeforeSinceIs404() throws Exception {
    mockMvc.perform(get("/api/posts/" + before.getId())).andExpect(status().isNotFound());
    verifyNoInteractions(client, gate);

    mockMvc
        .perform(get("/api/posts/" + atSince.getId()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.title").value("글 1000"));
  }

  @Test
  @DisplayName("경계 아래 글의 조회수 갱신은 geekhack을 부르지 않고 404")
  void statsOfBeforeSinceIs404() throws Exception {
    mockMvc
        .perform(get("/api/posts/" + before.getId() + "/stats"))
        .andExpect(status().isNotFound());
    verifyNoInteractions(client, gate);

    mockMvc.perform(get("/api/posts/" + atSince.getId() + "/stats")).andExpect(status().isOk());
  }
}

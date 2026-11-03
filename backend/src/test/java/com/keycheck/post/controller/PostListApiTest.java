package com.keycheck.post.controller;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.keycheck.BoardHtml;
import com.keycheck.TestcontainersConfiguration;
import com.keycheck.crawl.client.GeekhackClient;
import com.keycheck.crawl.client.RequestGate;
import com.keycheck.post.domain.Category;
import com.keycheck.post.domain.Post;
import com.keycheck.post.repository.PostRepository;
import java.time.Duration;
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

/** 목록이 모자랄 때 geekhack 작성순 목록에서 과거 글을 확보하는 동작 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class PostListApiTest {
  @Autowired private MockMvc mockMvc;
  @Autowired private PostRepository postRepository;
  @Autowired private JdbcTemplate jdbcTemplate;

  // 실제 geekhack을 호출하지 않는다. 파서와 DB는 실제 것을 쓴다.
  @MockitoBean private GeekhackClient client;
  @MockitoBean private RequestGate gate;

  @BeforeEach
  void setUp() {
    postRepository.deleteAll();
    setBackfill(0, null, false);
    when(gate.tryAcquire(any(Duration.class))).thenReturn(true);
  }

  private void setBackfill(int nextPage, Long floorTopicId, boolean exhausted) {
    jdbcTemplate.update(
        "update board_backfill set next_page = ?, floor_topic_id = ?, exhausted = ?",
        nextPage,
        floorTopicId,
        exhausted);
  }

  private void save(long topicId, int views) {
    postRepository.saveAndFlush(
        Post.create(
            topicId,
            "글 " + topicId,
            "maker",
            Category.KEYBOARD,
            Instant.parse("2026-10-01T00:00:00Z"),
            views,
            0,
            Instant.parse("2026-11-01T00:00:00Z"),
            "https://geekhack.org/index.php?topic=" + topicId + ".0",
            "본문"));
  }

  private void saveRange(long fromTopicId, long toTopicId) {
    for (long topicId = fromTopicId; topicId <= toTopicId; topicId++) {
      save(topicId, 0);
    }
  }

  @Test
  @DisplayName("최근순은 커버리지(floor 이상) 안의 글만 준다")
  void recentShowsOnlyCoveredPosts() throws Exception {
    setBackfill(1, 2000L, true);
    save(1999, 0);
    save(2000, 0);
    save(3000, 0);

    mockMvc
        .perform(get("/api/posts"))
        .andExpect(jsonPath("$.items[*].title", contains("글 3000", "글 2000")));
  }

  @Test
  @DisplayName("모자라면 작성순 목록 다음 쪽을 한 번 받아 틀 글로 저장하고 다시 조회한다")
  void backfillsOnceWhenShort() throws Exception {
    when(client.fetchBoardPageByFirstPost(0)).thenReturn(BoardHtml.page(0, 3000, 2000));

    mockMvc
        .perform(get("/api/posts"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items[*].title", contains("[IC] Board 3000", "[IC] Board 2000")))
        .andExpect(jsonPath("$.items[0].detailLoaded").value(false))
        .andExpect(jsonPath("$.items[0].category").value("UNCLASSIFIED"))
        .andExpect(jsonPath("$.items[0].representativeImageUrl").value(nullValue()))
        .andExpect(jsonPath("$.items[0].postedAt").value(nullValue()))
        .andExpect(jsonPath("$.items[0].summaryStatus").value("PENDING"))
        .andExpect(jsonPath("$.nextCursor").value("2000"))
        .andExpect(jsonPath("$.hasMore").value(true));

    // 다시 조회해도 20건이 안 되지만, 한 요청에 geekhack 목록은 한 번만 받는다
    verify(client, times(1)).fetchBoardPageByFirstPost(anyInt());
    verify(gate).tryAcquire(Duration.ofSeconds(3));
  }

  @Test
  @DisplayName("분류 필터는 과거 글을 확보하지 않는다(확보한 틀은 미분류라 붙지 않는다). 모자라면 hasMore=false")
  void categoryFilterDoesNotBackfill() throws Exception {
    setBackfill(1, 2000L, false);
    saveRange(2000, 2004);

    mockMvc
        .perform(get("/api/posts?category=keyboard"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items", hasSize(5)))
        .andExpect(jsonPath("$.hasMore").value(false));

    verify(client, never()).fetchBoardPageByFirstPost(anyInt());
    verify(gate, never()).tryAcquire(any(Duration.class));
  }

  @Test
  @DisplayName("세부 분류 필터도 과거 글을 확보하지 않는다. 모자라면 hasMore=false")
  void subcategoryFilterDoesNotBackfill() throws Exception {
    setBackfill(1, 2000L, false);
    saveRange(2000, 2004);
    jdbcTemplate.update("update post set subcategory = 'KB_65' where topic_id <= 2002");

    mockMvc
        .perform(get("/api/posts?category=keyboard&sub=65"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items", hasSize(3)))
        .andExpect(jsonPath("$.hasMore").value(false));

    verify(client, never()).fetchBoardPageByFirstPost(anyInt());
    verify(gate, never()).tryAcquire(any(Duration.class));
  }

  @Test
  @DisplayName("분류 필터에서 20건이 차면 hasMore=true")
  void categoryFilterFullPageHasMore() throws Exception {
    setBackfill(1, 2000L, false);
    saveRange(2000, 2019);

    mockMvc
        .perform(get("/api/posts?category=keyboard"))
        .andExpect(jsonPath("$.items", hasSize(20)))
        .andExpect(jsonPath("$.hasMore").value(true));
  }

  @Test
  @DisplayName("미분류 틀 글은 전체에만 보이고 분류 필터에는 나오지 않는다")
  void unclassifiedOnlyInAll() throws Exception {
    when(client.fetchBoardPageByFirstPost(0)).thenReturn(BoardHtml.page(0, 3000, 2000));
    mockMvc.perform(get("/api/posts")).andExpect(jsonPath("$.items", hasSize(2)));

    mockMvc.perform(get("/api/posts?category=keyboard")).andExpect(jsonPath("$.items", hasSize(0)));
    mockMvc.perform(get("/api/posts?category=keycap")).andExpect(jsonPath("$.items", hasSize(0)));
  }

  @Test
  @DisplayName("20건이 차면 목록을 받지 않는다")
  void doesNotBackfillWhenFull() throws Exception {
    setBackfill(1, 2000L, false);
    saveRange(2000, 2019);

    mockMvc
        .perform(get("/api/posts"))
        .andExpect(jsonPath("$.items", hasSize(20)))
        .andExpect(jsonPath("$.nextCursor").value("2000"))
        .andExpect(jsonPath("$.hasMore").value(true));

    verify(client, never()).fetchBoardPageByFirstPost(anyInt());
  }

  @Test
  @DisplayName("cursor로 이어 받다가 커버리지 끝에 닿으면 다음 쪽을 받아 이어 준다")
  void cursorContinuesIntoBackfill() throws Exception {
    setBackfill(1, 2000L, false);
    saveRange(2000, 2019);
    when(client.fetchBoardPageByFirstPost(1)).thenReturn(BoardHtml.page(1, 1999, 1998));

    mockMvc
        .perform(get("/api/posts?cursor=2000"))
        .andExpect(jsonPath("$.items[*].title", contains("[IC] Board 1999", "[IC] Board 1998")))
        .andExpect(jsonPath("$.nextCursor").value("1998"))
        .andExpect(jsonPath("$.hasMore").value(true));
  }

  @Test
  @DisplayName("요청 간격을 얻지 못하면 목록을 받지 않고 지금 있는 것만 준다")
  void gateClosedReturnsCurrent() throws Exception {
    when(gate.tryAcquire(any(Duration.class))).thenReturn(false);
    setBackfill(1, 2000L, false);
    save(2000, 0);
    save(2001, 0);

    mockMvc
        .perform(get("/api/posts"))
        .andExpect(jsonPath("$.items", hasSize(2)))
        .andExpect(jsonPath("$.hasMore").value(true));

    verify(client, never()).fetchBoardPageByFirstPost(anyInt());
  }

  @Test
  @DisplayName("커버리지가 없고 목록을 받지 못하면 빈 목록, hasMore=true, nextCursor=null")
  void noCoverageAndGateClosed() throws Exception {
    when(gate.tryAcquire(any(Duration.class))).thenReturn(false);
    save(3000, 0);

    mockMvc
        .perform(get("/api/posts"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items", hasSize(0)))
        .andExpect(jsonPath("$.nextCursor").value(nullValue()))
        .andExpect(jsonPath("$.hasMore").value(true));
  }

  @Test
  @DisplayName("항목이 없으면 받은 cursor를 그대로 nextCursor로 준다")
  void emptyResultKeepsCursor() throws Exception {
    when(gate.tryAcquire(any(Duration.class))).thenReturn(false);
    setBackfill(1, 2000L, false);

    mockMvc
        .perform(get("/api/posts?cursor=1500"))
        .andExpect(jsonPath("$.items", hasSize(0)))
        .andExpect(jsonPath("$.nextCursor").value("1500"))
        .andExpect(jsonPath("$.hasMore").value(true));
  }

  @Test
  @DisplayName("목록 요청이 실패해도 200으로 지금 있는 것만 준다")
  void boardFailureReturnsCurrent() throws Exception {
    when(client.fetchBoardPageByFirstPost(anyInt())).thenThrow(new IllegalStateException("요청 실패"));
    setBackfill(1, 2000L, false);
    save(2000, 0);

    mockMvc
        .perform(get("/api/posts"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items", hasSize(1)))
        .andExpect(jsonPath("$.hasMore").value(true));
  }

  @Test
  @DisplayName("끝까지 받았으면 목록을 받지 않고, 20건 미만이면 hasMore=false")
  void exhaustedHasNoMore() throws Exception {
    setBackfill(5, 2000L, true);
    save(2000, 0);

    mockMvc
        .perform(get("/api/posts"))
        .andExpect(jsonPath("$.items", hasSize(1)))
        .andExpect(jsonPath("$.hasMore").value(false));

    verify(gate, never()).tryAcquire(any(Duration.class));
    verify(client, never()).fetchBoardPageByFirstPost(anyInt());
  }

  @Test
  @DisplayName("끝까지 받았어도 20건이 차면 hasMore=true")
  void exhaustedButFullHasMore() throws Exception {
    setBackfill(5, 2000L, true);
    saveRange(2000, 2019);

    mockMvc.perform(get("/api/posts")).andExpect(jsonPath("$.hasMore").value(true));
  }

  @Test
  @DisplayName("조회수순은 커버리지와 상관없이 저장된 글 전체를 \"조회수_글 번호\" cursor로 주고, 목록을 받지 않는다")
  void viewsUsesKeysetCursorWithoutBackfill() throws Exception {
    for (long topicId = 1; topicId <= 25; topicId++) {
      save(topicId, (int) topicId * 10);
    }

    mockMvc
        .perform(get("/api/posts?sort=views"))
        .andExpect(jsonPath("$.items", hasSize(20)))
        .andExpect(jsonPath("$.items[0].title").value("글 25"))
        .andExpect(jsonPath("$.items[19].title").value("글 6"))
        .andExpect(jsonPath("$.nextCursor").value("60_6"))
        .andExpect(jsonPath("$.hasMore").value(true));
    mockMvc
        .perform(get("/api/posts?sort=views&cursor=60_6"))
        .andExpect(jsonPath("$.items[*].title", contains("글 5", "글 4", "글 3", "글 2", "글 1")))
        .andExpect(jsonPath("$.nextCursor").value(nullValue()))
        .andExpect(jsonPath("$.hasMore").value(false));

    verify(client, never()).fetchBoardPageByFirstPost(anyInt());
  }

  @Test
  @DisplayName("조회수순으로 이어 받는 사이 앞쪽에 글이 늘어도 이미 받은 글이 다시 오지 않는다")
  void viewsCursorIgnoresPostsAddedAbove() throws Exception {
    for (long topicId = 1; topicId <= 25; topicId++) {
      save(topicId, (int) topicId * 10);
    }
    save(100, 1000);

    mockMvc
        .perform(get("/api/posts?sort=views&cursor=60_6"))
        .andExpect(jsonPath("$.items[*].title", contains("글 5", "글 4", "글 3", "글 2", "글 1")));
  }

  @Test
  @DisplayName("조회수순 cursor가 형식에 맞지 않거나 범위를 넘으면 400")
  void invalidViewsCursorIs400() throws Exception {
    for (String cursor :
        new String[] {"20", "abc_1", "10_abc", "10_0", "-10_5", "10_5_1", "4294967276_5"}) {
      mockMvc
          .perform(get("/api/posts?sort=views&cursor=" + cursor))
          .andExpect(status().isBadRequest());
    }
  }

  @Test
  @DisplayName("조회수가 같으면 글 번호 내림차순")
  void viewsTieBreaksByTopicId() throws Exception {
    save(1, 10);
    save(3, 10);
    save(2, 10);

    mockMvc
        .perform(get("/api/posts?sort=views"))
        .andExpect(jsonPath("$.items[*].title", contains("글 3", "글 2", "글 1")));
  }
}

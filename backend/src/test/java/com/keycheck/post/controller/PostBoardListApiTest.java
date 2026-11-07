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
import com.keycheck.post.domain.Board;
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

/**
 * 게시판 필터(board=ic, gb)와 두 게시판을 합친 목록(board=all). 합친 목록은 두 게시판 모두 빠짐없이 받아 둔 범위만 보여 주고,
 * 모자라면 덜 받은 게시판을 확보한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class PostBoardListApiTest {
  @Autowired private MockMvc mockMvc;
  @Autowired private PostRepository postRepository;
  @Autowired private JdbcTemplate jdbcTemplate;

  // 실제 geekhack을 호출하지 않는다. 파서와 DB는 실제 것을 쓴다.
  @MockitoBean private GeekhackClient client;
  @MockitoBean private RequestGate gate;

  @BeforeEach
  void setUp() {
    postRepository.deleteAll();
    setBackfill(Board.IC, 0, null, false);
    setBackfill(Board.GB, 0, null, false);
    when(gate.tryAcquire(any(Duration.class))).thenReturn(true);
  }

  private void setBackfill(Board board, int nextPage, Long floorTopicId, boolean exhausted) {
    jdbcTemplate.update(
        "update board_backfill set next_page = ?, floor_topic_id = ?, exhausted = ?"
            + " where board = ?",
        nextPage,
        floorTopicId,
        exhausted,
        board.name());
  }

  private void save(Board board, long topicId, Category category, int views) {
    postRepository.saveAndFlush(
        Post.create(
            board,
            topicId,
            "글 " + topicId,
            "maker",
            category,
            Instant.parse("2026-10-01T00:00:00Z"),
            views,
            0,
            Instant.parse("2026-11-01T00:00:00Z"),
            "https://geekhack.org/index.php?topic=" + topicId + ".0",
            "본문"));
  }

  private void save(Board board, long topicId) {
    save(board, topicId, Category.KEYBOARD, 0);
  }

  private void verifyNoBoardFetch() {
    verify(client, never()).fetchBoardPageByFirstPost(any(Board.class), anyInt());
  }

  @Test
  @DisplayName("게시판 필터는 그 게시판 글만 주고, 기본(all)은 두 게시판 글을 글 번호 내림차순으로 섞어 준다")
  void boardFilter() throws Exception {
    setBackfill(Board.IC, 1, 0L, true);
    setBackfill(Board.GB, 1, 0L, true);
    save(Board.IC, 3000);
    save(Board.GB, 2500);
    save(Board.IC, 2000);

    mockMvc
        .perform(get("/api/posts?board=ic"))
        .andExpect(jsonPath("$.items[*].title", contains("글 3000", "글 2000")))
        .andExpect(jsonPath("$.items[*].board", contains("IC", "IC")));
    mockMvc
        .perform(get("/api/posts?board=gb"))
        .andExpect(jsonPath("$.items[*].title", contains("글 2500")))
        .andExpect(jsonPath("$.items[0].board").value("GB"));
    mockMvc
        .perform(get("/api/posts"))
        .andExpect(jsonPath("$.items[*].title", contains("글 3000", "글 2500", "글 2000")))
        .andExpect(jsonPath("$.items[*].board", contains("IC", "GB", "IC")));
    mockMvc
        .perform(get("/api/posts?board=all"))
        .andExpect(jsonPath("$.items", hasSize(3)))
        .andExpect(jsonPath("$.hasMore").value(false));
  }

  @Test
  @DisplayName("허용되지 않는 board는 400")
  void invalidBoardIs400() throws Exception {
    for (String board : new String[] {"xx", "132", "70", "icgb"}) {
      mockMvc.perform(get("/api/posts?board=" + board)).andExpect(status().isBadRequest());
    }
  }

  @Test
  @DisplayName("GB 게시판 목록은 GB 커버리지로 거르고, 모자라면 GB 작성순 목록의 다음 쪽을 받는다")
  void gbUsesGbCoverage() throws Exception {
    setBackfill(Board.GB, 1, 2500L, false);
    save(Board.GB, 2500);
    save(Board.GB, 2400);
    save(Board.IC, 3000);
    when(client.fetchBoardPageByFirstPost(Board.GB, 1))
        .thenReturn(BoardHtml.page(1, 2400, 2300));

    mockMvc
        .perform(get("/api/posts?board=gb"))
        .andExpect(jsonPath("$.items[*].title", contains("글 2500", "[IC] Board 2400", "[IC] Board 2300")))
        .andExpect(jsonPath("$.items[*].board", contains("GB", "GB", "GB")))
        .andExpect(jsonPath("$.hasMore").value(true));

    verify(client, times(1)).fetchBoardPageByFirstPost(Board.GB, 1);
    verify(client, never()).fetchBoardPageByFirstPost(Board.IC, 0);
  }

  @Test
  @DisplayName("all은 두 게시판의 floor 중 큰 값(덜 받은 쪽) 이상만 보여 준다")
  void allUsesLargerFloor() throws Exception {
    when(gate.tryAcquire(any(Duration.class))).thenReturn(false);
    setBackfill(Board.IC, 1, 2500L, false);
    setBackfill(Board.GB, 1, 2000L, false);
    save(Board.IC, 3000);
    save(Board.GB, 2600);
    save(Board.IC, 2500);
    save(Board.GB, 2200);
    save(Board.GB, 2000);

    mockMvc
        .perform(get("/api/posts"))
        .andExpect(jsonPath("$.items[*].title", contains("글 3000", "글 2600", "글 2500")))
        .andExpect(jsonPath("$.nextCursor").value("2500"))
        .andExpect(jsonPath("$.hasMore").value(true));
  }

  @Test
  @DisplayName("all에서 끝까지 받은 게시판은 floor를 0으로 보아 다른 게시판의 floor만 따른다")
  void allTreatsExhaustedBoardAsFullyCovered() throws Exception {
    when(gate.tryAcquire(any(Duration.class))).thenReturn(false);
    setBackfill(Board.IC, 5, 3000L, true);
    setBackfill(Board.GB, 1, 2000L, false);
    save(Board.IC, 3000);
    save(Board.IC, 2500);
    save(Board.GB, 2000);
    save(Board.GB, 1900);

    mockMvc
        .perform(get("/api/posts"))
        .andExpect(jsonPath("$.items[*].title", contains("글 3000", "글 2500", "글 2000")))
        .andExpect(jsonPath("$.hasMore").value(true));
  }

  @Test
  @DisplayName("all이 모자라면 floor가 더 큰(덜 받은) 게시판의 다음 쪽을 한 번 받는다")
  void allBackfillsLessCoveredBoard() throws Exception {
    setBackfill(Board.IC, 1, 2000L, false);
    setBackfill(Board.GB, 1, 2500L, false);
    save(Board.GB, 2500);
    save(Board.IC, 2000);
    when(client.fetchBoardPageByFirstPost(Board.GB, 1))
        .thenReturn(BoardHtml.page(1, 2400, 2100));

    mockMvc
        .perform(get("/api/posts"))
        .andExpect(
            jsonPath("$.items[*].title", contains("글 2500", "[IC] Board 2400", "[IC] Board 2100")))
        .andExpect(jsonPath("$.nextCursor").value("2100"))
        .andExpect(jsonPath("$.hasMore").value(true));

    verify(client, times(1)).fetchBoardPageByFirstPost(any(Board.class), anyInt());
    verify(client).fetchBoardPageByFirstPost(Board.GB, 1);
  }

  @Test
  @DisplayName("all에서 아직 한 쪽도 받지 않은 게시판이 있으면 그 게시판부터 받는다")
  void allBackfillsBoardWithoutCoverageFirst() throws Exception {
    setBackfill(Board.IC, 1, 2000L, false);
    save(Board.IC, 3000);
    save(Board.IC, 2000);
    when(client.fetchBoardPageByFirstPost(Board.GB, 0))
        .thenReturn(BoardHtml.page(0, 2600, 2500));

    mockMvc
        .perform(get("/api/posts"))
        .andExpect(
            jsonPath("$.items[*].title", contains("글 3000", "[IC] Board 2600", "[IC] Board 2500")))
        .andExpect(jsonPath("$.items[*].board", contains("IC", "GB", "GB")));

    verify(client, times(1)).fetchBoardPageByFirstPost(any(Board.class), anyInt());
    verify(client).fetchBoardPageByFirstPost(Board.GB, 0);
  }

  @Test
  @DisplayName("둘 다 받은 것이 없으면 IC부터 한 요청에 한 쪽씩 받고, 두 게시판을 받아야 글을 보여 준다")
  void allStartsFromNothing() throws Exception {
    when(client.fetchBoardPageByFirstPost(Board.IC, 0))
        .thenReturn(BoardHtml.page(0, 3000, 2000));
    when(client.fetchBoardPageByFirstPost(Board.GB, 0)).thenReturn(BoardHtml.page(0, 2500));

    mockMvc
        .perform(get("/api/posts"))
        .andExpect(jsonPath("$.items", hasSize(0)))
        .andExpect(jsonPath("$.nextCursor").value(nullValue()))
        .andExpect(jsonPath("$.hasMore").value(true));
    verify(client).fetchBoardPageByFirstPost(Board.IC, 0);
    verify(client, never()).fetchBoardPageByFirstPost(Board.GB, 0);

    mockMvc
        .perform(get("/api/posts"))
        .andExpect(
            jsonPath(
                "$.items[*].title", contains("[IC] Board 3000", "[IC] Board 2500")))
        .andExpect(jsonPath("$.hasMore").value(true));
    verify(client).fetchBoardPageByFirstPost(Board.GB, 0);
  }

  @Test
  @DisplayName("all은 두 게시판 모두 끝까지 받았고 모자랄 때만 hasMore=false이고, 그때는 목록을 받지 않는다")
  void allEndsOnlyWhenBothExhausted() throws Exception {
    setBackfill(Board.IC, 5, 2000L, true);
    setBackfill(Board.GB, 5, 2100L, true);
    save(Board.IC, 2000);
    save(Board.GB, 2100);

    mockMvc
        .perform(get("/api/posts"))
        .andExpect(jsonPath("$.items", hasSize(2)))
        .andExpect(jsonPath("$.hasMore").value(false));
    verifyNoBoardFetch();
  }

  @Test
  @DisplayName("all에서 한 게시판만 끝까지 받았으면, 다른 게시판을 받지 못해 모자라도 hasMore=true")
  void allHasMoreWhileOneBoardRemains() throws Exception {
    when(gate.tryAcquire(any(Duration.class))).thenReturn(false);
    setBackfill(Board.IC, 5, 2000L, true);
    setBackfill(Board.GB, 1, 2100L, false);
    save(Board.GB, 2100);

    mockMvc
        .perform(get("/api/posts"))
        .andExpect(jsonPath("$.items", hasSize(1)))
        .andExpect(jsonPath("$.hasMore").value(true));
  }

  @Test
  @DisplayName("게시판과 분류 필터를 함께 쓰면 둘 다 맞는 글만 주고, 과거 글을 확보하지 않아 모자라면 hasMore=false")
  void boardWithCategoryFilter() throws Exception {
    setBackfill(Board.IC, 1, 2000L, false);
    setBackfill(Board.GB, 1, 2000L, false);
    save(Board.GB, 2100, Category.KEYBOARD, 0);
    save(Board.GB, 2050, Category.KEYCAP, 0);
    save(Board.IC, 2200, Category.KEYBOARD, 0);

    mockMvc
        .perform(get("/api/posts?board=gb&category=keyboard"))
        .andExpect(jsonPath("$.items[*].title", contains("글 2100")))
        .andExpect(jsonPath("$.hasMore").value(false));
    mockMvc
        .perform(get("/api/posts?category=keyboard"))
        .andExpect(jsonPath("$.items[*].title", contains("글 2200", "글 2100")))
        .andExpect(jsonPath("$.hasMore").value(false));
    verifyNoBoardFetch();
    verify(gate, never()).tryAcquire(any(Duration.class));
  }

  @Test
  @DisplayName("분류 필터의 all은 GB를 아직 받지 않았어도(V6 직후) IC 커버리지 안의 글을 준다")
  void allWithCategoryFilterWithoutGbCoverage() throws Exception {
    setBackfill(Board.IC, 3, 2000L, false);
    save(Board.IC, 3000, Category.KEYBOARD, 0);
    save(Board.IC, 2500, Category.KEYCAP, 0);
    save(Board.IC, 2000, Category.KEYBOARD, 0);
    save(Board.GB, 2800, Category.KEYBOARD, 0);

    mockMvc
        .perform(get("/api/posts?category=keyboard"))
        .andExpect(jsonPath("$.items[*].title", contains("글 3000", "글 2000")))
        .andExpect(jsonPath("$.hasMore").value(false));
    verifyNoBoardFetch();
  }

  @Test
  @DisplayName("분류 필터의 all은 게시판마다 그 게시판의 floor로 걸러 섞는다(큰 floor로 자르지 않는다)")
  void allWithCategoryFilterUsesFloorPerBoard() throws Exception {
    setBackfill(Board.IC, 3, 2000L, false);
    setBackfill(Board.GB, 1, 2500L, false);
    save(Board.GB, 2600, Category.KEYBOARD, 0);
    save(Board.IC, 2550, Category.KEYBOARD, 0);
    save(Board.GB, 2400, Category.KEYBOARD, 0);
    save(Board.IC, 2200, Category.KEYBOARD, 0);
    save(Board.IC, 1900, Category.KEYBOARD, 0);

    mockMvc
        .perform(get("/api/posts?category=keyboard"))
        .andExpect(jsonPath("$.items[*].title", contains("글 2600", "글 2550", "글 2200")))
        .andExpect(jsonPath("$.hasMore").value(false));
    mockMvc
        .perform(get("/api/posts?category=keyboard&cursor=2550"))
        .andExpect(jsonPath("$.items[*].title", contains("글 2200")));
    verifyNoBoardFetch();
  }

  @Test
  @DisplayName("분류 필터의 all은 20개를 넘으면 두 게시판을 섞은 앞 20개와 hasMore=true를 준다")
  void allWithCategoryFilterPagesAcrossBoards() throws Exception {
    setBackfill(Board.IC, 1, 1000L, false);
    setBackfill(Board.GB, 1, 1000L, false);
    for (int i = 0; i < 15; i++) {
      save(Board.IC, 2000 + i * 2, Category.KEYBOARD, 0);
      save(Board.GB, 2001 + i * 2, Category.KEYBOARD, 0);
    }

    mockMvc
        .perform(get("/api/posts?category=keyboard"))
        .andExpect(jsonPath("$.items", hasSize(20)))
        .andExpect(jsonPath("$.items[0].title").value("글 2029"))
        .andExpect(jsonPath("$.nextCursor").value("2010"))
        .andExpect(jsonPath("$.hasMore").value(true));
    mockMvc
        .perform(get("/api/posts?category=keyboard&cursor=2010"))
        .andExpect(jsonPath("$.items", hasSize(10)))
        .andExpect(jsonPath("$.items[0].title").value("글 2009"))
        .andExpect(jsonPath("$.hasMore").value(false));
  }

  @Test
  @DisplayName("조회수순은 커버리지와 상관없이 게시판으로만 거르고 목록을 받지 않는다")
  void viewsFiltersByBoardOnly() throws Exception {
    save(Board.IC, 1, Category.KEYBOARD, 100);
    save(Board.GB, 2, Category.KEYBOARD, 30);
    save(Board.GB, 3, Category.KEYCAP, 20);

    mockMvc
        .perform(get("/api/posts?sort=views&board=gb"))
        .andExpect(jsonPath("$.items[*].title", contains("글 2", "글 3")))
        .andExpect(jsonPath("$.hasMore").value(false));
    mockMvc
        .perform(get("/api/posts?sort=views&board=gb&category=keycap"))
        .andExpect(jsonPath("$.items[*].title", contains("글 3")));
    mockMvc
        .perform(get("/api/posts?sort=views"))
        .andExpect(jsonPath("$.items[*].title", contains("글 1", "글 2", "글 3")));
    verifyNoBoardFetch();
  }
}

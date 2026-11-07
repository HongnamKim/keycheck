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
import com.keycheck.post.domain.Subcategory;
import com.keycheck.post.repository.PostRepository;
import java.time.Instant;
import org.junit.jupiter.api.AfterEach;
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

/** 상세 아래 목록: 같은 조건의 목록에서 이 글 바로 위(before)와 바로 아래(after) 글. geekhack을 호출하지 않는다. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class PostNeighborsApiTest {
  @Autowired private MockMvc mockMvc;
  @Autowired private PostRepository postRepository;
  @Autowired private JdbcTemplate jdbcTemplate;

  @MockitoBean private GeekhackClient client;
  @MockitoBean private RequestGate gate;

  @BeforeEach
  void setUp() {
    postRepository.deleteAll();
    setBackfill(Board.IC, 0, null, false);
    setBackfill(Board.GB, 0, null, false);
  }

  @AfterEach
  void noGeekhackCalls() {
    verifyNoInteractions(client, gate);
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

  private Post save(Board board, long topicId, Category category, int views) {
    return postRepository.saveAndFlush(
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

  private Post save(Board board, long topicId) {
    return save(board, topicId, Category.KEYBOARD, 0);
  }

  private void setSubcategory(long topicId, Subcategory subcategory) {
    jdbcTemplate.update(
        "update post set subcategory = ? where topic_id = ?", subcategory.name(), topicId);
  }

  private String url(Post post, String query) {
    return "/api/posts/" + post.getId() + "/neighbors" + (query.isEmpty() ? "" : "?" + query);
  }

  @Test
  @DisplayName("최근순: before는 바로 위 글들을 목록 순서대로(마지막이 바로 위), after는 바로 아래 글들을 목록 순서대로 준다")
  void recentBeforeAndAfterInListOrder() throws Exception {
    setBackfill(Board.IC, 1, 1000L, false);
    Post current = null;
    for (long topicId = 1001; topicId <= 1015; topicId++) {
      Post post = save(Board.IC, topicId);
      if (topicId == 1008) {
        current = post;
      }
    }

    mockMvc
        .perform(get(url(current, "board=ic&size=3")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.before[*].title", contains("글 1011", "글 1010", "글 1009")))
        .andExpect(jsonPath("$.after[*].title", contains("글 1007", "글 1006", "글 1005")))
        .andExpect(jsonPath("$.before[0].board").value("IC"))
        .andExpect(jsonPath("$.before[0].detailLoaded").value(true));
  }

  @Test
  @DisplayName("size를 생략하면 앞뒤로 5개씩 준다")
  void defaultSizeIsFive() throws Exception {
    setBackfill(Board.IC, 1, 1000L, false);
    Post current = null;
    for (long topicId = 1001; topicId <= 1020; topicId++) {
      Post post = save(Board.IC, topicId);
      if (topicId == 1010) {
        current = post;
      }
    }

    mockMvc
        .perform(get(url(current, "board=ic")))
        .andExpect(jsonPath("$.before", hasSize(5)))
        .andExpect(jsonPath("$.before[4].title").value("글 1011"))
        .andExpect(jsonPath("$.after", hasSize(5)))
        .andExpect(jsonPath("$.after[0].title").value("글 1009"));
  }

  @Test
  @DisplayName("가장 최근 글은 before가 비고, 커버리지 끝 근처면 after가 floor 이상 글까지만 와서 모자란다")
  void recentEdges() throws Exception {
    setBackfill(Board.IC, 1, 1003L, false);
    save(Board.IC, 1001);
    save(Board.IC, 1002);
    save(Board.IC, 1003);
    Post fourth = save(Board.IC, 1004);
    Post newest = save(Board.IC, 1005);

    mockMvc
        .perform(get(url(newest, "board=ic&size=3")))
        .andExpect(jsonPath("$.before", hasSize(0)))
        .andExpect(jsonPath("$.after[*].title", contains("글 1004", "글 1003")));
    mockMvc
        .perform(get(url(fourth, "board=ic&size=3")))
        .andExpect(jsonPath("$.before[*].title", contains("글 1005")))
        .andExpect(jsonPath("$.after[*].title", contains("글 1003")));
  }

  @Test
  @DisplayName("글이 커버리지 밖(floor 미만)이어도 그 순서 기준 위치로 계산한다")
  void recentPostBelowFloor() throws Exception {
    setBackfill(Board.IC, 1, 1003L, false);
    Post below = save(Board.IC, 1001);
    save(Board.IC, 1002);
    save(Board.IC, 1003);
    save(Board.IC, 1004);

    mockMvc
        .perform(get(url(below, "board=ic&size=5")))
        .andExpect(jsonPath("$.before[*].title", contains("글 1004", "글 1003")))
        .andExpect(jsonPath("$.after", hasSize(0)));
  }

  @Test
  @DisplayName("board=ic·gb는 그 게시판 글만, all은 두 게시판의 floor 중 큰 값 이상에서 섞어 준다")
  void recentByBoard() throws Exception {
    setBackfill(Board.IC, 1, 1000L, false);
    setBackfill(Board.GB, 1, 1003L, false);
    save(Board.GB, 1001);
    save(Board.IC, 1002);
    save(Board.GB, 1003);
    save(Board.IC, 1004);
    Post current = save(Board.IC, 1005);
    save(Board.GB, 1006);
    save(Board.IC, 1007);

    mockMvc
        .perform(get(url(current, "board=ic")))
        .andExpect(jsonPath("$.before[*].title", contains("글 1007")))
        .andExpect(jsonPath("$.after[*].title", contains("글 1004", "글 1002")));
    // 글이 다른 게시판이어도 GB 목록 순서 기준 위치로 계산한다
    mockMvc
        .perform(get(url(current, "board=gb")))
        .andExpect(jsonPath("$.before[*].title", contains("글 1006")))
        .andExpect(jsonPath("$.after[*].title", contains("글 1003")));
    mockMvc
        .perform(get(url(current, "board=all")))
        .andExpect(jsonPath("$.before[*].title", contains("글 1007", "글 1006")))
        .andExpect(jsonPath("$.after[*].title", contains("글 1004", "글 1003")))
        .andExpect(jsonPath("$.after[*].board", contains("IC", "GB")));
  }

  @Test
  @DisplayName("all에서 아직 받지 않은 게시판이 있으면 분류 필터 없는 목록이 비므로 앞뒤도 빈다")
  void recentAllWithoutCoverage() throws Exception {
    setBackfill(Board.IC, 1, 1000L, false);
    save(Board.IC, 1001);
    Post current = save(Board.IC, 1002);

    mockMvc
        .perform(get(url(current, "")))
        .andExpect(jsonPath("$.before", hasSize(0)))
        .andExpect(jsonPath("$.after", hasSize(0)));
  }

  @Test
  @DisplayName("분류·세부 분류 필터로 거르고, 분류 필터의 all은 게시판마다 그 게시판의 floor로 걸러 섞는다")
  void recentWithCategoryFilter() throws Exception {
    setBackfill(Board.IC, 1, 1000L, false);
    setBackfill(Board.GB, 1, 1004L, false);
    save(Board.IC, 1001, Category.KEYBOARD, 0);
    save(Board.GB, 1002, Category.KEYBOARD, 0);
    save(Board.IC, 1003, Category.KEYCAP, 0);
    save(Board.IC, 1004, Category.KEYBOARD, 0);
    Post current = save(Board.IC, 1005, Category.KEYBOARD, 0);
    save(Board.GB, 1006, Category.KEYBOARD, 0);
    save(Board.IC, 1007, Category.KEYCAP, 0);
    save(Board.IC, 1008, Category.KEYBOARD, 0);
    setSubcategory(1001, Subcategory.KB_65);
    setSubcategory(1005, Subcategory.KB_65);
    setSubcategory(1008, Subcategory.KB_65);
    setSubcategory(1006, Subcategory.KB_75);

    mockMvc
        .perform(get(url(current, "category=keyboard")))
        .andExpect(jsonPath("$.before[*].title", contains("글 1008", "글 1006")))
        .andExpect(jsonPath("$.after[*].title", contains("글 1004", "글 1001")));
    mockMvc
        .perform(get(url(current, "category=keyboard&sub=65")))
        .andExpect(jsonPath("$.before[*].title", contains("글 1008")))
        .andExpect(jsonPath("$.after[*].title", contains("글 1001")));
    mockMvc
        .perform(get(url(current, "category=keycap&board=ic")))
        .andExpect(jsonPath("$.before[*].title", contains("글 1007")))
        .andExpect(jsonPath("$.after[*].title", contains("글 1003")));
  }

  @Test
  @DisplayName("조회수순: 커버리지와 상관없이 조회수 내림차순, 같으면 글 번호 내림차순 기준으로 앞뒤를 준다")
  void views() throws Exception {
    save(Board.IC, 1, Category.KEYBOARD, 300);
    save(Board.GB, 2, Category.KEYBOARD, 100);
    Post current = save(Board.IC, 5, Category.KEYBOARD, 100);
    save(Board.IC, 9, Category.KEYBOARD, 100);
    save(Board.IC, 3, Category.KEYBOARD, 100);
    save(Board.IC, 4, Category.KEYCAP, 50);
    save(Board.GB, 6, Category.KEYBOARD, 10);

    mockMvc
        .perform(get(url(current, "sort=views&size=2")))
        .andExpect(jsonPath("$.before[*].title", contains("글 1", "글 9")))
        .andExpect(jsonPath("$.after[*].title", contains("글 3", "글 2")));
    mockMvc
        .perform(get(url(current, "sort=views&board=ic&category=keyboard")))
        .andExpect(jsonPath("$.before[*].title", contains("글 1", "글 9")))
        .andExpect(jsonPath("$.after[*].title", contains("글 3")));
    mockMvc
        .perform(get(url(current, "sort=views&board=gb")))
        .andExpect(jsonPath("$.before", hasSize(0)))
        .andExpect(jsonPath("$.after[*].title", contains("글 2", "글 6")));
  }

  @Test
  @DisplayName("숨김 글은 앞뒤에서 빠진다")
  void excludesHiddenPosts() throws Exception {
    setBackfill(Board.IC, 1, 1000L, false);
    save(Board.IC, 1001);
    Post hiddenBelow = save(Board.IC, 1002);
    Post current = save(Board.IC, 1003);
    Post hiddenAbove = save(Board.IC, 1004);
    save(Board.IC, 1005);
    hide(hiddenBelow);
    hide(hiddenAbove);

    mockMvc
        .perform(get(url(current, "board=ic")))
        .andExpect(jsonPath("$.before[*].title", contains("글 1005")))
        .andExpect(jsonPath("$.after[*].title", contains("글 1001")));
    mockMvc
        .perform(get(url(current, "board=ic&sort=views")))
        .andExpect(jsonPath("$.before[*].title", contains("글 1005")))
        .andExpect(jsonPath("$.after[*].title", contains("글 1001")));
  }

  private void hide(Post post) {
    post.setHidden(true);
    postRepository.saveAndFlush(post);
  }

  @Test
  @DisplayName("없거나 숨김인 글은 404")
  void missingOrHiddenIs404() throws Exception {
    Post hidden = save(Board.IC, 1001);
    hide(hidden);

    mockMvc.perform(get("/api/posts/999999/neighbors")).andExpect(status().isNotFound());
    mockMvc.perform(get(url(hidden, ""))).andExpect(status().isNotFound());
  }

  @Test
  @DisplayName("size는 1~10이고 범위 밖이거나 숫자가 아니면 400")
  void sizeRange() throws Exception {
    setBackfill(Board.IC, 1, 1000L, false);
    Post current = save(Board.IC, 1001);

    mockMvc.perform(get(url(current, "size=1"))).andExpect(status().isOk());
    mockMvc.perform(get(url(current, "size=10"))).andExpect(status().isOk());
    for (String size : new String[] {"0", "11", "-1", "abc"}) {
      mockMvc.perform(get(url(current, "size=" + size))).andExpect(status().isBadRequest());
    }
  }

  @Test
  @DisplayName("목록 API와 같이 허용되지 않는 board·category·sub·sort는 400")
  void invalidParamsAre400() throws Exception {
    Post current = save(Board.IC, 1001);

    for (String query :
        new String[] {
          "board=xx", "category=mouse", "sort=old", "sub=65", "category=keycap&sub=65",
          "category=keyboard&sub=xx"
        }) {
      mockMvc.perform(get(url(current, query))).andExpect(status().isBadRequest());
    }
  }
}

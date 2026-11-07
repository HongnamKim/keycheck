package com.keycheck.post.controller;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
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
import java.util.List;
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

/**
 * 메인 하이라이트 6칸과 날짜 줄 숫자. 공동구매 기간은 요약기가 뽑으므로 테스트는 DB에 직접 넣는다. 작성일·마감일은 모두 시계 기준 상대값이다.
 * geekhack을 호출하지 않는다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, PostHighlightApiTest.ClockConfig.class})
class PostHighlightApiTest {
  private static final String URL = "/api/posts/highlights";

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

  /** 본문을 읽은 글(저장 전). 작성일은 시계에서 daysAgo일 전. */
  private Post newPost(Board board, long topicId, int daysAgo, int views, int replies) {
    return Post.create(
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
  }

  /** 이미지를 하나 더해 저장하고 대표 이미지로 정한다 */
  private Post saveWithRepresentative(Post post) {
    post.addImage(
        "https://i.imgur.com/" + post.getTopicId() + ".jpg", ImageSource.EXTERNAL, 0, null);
    postRepository.saveAndFlush(post);
    post.setRepresentativeImageId(post.getImages().get(0).getId());
    return postRepository.saveAndFlush(post);
  }

  /** 하이라이트 후보: 대표 이미지가 있는 본문 읽은 글. 작성일은 시계에서 daysAgo일 전. */
  private Post save(Board board, long topicId, int daysAgo, int views, int replies) {
    return saveWithRepresentative(newPost(board, topicId, daysAgo, views, replies));
  }

  private void setGbEndDate(Post post, LocalDate gbEndDate) {
    jdbcTemplate.update("update post set gb_end_date = ? where id = ?", gbEndDate, post.getId());
  }

  @Test
  @DisplayName("칸마다 규칙대로 고른다: 창 안의 조회수·댓글·마감 순위에서 앞 칸에 들어간 글을 건너뛴 첫 글")
  void picksSlotsByRules() throws Exception {
    save(Board.GB, 101, 3, 500, 0);
    // 조회수가 가장 크지만 7일 창 밖이라 큰 칸이 아니다
    save(Board.GB, 102, 20, 800, 0);
    // 마감이 가장 가까운 글(104)은 조회수·글 번호·작성일로 줄 세우면 103에 진다: 마감 순으로 골랐다는 것만 맞힌다
    setGbEndDate(save(Board.GB, 103, 10, 300, 0), TODAY.plusDays(10));
    setGbEndDate(save(Board.GB, 104, 5, 200, 0), TODAY.plusDays(5));
    // 30일 창 밖
    save(Board.GB, 105, 40, 5000, 0);
    save(Board.IC, 201, 2, 900, 100);
    save(Board.IC, 202, 5, 400, 80);
    save(Board.IC, 203, 7, 600, 5);
    // 14일 창 밖
    save(Board.IC, 204, 20, 2000, 500);

    mockMvc
        .perform(get(URL))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.today").value("2026-11-13"))
        .andExpect(
            jsonPath(
                "$.slots[*].slot",
                contains("FEATURE", "TALL", "DEADLINE", "IC_REPLIES", "IC_VIEWS", "GB_VIEWS")))
        .andExpect(
            jsonPath(
                "$.slots[*].post.title",
                contains("글 101", "글 201", "글 104", "글 202", "글 203", "글 102")))
        .andExpect(
            jsonPath("$.slots[*].deadline", contains(false, false, true, false, false, false)))
        .andExpect(jsonPath("$.slots[*].windowDays", contains(7, 14, null, 14, 14, 30)))
        // 댓글 1위·조회 1위(글 201)는 세로 칸에 들어갔으므로 작은 칸 1·2는 그 창의 2위
        .andExpect(jsonPath("$.slots[*].rank", contains(1, 1, null, 2, 2, 1)))
        .andExpect(jsonPath("$.slots[2].post.gbEndDate").value("2026-11-18"));
  }

  @Test
  @DisplayName("글이 적어 같은 글이 여러 규칙의 1위여도 한 글은 한 칸에만 들어간다")
  void noRepeatAcrossSlots() throws Exception {
    setGbEndDate(save(Board.GB, 1, 3, 500, 0), TODAY.plusDays(3));
    save(Board.GB, 2, 4, 100, 0);
    save(Board.IC, 3, 2, 900, 50);
    save(Board.IC, 4, 3, 100, 10);

    mockMvc
        .perform(get(URL))
        .andExpect(status().isOk())
        .andExpect(
            jsonPath("$.slots[*].slot", contains("FEATURE", "TALL", "DEADLINE", "IC_REPLIES")))
        // 마감 띠의 글 1은 큰 칸에 들어갔으므로 마감 띠는 14일 공동구매 조회 중 남은 글(2위)을 사진 칸으로 쓴다
        .andExpect(jsonPath("$.slots[*].post.title", contains("글 1", "글 3", "글 2", "글 4")))
        .andExpect(jsonPath("$.slots[*].deadline", contains(false, false, false, false)))
        .andExpect(jsonPath("$.slots[*].rank", contains(1, 1, 2, 2)));
  }

  @Test
  @DisplayName("창 안에 후보가 없으면 30일 → 90일 → 전체로 넓히고, 전체면 windowDays는 null")
  void widensWindow() throws Exception {
    save(Board.GB, 1, 20, 100, 0);
    mockMvc
        .perform(get(URL))
        .andExpect(jsonPath("$.slots[0].slot").value("FEATURE"))
        .andExpect(jsonPath("$.slots[0].post.title").value("글 1"))
        .andExpect(jsonPath("$.slots[0].windowDays").value(30))
        .andExpect(jsonPath("$.slots[0].rank").value(1));

    postRepository.deleteAll();
    save(Board.GB, 2, 90, 100, 0);
    mockMvc
        .perform(get(URL))
        .andExpect(jsonPath("$.slots[0].slot").value("FEATURE"))
        .andExpect(jsonPath("$.slots[0].post.title").value("글 2"))
        .andExpect(jsonPath("$.slots[0].windowDays").value(90));

    postRepository.deleteAll();
    save(Board.GB, 3, 200, 100, 0);
    mockMvc
        .perform(get(URL))
        .andExpect(jsonPath("$.slots[0].slot").value("FEATURE"))
        .andExpect(jsonPath("$.slots[0].post.title").value("글 3"))
        .andExpect(jsonPath("$.slots[0].windowDays").value(nullValue()))
        .andExpect(jsonPath("$.slots[0].rank").value(1));
  }

  @Test
  @DisplayName("마감이 가장 가까운 글이 앞 칸에 들어갔으면 마감 띠는 그다음으로 마감이 가까운 글을 쓴다")
  void deadlineSkipsUsedPost() throws Exception {
    // 큰 칸(7일 공동구매 조회 1위)이면서 마감도 가장 가깝다
    setGbEndDate(save(Board.GB, 1, 3, 500, 0), TODAY.plusDays(2));
    setGbEndDate(save(Board.GB, 2, 5, 100, 0), TODAY.plusDays(6));

    mockMvc
        .perform(get(URL))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.slots[*].slot", contains("FEATURE", "DEADLINE")))
        .andExpect(jsonPath("$.slots[*].post.title", contains("글 1", "글 2")))
        .andExpect(jsonPath("$.slots[1].deadline").value(true))
        .andExpect(jsonPath("$.slots[1].windowDays").value(nullValue()))
        .andExpect(jsonPath("$.slots[1].rank").value(nullValue()));
  }

  @Test
  @DisplayName("마감일이 같으면 조회수가 많은 글, 조회수도 같으면 글 번호가 큰 글을 마감 띠에 쓴다")
  void deadlineTieBreaksByViewsThenTopicId() throws Exception {
    // 큰 칸(7일 공동구매 조회 1위). 마감이 없어 마감 띠 후보가 아니다
    save(Board.GB, 1, 3, 900, 0);
    setGbEndDate(save(Board.GB, 2, 3, 300, 0), TODAY.plusDays(3));
    setGbEndDate(save(Board.GB, 3, 3, 300, 0), TODAY.plusDays(3));
    setGbEndDate(save(Board.GB, 4, 3, 100, 0), TODAY.plusDays(3));

    mockMvc
        .perform(get(URL))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.slots[1].slot").value("DEADLINE"))
        .andExpect(jsonPath("$.slots[1].deadline").value(true))
        .andExpect(jsonPath("$.slots[1].post.title").value("글 3"));
  }

  @Test
  @DisplayName("마감 띠는 작성일을 묻지 않아, 오래전에 올라온 공동구매도 마감이 남았으면 쓴다")
  void deadlineIgnoresPostedAt() throws Exception {
    save(Board.GB, 1, 3, 500, 0);
    setGbEndDate(save(Board.GB, 2, 200, 10, 0), TODAY.plusDays(2));

    mockMvc
        .perform(get(URL))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.slots[*].slot", contains("FEATURE", "DEADLINE")))
        .andExpect(jsonPath("$.slots[*].post.title", contains("글 1", "글 2")))
        .andExpect(jsonPath("$.slots[1].deadline").value(true))
        .andExpect(jsonPath("$.slots[1].windowDays").value(nullValue()));
  }

  @Test
  @DisplayName("오늘 이후 마감인 공동구매가 없으면 마감 띠는 14일 공동구매 조회 중 남은 첫 글을 사진 칸으로 쓴다")
  void deadlineFallsBackToPhoto() throws Exception {
    setGbEndDate(save(Board.GB, 1, 3, 500, 0), TODAY.minusDays(1));
    // 14일 창 안에는 큰 칸 글뿐이라 30일로 넓힌다
    setGbEndDate(save(Board.GB, 2, 20, 300, 0), TODAY.minusDays(5));

    mockMvc
        .perform(get(URL))
        .andExpect(jsonPath("$.slots[*].slot", contains("FEATURE", "DEADLINE")))
        .andExpect(jsonPath("$.slots[*].post.title", contains("글 1", "글 2")))
        .andExpect(jsonPath("$.slots[1].deadline").value(false))
        .andExpect(jsonPath("$.slots[1].windowDays").value(30))
        .andExpect(jsonPath("$.slots[1].rank").value(2));

    // 14일 공동구매 조회 1위가 큰 칸 글과 다르면 그 1위를 쓴다
    postRepository.deleteAll();
    save(Board.GB, 3, 3, 100, 0);
    save(Board.GB, 4, 10, 500, 0);

    mockMvc
        .perform(get(URL))
        .andExpect(jsonPath("$.slots[*].slot", contains("FEATURE", "DEADLINE")))
        .andExpect(jsonPath("$.slots[*].post.title", contains("글 3", "글 4")))
        .andExpect(jsonPath("$.slots[1].deadline").value(false))
        .andExpect(jsonPath("$.slots[1].windowDays").value(14))
        .andExpect(jsonPath("$.slots[1].rank").value(1));
  }

  @Test
  @DisplayName("마감 띠는 한국 날짜로 오늘 마감까지 넣고, 한국 자정(UTC 15:00)이 지나면 빼고 today도 다음 날이 된다")
  void deadlineIncludesToday() throws Exception {
    clock.set(Instant.parse("2026-11-13T14:59:59Z"));
    save(Board.GB, 1, 3, 900, 0);
    setGbEndDate(save(Board.GB, 2, 3, 100, 0), LocalDate.of(2026, 11, 13));

    mockMvc
        .perform(get(URL))
        .andExpect(jsonPath("$.today").value("2026-11-13"))
        .andExpect(jsonPath("$.slots[*].slot", contains("FEATURE", "DEADLINE")))
        .andExpect(jsonPath("$.slots[1].post.title").value("글 2"))
        .andExpect(jsonPath("$.slots[1].deadline").value(true));

    clock.set(Instant.parse("2026-11-13T15:00:00Z"));
    mockMvc
        .perform(get(URL))
        .andExpect(jsonPath("$.today").value("2026-11-14"))
        .andExpect(jsonPath("$.slots[*].slot", contains("FEATURE", "DEADLINE")))
        .andExpect(jsonPath("$.slots[1].post.title").value("글 2"))
        .andExpect(jsonPath("$.slots[1].deadline").value(false));
  }

  @Test
  @DisplayName("숨김 글, 틀 글, 대표 이미지 없는 글은 마감 띠를 포함해 어떤 칸에도 들어가지 않는다")
  void excludesNonCandidates() throws Exception {
    // 후보라면 칸마다 1위가 되도록 조회수·댓글을 크게 주고, 공동구매 글에는 가장 가까운 마감도 넣는다
    for (Board board : Board.values()) {
      long base = board == Board.GB ? 10 : 20;
      Post hidden = newPost(board, base + 1, 2, 9000, 900);
      hidden.setHidden(true);
      Post skeleton =
          Post.createSkeleton(
              board,
              base + 2,
              "글 " + (base + 2),
              "maker",
              9000,
              900,
              clock.instant(),
              "https://geekhack.org/index.php?topic=" + (base + 2) + ".0");
      // 작성일·대표 이미지가 있어 본문을 읽지 않았다는 것만 후보 조건에 걸린다
      skeleton.setPostedAt(clock.instant().minus(Duration.ofDays(2)));
      List<Post> excluded =
          List.of(
              saveWithRepresentative(hidden),
              saveWithRepresentative(skeleton),
              postRepository.saveAndFlush(newPost(board, base + 3, 2, 9000, 900)));
      if (board == Board.GB) {
        excluded.forEach(post -> setGbEndDate(post, TODAY.plusDays(3)));
      }
    }
    save(Board.GB, 1, 3, 100, 0);
    save(Board.IC, 2, 3, 100, 0);

    mockMvc
        .perform(get(URL))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.slots[*].slot", contains("FEATURE", "TALL")))
        .andExpect(jsonPath("$.slots[*].post.title", contains("글 1", "글 2")));
  }

  @Test
  @DisplayName("고를 글이 없는 칸은 slots에서 빠지고, 글이 하나도 없으면 slots가 빈다")
  void omitsEmptySlots() throws Exception {
    mockMvc
        .perform(get(URL))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.slots", hasSize(0)))
        .andExpect(jsonPath("$.newIcCount").value(0))
        .andExpect(jsonPath("$.newGbCount").value(0))
        .andExpect(jsonPath("$.endingSoonGbCount").value(0));

    save(Board.IC, 1, 2, 900, 10);
    save(Board.IC, 2, 3, 100, 5);

    mockMvc
        .perform(get(URL))
        .andExpect(jsonPath("$.slots[*].slot", contains("TALL", "IC_REPLIES")))
        .andExpect(jsonPath("$.slots[*].post.title", contains("글 1", "글 2")));
  }

  @Test
  @DisplayName("날짜 줄: 최근 7일 새 IC·공동구매 수와 오늘부터 14일 안(양끝 포함)에 마감하는 공동구매 수. 대표 이미지는 묻지 않는다")
  void countsDateline() throws Exception {
    save(Board.IC, 1, 1, 0, 0);
    save(Board.IC, 2, 3, 0, 0);
    save(Board.IC, 3, 6, 0, 0);
    save(Board.GB, 4, 2, 0, 0);
    // 7일 경계: 7일에서 1시간 모자란 글은 세고, 1시간 넘은 글은 세지 않는다
    Post justInside = newPost(Board.IC, 12, 0, 0, 0);
    justInside.setPostedAt(clock.instant().minus(Duration.ofDays(7).minusHours(1)));
    saveWithRepresentative(justInside);
    Post justOutside = newPost(Board.IC, 13, 0, 0, 0);
    justOutside.setPostedAt(clock.instant().minus(Duration.ofDays(7).plusHours(1)));
    saveWithRepresentative(justOutside);
    // 대표 이미지가 없는 글도 센다
    postRepository.saveAndFlush(newPost(Board.IC, 14, 2, 0, 0));
    setGbEndDate(postRepository.saveAndFlush(newPost(Board.GB, 15, 8, 0, 0)), TODAY.plusDays(3));
    // 마감을 넣은 글은 모두 8일 전 작성이라 새 글 수에 들지 않는다
    setGbEndDate(save(Board.GB, 5, 8, 0, 0), TODAY);
    setGbEndDate(save(Board.GB, 6, 8, 0, 0), TODAY.plusDays(14));
    setGbEndDate(save(Board.GB, 7, 8, 0, 0), TODAY.minusDays(1));
    setGbEndDate(save(Board.GB, 8, 8, 0, 0), TODAY.plusDays(15));
    // GB에서 IC로 옮겨져 예전 기간이 남은 글
    setGbEndDate(save(Board.IC, 9, 8, 0, 0), TODAY.plusDays(3));
    // 숨김 글은 세지 않는다
    Post hiddenIc = newPost(Board.IC, 10, 1, 0, 0);
    hiddenIc.setHidden(true);
    postRepository.saveAndFlush(hiddenIc);
    Post hiddenGb = newPost(Board.GB, 11, 8, 0, 0);
    hiddenGb.setHidden(true);
    setGbEndDate(postRepository.saveAndFlush(hiddenGb), TODAY.plusDays(3));

    mockMvc
        .perform(get(URL))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.newIcCount").value(5))
        .andExpect(jsonPath("$.newGbCount").value(1))
        .andExpect(jsonPath("$.endingSoonGbCount").value(3));
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

package com.keycheck.post.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.keycheck.TestcontainersConfiguration;
import com.keycheck.crawl.client.GeekhackClient;
import com.keycheck.crawl.client.RequestGate;
import com.keycheck.post.domain.Board;
import com.keycheck.post.domain.Category;
import com.keycheck.post.domain.Post;
import com.keycheck.post.domain.SummaryStatus;
import com.keycheck.post.repository.PostRepository;
import com.keycheck.post.service.PostQueryService;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
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
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.servlet.MockMvc;

/** RSS 피드 글 목록은 사이트맵과 같은 글을 요약 저장 시각 순으로 최대 50건 준다 (수집 기간 경계 since-topic-id = 1000) */
@SpringBootTest(properties = "keycheck.collect.since-topic-id=1000")
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class PostFeedApiTest {
  private static final Instant CHECKED_AT = Instant.parse("2026-11-01T00:00:00Z");

  @Autowired private MockMvc mockMvc;
  @Autowired private PostRepository postRepository;
  @Autowired private JdbcTemplate jdbcTemplate;

  // 실제 geekhack을 호출하지 않는다
  @MockitoBean private GeekhackClient client;
  @MockitoBean private RequestGate gate;

  @BeforeEach
  void setUp() {
    postRepository.deleteAll();
  }

  @AfterEach
  void noGeekhackCalls() {
    verifyNoInteractions(client, gate);
  }

  /** 피드에 들어가는 글(본문을 읽었고 요약이 끝났고 요약 줄이 있다)을 만들고 customize로 바꾼다. 저장하지 않는다. */
  private Post build(long topicId, Consumer<Post> customize) {
    Post post =
        Post.create(
            Board.IC,
            topicId,
            "글 " + topicId,
            "maker",
            Category.KEYBOARD,
            Instant.parse("2026-10-01T00:00:00Z"),
            100,
            5,
            CHECKED_AT,
            "https://geekhack.org/index.php?topic=" + topicId + ".0",
            "본문");
    post.setSummaryStatus(SummaryStatus.DONE);
    post.setSummaryLines(List.of("요약 줄"));
    customize.accept(post);
    return post;
  }

  private Post save(long topicId, Consumer<Post> customize) {
    return postRepository.saveAndFlush(build(topicId, customize));
  }

  private Post save(long topicId, Instant summarizedAt) {
    Post post = save(topicId, p -> {});
    jdbcTemplate.update(
        "update post set summarized_at = ? where id = ?",
        Timestamp.from(summarizedAt),
        post.getId());
    return post;
  }

  @Test
  @DisplayName("글마다 게시판·제목·상품 이름·분류·요약 줄 전체·작성일·요약 저장 시각을 준다")
  void returnsFeedItemShape() throws Exception {
    Post post =
        save(
            1001,
            p -> {
              p.setBoard(Board.GB);
              p.setCategory(Category.KEYCAP);
              p.setSummaryLines(List.of("첫 줄이에요.", "둘째 줄이에요."));
            });
    jdbcTemplate.update(
        "update post set product_name = 'GMK Nightshift', summarized_at = ? where id = ?",
        Timestamp.from(Instant.parse("2026-11-03T04:05:06Z")),
        post.getId());
    Post unnamed = save(1002, p -> {});

    mockMvc
        .perform(get("/api/posts/feed"))
        .andExpect(status().isOk())
        .andExpect(
            content()
                .json(
                    """
                    [
                      {"id": %d, "board": "GB", "title": "글 1001", "productName": "GMK Nightshift",
                       "category": "KEYCAP", "summaryLines": ["첫 줄이에요.", "둘째 줄이에요."],
                       "postedAt": "2026-10-01T00:00:00Z", "summarizedAt": "2026-11-03T04:05:06Z"},
                      {"id": %d, "board": "IC", "title": "글 1002", "productName": null,
                       "category": "KEYBOARD", "summaryLines": ["요약 줄"],
                       "postedAt": "2026-10-01T00:00:00Z", "summarizedAt": null}
                    ]
                    """
                        .formatted(post.getId(), unnamed.getId()),
                    JsonCompareMode.STRICT));
  }

  @Test
  @DisplayName("숨긴 글, 수집 기간 이전 글, 틀 글, 요약이 끝나지 않은 글, 요약 줄이 없는 글은 빠진다")
  void excludesPostsNotToIndex() throws Exception {
    save(1000, post -> {});
    save(999, post -> {});
    save(1001, post -> post.setHidden(true));
    save(1002, post -> post.setSummaryStatus(SummaryStatus.PENDING));
    save(1003, post -> post.setSummaryStatus(SummaryStatus.FAILED));
    save(1004, post -> post.setSummaryLines(List.of()));
    save(1005, post -> post.setSummaryLines(null));
    // 본문을 읽지 않은 틀 글은 요약 완료로 두어도 빠진다
    Post skeleton =
        Post.createSkeleton(
            Board.IC,
            1006L,
            "글 1006",
            "maker",
            100,
            5,
            CHECKED_AT,
            "https://geekhack.org/index.php?topic=1006.0");
    skeleton.setSummaryStatus(SummaryStatus.DONE);
    skeleton.setSummaryLines(List.of("요약 줄"));
    postRepository.saveAndFlush(skeleton);

    mockMvc
        .perform(get("/api/posts/feed"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[*].title", contains("글 1000")));
  }

  @Test
  @DisplayName("요약 저장 시각 내림차순으로 주고, 시각을 모르는 글은 뒤에 두며, 같으면 글 번호 내림차순이다")
  void ordersBySummarizedAtDescNullsLast() throws Exception {
    Instant earlier = Instant.parse("2026-11-02T00:00:00Z");
    Instant later = Instant.parse("2026-11-04T00:00:00Z");
    save(1001, later);
    save(1002, earlier);
    save(1003, post -> {});
    save(1004, post -> {});
    save(1005, later);

    mockMvc
        .perform(get("/api/posts/feed"))
        .andExpect(status().isOk())
        .andExpect(
            jsonPath("$[*].title", contains("글 1005", "글 1001", "글 1002", "글 1004", "글 1003")));
  }

  @Test
  @DisplayName("최대 50건만 주고, 순서상 뒤의 글이 빠진다")
  void capsAtFeedSize() throws Exception {
    assertThat(PostQueryService.FEED_SIZE).isEqualTo(50);
    List<Post> posts = new ArrayList<>();
    for (long topicId = 1001; topicId <= 1001 + PostQueryService.FEED_SIZE; topicId++) {
      posts.add(build(topicId, post -> {}));
    }
    postRepository.saveAllAndFlush(posts);

    // 모두 요약 저장 시각을 모르므로 글 번호 내림차순이고, 가장 작은 글 번호(1001)가 빠진다
    mockMvc
        .perform(get("/api/posts/feed"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$", hasSize(PostQueryService.FEED_SIZE)))
        .andExpect(jsonPath("$[0].title").value("글 " + (1000 + PostQueryService.FEED_SIZE + 1)))
        .andExpect(jsonPath("$[*].title", not(hasItem("글 1001"))));
  }
}

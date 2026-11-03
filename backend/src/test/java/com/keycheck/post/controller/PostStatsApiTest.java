package com.keycheck.post.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.keycheck.MutableClock;
import com.keycheck.TestcontainersConfiguration;
import com.keycheck.crawl.client.GeekhackClient;
import com.keycheck.crawl.client.RequestGate;
import com.keycheck.post.domain.Category;
import com.keycheck.post.domain.Post;
import com.keycheck.post.repository.PostRepository;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
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

@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, PostStatsApiTest.ClockConfig.class})
class PostStatsApiTest {
  private static final long TOPIC_ID = 2000L;

  @Autowired private MockMvc mockMvc;
  @Autowired private PostRepository postRepository;
  @Autowired private MutableClock clock;
  @Autowired private JdbcTemplate jdbcTemplate;

  // 실제 geekhack을 호출하지 않는다. 파서와 DB는 실제 것을 쓴다.
  @MockitoBean private GeekhackClient client;
  @MockitoBean private RequestGate gate;

  /** 저장값: 조회수 100, 댓글 5. 픽스처를 읽으면 조회수 512, 댓글 2가 된다. */
  private String lastPage;

  @BeforeEach
  void setUp() throws IOException {
    postRepository.deleteAll();
    try (InputStream in = getClass().getResourceAsStream("/fixtures/topic-attachment.html")) {
      lastPage = new String(in.readAllBytes(), StandardCharsets.UTF_8);
    }
    when(gate.tryAcquire()).thenReturn(true);
    when(client.fetchTopicLastPage(TOPIC_ID)).thenReturn(lastPage);
  }

  private Post savePostCheckedAgo(Duration ago) {
    return postRepository.saveAndFlush(
        Post.create(
            TOPIC_ID,
            "글",
            "maker",
            Category.KEYBOARD,
            Instant.parse("2026-10-01T00:00:00Z"),
            100,
            5,
            clock.instant().minus(ago),
            "https://geekhack.test/index.php?topic=2000.0",
            "본문"));
  }

  private void assertStoredValuesReturned(Post post) throws Exception {
    Instant checkedAt = post.getStatsCheckedAt();
    mockMvc
        .perform(get("/api/posts/" + post.getId() + "/stats"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.views").value(100))
        .andExpect(jsonPath("$.replies").value(5))
        .andExpect(jsonPath("$.statsCheckedAt").value(checkedAt.toString()));
    assertThat(postRepository.findById(post.getId()).orElseThrow().getViews()).isEqualTo(100);
  }

  @Test
  @DisplayName("확인한 지 1시간 이내면 저장값을 돌려주고 요청 간격도 쓰지 않는다")
  void freshStatsAreReturnedWithoutRequest() throws Exception {
    Post post = savePostCheckedAgo(Duration.ofHours(1));

    assertStoredValuesReturned(post);

    verify(gate, never()).tryAcquire();
    verify(client, never()).fetchTopicLastPage(anyLong());
  }

  @Test
  @DisplayName("1시간이 지났으면 geekhack 글 번호로 마지막 페이지를 읽어 새 값을 저장하고 돌려준다")
  void staleStatsAreRefreshed() throws Exception {
    Post post = savePostCheckedAgo(Duration.ofHours(1).plusSeconds(1));

    mockMvc
        .perform(get("/api/posts/" + post.getId() + "/stats"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.views").value(512))
        .andExpect(jsonPath("$.replies").value(2))
        .andExpect(jsonPath("$.statsCheckedAt").value(clock.instant().toString()));

    verify(client).fetchTopicLastPage(TOPIC_ID);
    Post saved = postRepository.findById(post.getId()).orElseThrow();
    assertThat(saved.getViews()).isEqualTo(512);
    assertThat(saved.getReplies()).isEqualTo(2);
    assertThat(saved.getStatsCheckedAt()).isEqualTo(clock.instant());
  }

  @Test
  @DisplayName("geekhack 응답을 기다리는 동안 바뀐 다른 값(숨김·제목)은 덮어쓰지 않는다")
  void refreshKeepsConcurrentChanges() throws Exception {
    Post post = savePostCheckedAgo(Duration.ofHours(2));
    when(client.fetchTopicLastPage(TOPIC_ID))
        .thenAnswer(
            invocation -> {
              // 기다리는 동안 정기 수집이 지운 글을 숨긴 상황
              jdbcTemplate.update(
                  "update post set title = 'deleted', hidden = true where id = ?", post.getId());
              return lastPage;
            });

    mockMvc
        .perform(get("/api/posts/" + post.getId() + "/stats"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.views").value(512));

    Post saved = postRepository.findById(post.getId()).orElseThrow();
    assertThat(saved.getTitle()).isEqualTo("deleted");
    assertThat(saved.isHidden()).isTrue();
    assertThat(saved.getViews()).isEqualTo(512);
    assertThat(saved.getReplies()).isEqualTo(2);
    assertThat(saved.getStatsCheckedAt()).isEqualTo(clock.instant());
  }

  @Test
  @DisplayName("요청 간격이 차지 않았으면 요청하지 않고 저장값을 돌려준다")
  void gateClosedReturnsStoredValues() throws Exception {
    when(gate.tryAcquire()).thenReturn(false);
    Post post = savePostCheckedAgo(Duration.ofHours(2));

    assertStoredValuesReturned(post);

    verify(client, never()).fetchTopicLastPage(anyLong());
  }

  @Test
  @DisplayName("요청이 실패하면 저장값을 돌려주고 확인 시각은 그대로 둔다")
  void requestFailureReturnsStoredValues() throws Exception {
    when(client.fetchTopicLastPage(TOPIC_ID)).thenThrow(new IllegalStateException("요청 실패"));
    Post post = savePostCheckedAgo(Duration.ofHours(2));

    assertStoredValuesReturned(post);

    assertThat(postRepository.findById(post.getId()).orElseThrow().getStatsCheckedAt())
        .isEqualTo(post.getStatsCheckedAt());
  }

  @Test
  @DisplayName("페이지를 해석하지 못하면 저장값을 돌려주고 확인 시각은 그대로 둔다")
  void parseFailureReturnsStoredValues() throws Exception {
    when(client.fetchTopicLastPage(TOPIC_ID)).thenReturn("<html>The topic is missing</html>");
    Post post = savePostCheckedAgo(Duration.ofHours(2));

    assertStoredValuesReturned(post);

    assertThat(postRepository.findById(post.getId()).orElseThrow().getStatsCheckedAt())
        .isEqualTo(post.getStatsCheckedAt());
  }

  @Test
  @DisplayName("숨김 글과 없는 글은 404이고 요청하지 않는다")
  void hiddenOrMissingIs404() throws Exception {
    Post post = savePostCheckedAgo(Duration.ofHours(2));
    post.setHidden(true);
    postRepository.saveAndFlush(post);

    mockMvc.perform(get("/api/posts/" + post.getId() + "/stats")).andExpect(status().isNotFound());
    mockMvc.perform(get("/api/posts/999999/stats")).andExpect(status().isNotFound());

    verify(client, never()).fetchTopicLastPage(anyLong());
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

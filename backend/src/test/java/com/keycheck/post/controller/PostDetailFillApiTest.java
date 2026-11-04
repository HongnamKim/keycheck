package com.keycheck.post.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
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
import com.keycheck.post.domain.Board;
import com.keycheck.post.domain.Category;
import com.keycheck.post.domain.Post;
import com.keycheck.post.repository.PostImageRepository;
import com.keycheck.post.repository.PostRepository;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.TimeUnit;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** 틀 글(목록에서만 읽은 글)의 상세를 열면 geekhack 글 첫 페이지를 읽어 채운다 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, PostDetailFillApiTest.ClockConfig.class})
class PostDetailFillApiTest {
  private static final long TOPIC_ID = 3000L;

  @Autowired private MockMvc mockMvc;
  @Autowired private PostRepository postRepository;
  @Autowired private PostImageRepository postImageRepository;
  @Autowired private MutableClock clock;

  // 실제 geekhack을 호출하지 않는다. 파서와 DB는 실제 것을 쓴다.
  @MockitoBean private GeekhackClient client;
  @MockitoBean private RequestGate gate;

  /** 본문 "Test Board A small board — made up.", 조회수 37883, 이미지 3개(첫째는 이모티콘) */
  private String topicPage;

  @BeforeEach
  void setUp() throws IOException {
    postRepository.deleteAll();
    try (InputStream in = getClass().getResourceAsStream("/fixtures/topic-external.html")) {
      topicPage = new String(in.readAllBytes(), StandardCharsets.UTF_8);
    }
    when(gate.tryAcquire(any(Duration.class))).thenReturn(true);
    when(client.fetchTopic(TOPIC_ID)).thenReturn(topicPage);
  }

  /** 틀 글은 미분류로 저장된다 */
  private Post saveSkeleton() {
    return postRepository.saveAndFlush(
        Post.createSkeleton(
            Board.IC,
            TOPIC_ID,
            "[IC] Test Board",
            "bob",
            100,
            5,
            Instant.parse("2026-10-30T00:00:00Z"),
            "https://geekhack.test/index.php?topic=3000.0"));
  }

  private void assertSkeletonReturned(Post post) throws Exception {
    mockMvc
        .perform(get("/api/posts/" + post.getId()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.detailLoaded").value(false))
        .andExpect(jsonPath("$.category").value("UNCLASSIFIED"))
        .andExpect(jsonPath("$.title").value("[IC] Test Board"))
        .andExpect(jsonPath("$.representativeImageUrl").value(nullValue()))
        .andExpect(jsonPath("$.imageUrls", hasSize(0)))
        .andExpect(jsonPath("$.summaryStatus").value("PENDING"))
        .andExpect(jsonPath("$.summaryLines", hasSize(0)))
        .andExpect(jsonPath("$.postedAt").value(nullValue()))
        .andExpect(jsonPath("$.views").value(100));
    assertThat(postRepository.findById(post.getId()).orElseThrow().isDetailLoaded()).isFalse();
  }

  @Test
  @DisplayName("틀 글이면 글 첫 페이지를 읽어 채우고(미분류였던 분류도 본문으로 정한다) 채운 결과를 돌려준다")
  void fillsSkeletonAndReturnsIt() throws Exception {
    Post post = saveSkeleton();

    mockMvc
        .perform(get("/api/posts/" + post.getId()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.detailLoaded").value(true))
        .andExpect(jsonPath("$.category").value("KEYBOARD"))
        .andExpect(jsonPath("$.representativeImageUrl").value("https://i.imgur.com/first.jpg"))
        .andExpect(
            jsonPath(
                "$.imageUrls",
                contains("https://i.imgur.com/first.jpg", "https://i.imgur.com/second.png")))
        // 요약은 SummaryWorker가 나중에 만든다
        .andExpect(jsonPath("$.summaryStatus").value("PENDING"))
        .andExpect(jsonPath("$.summaryLines", hasSize(0)))
        .andExpect(jsonPath("$.postedAt").value("2026-10-13T13:29:35Z"))
        .andExpect(jsonPath("$.views").value(37883))
        .andExpect(jsonPath("$.statsCheckedAt").value("2026-10-30T00:00:00Z"));

    verify(gate).tryAcquire(Duration.ofSeconds(5));
    Post saved = postRepository.findById(post.getId()).orElseThrow();
    assertThat(saved.getDetailFetchedAt()).isEqualTo(clock.instant());
    assertThat(saved.getBodyText()).isEqualTo("Test Board A small board — made up.");
    assertThat(postImageRepository.findAll()).hasSize(3);
  }

  @Test
  @DisplayName("요청 간격을 얻지 못하면 요청하지 않고 틀 그대로 돌려준다")
  void gateClosedReturnsSkeleton() throws Exception {
    when(gate.tryAcquire(any(Duration.class))).thenReturn(false);
    Post post = saveSkeleton();

    assertSkeletonReturned(post);

    verify(client, never()).fetchTopic(anyLong());
  }

  @Test
  @DisplayName("요청이 실패하면 틀 그대로 돌려준다")
  void requestFailureReturnsSkeleton() throws Exception {
    when(client.fetchTopic(TOPIC_ID)).thenThrow(new IllegalStateException("요청 실패"));
    Post post = saveSkeleton();

    assertSkeletonReturned(post);
  }

  @Test
  @DisplayName("페이지를 해석하지 못하면 틀 그대로 돌려준다")
  void parseFailureReturnsSkeleton() throws Exception {
    when(client.fetchTopic(TOPIC_ID)).thenReturn("<html>The topic is missing</html>");
    Post post = saveSkeleton();

    assertSkeletonReturned(post);
  }

  @Test
  @DisplayName("이미 채워진 글은 geekhack을 요청하지 않는다")
  void loadedPostIsNotFetched() throws Exception {
    Post post =
        postRepository.saveAndFlush(
            Post.create(
                Board.IC,
                TOPIC_ID,
                "[IC] Test Board",
                "bob",
                Category.KEYBOARD,
                Instant.parse("2026-10-13T13:29:35Z"),
                100,
                5,
                Instant.parse("2026-10-30T00:00:00Z"),
                "https://geekhack.test/index.php?topic=3000.0",
                "본문"));

    mockMvc
        .perform(get("/api/posts/" + post.getId()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.detailLoaded").value(true));

    verify(gate, never()).tryAcquire(any(Duration.class));
    verify(client, never()).fetchTopic(anyLong());
  }

  @Test
  @DisplayName("숨김 틀 글은 404이고 요청하지 않는다")
  void hiddenSkeletonIs404() throws Exception {
    Post post = saveSkeleton();
    post.setHidden(true);
    postRepository.saveAndFlush(post);

    mockMvc.perform(get("/api/posts/" + post.getId())).andExpect(status().isNotFound());

    verify(client, never()).fetchTopic(anyLong());
  }

  @Test
  @DisplayName("두 명이 동시에 열어도 이미지는 한 번만 저장된다")
  void concurrentOpensSaveImagesOnce() throws Exception {
    Post post = saveSkeleton();
    // 두 요청이 모두 글 페이지를 받은 뒤에 저장하러 가게 한다
    CyclicBarrier bothFetched = new CyclicBarrier(2);
    when(client.fetchTopic(TOPIC_ID))
        .thenAnswer(
            invocation -> {
              bothFetched.await(10, TimeUnit.SECONDS);
              return topicPage;
            });

    CompletableFuture<Integer> first = CompletableFuture.supplyAsync(() -> open(post));
    CompletableFuture<Integer> second = CompletableFuture.supplyAsync(() -> open(post));

    assertThat(first.get(20, TimeUnit.SECONDS)).isEqualTo(200);
    assertThat(second.get(20, TimeUnit.SECONDS)).isEqualTo(200);
    assertThat(postImageRepository.findAll()).hasSize(3);
  }

  private int open(Post post) {
    try {
      return mockMvc
          .perform(get("/api/posts/" + post.getId()))
          .andReturn()
          .getResponse()
          .getStatus();
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
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

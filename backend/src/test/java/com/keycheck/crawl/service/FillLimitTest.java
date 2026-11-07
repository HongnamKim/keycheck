package com.keycheck.crawl.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.keycheck.TestcontainersConfiguration;
import com.keycheck.crawl.client.GeekhackClient;
import com.keycheck.crawl.client.RequestGate;
import com.keycheck.crawl.dto.PrefetchResult;
import com.keycheck.post.domain.Board;
import com.keycheck.post.domain.Post;
import com.keycheck.post.repository.PostRepository;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** 틀 글 채우기 동시 상한(keycheck.fill.max-concurrent). 상한을 1로 둔다. */
@SpringBootTest(properties = "keycheck.fill.max-concurrent=1")
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class FillLimitTest {
  private static final long FIRST_TOPIC_ID = 3000L;
  private static final long SECOND_TOPIC_ID = 3001L;

  @Autowired private MockMvc mockMvc;
  @Autowired private PostRepository postRepository;
  @Autowired private CrawlService crawlService;

  // 실제 geekhack을 호출하지 않는다. 파서와 DB는 실제 것을 쓴다.
  @MockitoBean private GeekhackClient client;
  @MockitoBean private RequestGate gate;

  @BeforeEach
  void setUp() throws IOException {
    postRepository.deleteAll();
    try (InputStream in = getClass().getResourceAsStream("/fixtures/topic-external.html")) {
      when(client.fetchTopic(anyLong()))
          .thenReturn(new String(in.readAllBytes(), StandardCharsets.UTF_8));
    }
  }

  private Post saveSkeleton(long topicId) {
    return postRepository.saveAndFlush(
        Post.createSkeleton(
            Board.IC,
            topicId,
            "[IC] Test Board " + topicId,
            "bob",
            100,
            5,
            Instant.parse("2026-10-30T00:00:00Z"),
            "https://geekhack.test/index.php?topic=" + topicId + ".0"));
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

  private boolean isDetailLoaded(Post post) {
    return postRepository.findById(post.getId()).orElseThrow().isDetailLoaded();
  }

  @Test
  @DisplayName("채우기가 상한만큼 진행 중이면 다른 틀 글은 기다리지 않고 틀 그대로 돌려주고(미리 읽기도 하지 않는다), 끝나면 다시 채운다")
  void fillConcurrencyCap() throws Exception {
    Post first = saveSkeleton(FIRST_TOPIC_ID);
    Post second = saveSkeleton(SECOND_TOPIC_ID);
    // 첫 채우기가 요청 간격을 기다리는 중인 상황을 만든다
    CountDownLatch waiting = new CountDownLatch(1);
    CountDownLatch release = new CountDownLatch(1);
    when(gate.tryAcquire(any(Duration.class)))
        .thenAnswer(
            invocation -> {
              waiting.countDown();
              return release.await(10, TimeUnit.SECONDS);
            });

    CompletableFuture<Integer> firstOpen = CompletableFuture.supplyAsync(() -> open(first));
    assertThat(waiting.await(10, TimeUnit.SECONDS)).isTrue();

    mockMvc
        .perform(get("/api/posts/" + second.getId()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.detailLoaded").value(false))
        .andExpect(jsonPath("$.title").value("[IC] Test Board 3001"));
    assertThat(crawlService.prefetchSkeleton(second)).isEqualTo(PrefetchResult.NO_SLOT);

    release.countDown();
    assertThat(firstOpen.get(20, TimeUnit.SECONDS)).isEqualTo(200);
    assertThat(isDetailLoaded(first)).isTrue();
    // 두 번째 글은 요청 간격도 기다리지 않았고 geekhack에 요청하지 않았다
    verify(gate, times(1)).tryAcquire(any(Duration.class));
    verify(gate, never()).tryAcquireIdle();
    verify(client, never()).fetchTopic(SECOND_TOPIC_ID);
    assertThat(isDetailLoaded(second)).isFalse();

    // 사용자 한 명이 틀 글 하나를 여는 경우는 채운다
    assertThat(open(second)).isEqualTo(200);
    assertThat(isDetailLoaded(second)).isTrue();
    verify(client).fetchTopic(SECOND_TOPIC_ID);
  }
}

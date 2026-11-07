package com.keycheck.crawl.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.keycheck.TestcontainersConfiguration;
import com.keycheck.crawl.client.RequestGate;
import com.keycheck.crawl.service.CrawlServiceTest.FakeGeekhackClient;
import com.keycheck.post.domain.Board;
import com.keycheck.post.domain.Post;
import com.keycheck.post.repository.PostRepository;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * 틀 글 미리 읽기. 수집 기간 경계는 since-topic-id = 1000. 가짜 클라이언트는 CrawlServiceTest의 것을 쓰고(픽스처 글 2000, 3000),
 * 빈 간격이 있는지는 테스트가 정한다.
 */
@SpringBootTest(
    properties = {
      "keycheck.geekhack.base-url=https://geekhack.test",
      "keycheck.collect.since-topic-id=1000"
    })
@Import({TestcontainersConfiguration.class, CrawlServiceTest.FakesConfig.class})
class PrefetchWorkerTest {
  @Autowired private PrefetchWorker worker;
  @Autowired private PostRepository postRepository;
  @Autowired private FakeGeekhackClient client;

  @MockitoBean private RequestGate gate;

  @BeforeEach
  void setUp() throws IOException {
    postRepository.deleteAll();
    client.reset();
    client.topics.put(2000L, read("/fixtures/topic-attachment.html"));
    client.topics.put(3000L, read("/fixtures/topic-external.html"));
    when(gate.tryAcquireIdle()).thenReturn(true);
  }

  private String read(String path) throws IOException {
    try (InputStream in = getClass().getResourceAsStream(path)) {
      return new String(in.readAllBytes(), StandardCharsets.UTF_8);
    }
  }

  private Post saveSkeleton(long topicId) {
    return postRepository.saveAndFlush(
        Post.createSkeleton(
            Board.IC,
            topicId,
            "[IC] 글 " + topicId,
            "maker",
            10,
            1,
            Instant.parse("2026-10-30T00:00:00Z"),
            "https://geekhack.test/index.php?topic=" + topicId + ".0"));
  }

  private Post post(long topicId) {
    return postRepository.findByTopicId(topicId).orElseThrow();
  }

  @Test
  @DisplayName("글 번호가 큰(최근) 틀 글부터 한 번에 하나씩 채우고, 미리 읽기로 채운 글로 표시한다")
  void fillsLargestTopicFirst() {
    saveSkeleton(2000);
    saveSkeleton(3000);

    assertThat(worker.prefetchNext()).isTrue();
    assertThat(post(3000).isDetailLoaded()).isTrue();
    assertThat(post(3000).isPrefetched()).isTrue();
    assertThat(post(2000).isDetailLoaded()).isFalse();

    assertThat(worker.prefetchNext()).isTrue();
    assertThat(post(2000).isDetailLoaded()).isTrue();

    assertThat(worker.prefetchNext()).isFalse();
    assertThat(client.fetchedTopics).containsExactly(3000L, 2000L);
  }

  @Test
  @DisplayName("빈 간격이 없으면 요청하지 않고 실패로 세지도 않는다")
  void skipsWhenNoIdleSlot() {
    when(gate.tryAcquireIdle()).thenReturn(false);
    saveSkeleton(3000);

    assertThat(worker.prefetchNext()).isTrue();

    assertThat(post(3000).isDetailLoaded()).isFalse();
    assertThat(post(3000).getPrefetchFailures()).isZero();
    assertThat(client.fetchedTopics).isEmpty();
  }

  @Test
  @DisplayName("읽지 못한 틀 글은 실패 횟수를 세고, MAX_FAILURES번 실패하면 더 미리 읽지 않는다")
  void countsFailureAndStopsAfterThree() {
    client.topics.remove(3000L);
    saveSkeleton(3000);

    for (int i = 0; i < PrefetchWorker.MAX_FAILURES; i++) {
      assertThat(worker.prefetchNext()).isTrue();
    }

    assertThat(post(3000).getPrefetchFailures()).isEqualTo(3);
    assertThat(post(3000).isDetailLoaded()).isFalse();
    assertThat(worker.prefetchNext()).isFalse();
    assertThat(client.fetchedTopics).containsExactly(3000L, 3000L, 3000L);
  }

  @Test
  @DisplayName("숨긴 틀 글과 수집 기간 경계 아래 틀 글은 미리 읽지 않고, 경계 글 번호 그 틀 글은 미리 읽는다")
  void skipsHiddenAndBeforeSince() throws IOException {
    Post hidden = saveSkeleton(3000);
    hidden.setHidden(true);
    postRepository.saveAndFlush(hidden);
    saveSkeleton(999);
    saveSkeleton(1000);
    client.topics.put(1000L, read("/fixtures/topic-attachment.html"));

    assertThat(worker.prefetchNext()).isTrue();
    assertThat(post(1000).isDetailLoaded()).isTrue();

    assertThat(worker.prefetchNext()).isFalse();
    assertThat(client.fetchedTopics).containsExactly(1000L);
  }
}

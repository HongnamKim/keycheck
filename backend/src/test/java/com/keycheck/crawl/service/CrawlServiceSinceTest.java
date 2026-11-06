package com.keycheck.crawl.service;

import static com.keycheck.crawl.service.CrawlServiceTest.key;
import static org.assertj.core.api.Assertions.assertThat;

import com.keycheck.BoardHtml;
import com.keycheck.TestcontainersConfiguration;
import com.keycheck.crawl.domain.BoardBackfill;
import com.keycheck.crawl.repository.BoardBackfillRepository;
import com.keycheck.crawl.service.CrawlServiceTest.FakeGeekhackClient;
import com.keycheck.post.domain.Board;
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
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 수집 기간 경계(since-topic-id = 2500)보다 작은 글은 정기 수집과 과거 글 확보에서 빠진다. 픽스처의 글 2개: 2000(경계 아래),
 * 3000. 가짜 클라이언트는 CrawlServiceTest의 것을 쓴다.
 */
@SpringBootTest(
    properties = {
      "keycheck.geekhack.min-interval=0s",
      "keycheck.geekhack.base-url=https://geekhack.test",
      "keycheck.collect.since-topic-id=2500"
    })
@Import({TestcontainersConfiguration.class, CrawlServiceTest.FakesConfig.class})
class CrawlServiceSinceTest {
  @Autowired private CrawlService crawlService;
  @Autowired private PostRepository postRepository;
  @Autowired private FakeGeekhackClient client;
  @Autowired private JdbcTemplate jdbcTemplate;
  @Autowired private BoardBackfillRepository backfillRepository;

  @BeforeEach
  void setUp() throws IOException {
    postRepository.deleteAll();
    jdbcTemplate.update(
        "update board_backfill set next_page = 0, floor_topic_id = null, exhausted = false");
    String board = read("/fixtures/board-list.html");
    client.reset();
    client.boardPages.put(key(Board.IC, 0), board);
    client.boardPages.put(key(Board.IC, 1), BoardHtml.page(1));
    client.boardPages.put(key(Board.GB, 0), BoardHtml.page(0));
    client.boardPages.put(key(Board.GB, 1), BoardHtml.page(1));
    client.firstPostPages.put(key(Board.IC, 0), board);
    client.topics.put(2000L, read("/fixtures/topic-attachment.html"));
    client.topics.put(3000L, read("/fixtures/topic-external.html"));
  }

  private String read(String path) throws IOException {
    try (InputStream in = getClass().getResourceAsStream(path)) {
      return new String(in.readAllBytes(), StandardCharsets.UTF_8);
    }
  }

  private Post post(long topicId) {
    return postRepository.findByTopicId(topicId).orElseThrow();
  }

  /** IC 게시판의 확보 상태 */
  private BoardBackfill backfill() {
    return backfillRepository.current(Board.IC);
  }

  @Test
  @DisplayName("정기 수집은 경계보다 작은 새 글을 저장하지 않고 글 페이지도 요청하지 않는다")
  void crawlSkipsNewTopicBeforeSince() {
    crawlService.crawl();

    assertThat(postRepository.findByTopicId(3000L)).isPresent();
    assertThat(postRepository.findByTopicId(2000L)).isEmpty();
    assertThat(client.fetchedTopics).containsExactly(3000L);
  }

  @Test
  @DisplayName("정기 수집은 경계 글 번호 그 글은 저장하고 바로 아래 글은 저장하지 않는다")
  void crawlKeepsTopicAtSince() throws IOException {
    client.boardPages.put(key(Board.GB, 0), BoardHtml.page(0, 2500, 2499));
    client.topics.put(2500L, read("/fixtures/topic-external.html"));

    crawlService.crawl();

    assertThat(post(2500).isDetailLoaded()).isTrue();
    assertThat(postRepository.findByTopicId(2499L)).isEmpty();
    assertThat(client.fetchedTopics).containsExactlyInAnyOrder(3000L, 2500L);
  }

  @Test
  @DisplayName("이미 저장된 경계 아래 틀 글은 목록 값만 갱신하고 글 페이지로 채우지 않는다")
  void crawlUpdatesExistingBeforeSinceWithoutFilling() {
    postRepository.saveAndFlush(
        Post.createSkeleton(
            Board.IC,
            2000L,
            "[IC] Old Title",
            "alice",
            10,
            0,
            Instant.parse("2026-10-01T00:00:00Z"),
            "https://geekhack.test/index.php?topic=2000.0"));

    crawlService.crawl();

    Post post = post(2000);
    assertThat(post.getViews()).isEqualTo(4347);
    assertThat(post.isDetailLoaded()).isFalse();
    assertThat(client.fetchedTopics).containsExactly(3000L);
  }

  @Test
  @DisplayName("과거 글 확보는 경계보다 작은 글을 틀 글로도 저장하지 않고, 경계에 닿은 게시판을 끝까지 받은 것으로 둔다")
  void backfillSavesNoSkeletonBeforeSince() {
    assertThat(crawlService.backfillNextPage(Board.IC, Duration.ZERO)).isTrue();

    assertThat(post(3000).isDetailLoaded()).isFalse();
    assertThat(postRepository.findByTopicId(2000L)).isEmpty();
    assertThat(backfill().isExhausted()).isTrue();
  }

  @Test
  @DisplayName("이미 경계 아래까지 받아 둔 게시판은 목록을 요청하지 않고 끝까지 받은 것으로 둔다")
  void backfillAlreadyBelowSinceExhaustsWithoutRequest() {
    jdbcTemplate.update(
        "update board_backfill set next_page = 1, floor_topic_id = 2400 where board = 'IC'");

    // 요청은 없지만 확보 상태를 바꿨으므로 true(목록 API가 상태를 다시 읽어 바로 끝낸다)
    assertThat(crawlService.backfillNextPage(Board.IC, Duration.ZERO)).isTrue();

    assertThat(backfill().isExhausted()).isTrue();
    assertThat(backfillRepository.current(Board.GB).isExhausted()).isFalse();
    assertThat(client.fetchedFirstPostPages).isEmpty();
  }
}

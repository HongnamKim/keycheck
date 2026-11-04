package com.keycheck.crawl.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.keycheck.TestcontainersConfiguration;
import com.keycheck.crawl.dto.BoardTopic;
import com.keycheck.crawl.dto.RawImage;
import com.keycheck.crawl.dto.TopicContent;
import com.keycheck.crawl.dto.TopicPage;
import com.keycheck.image.domain.ImageSource;
import com.keycheck.image.dto.Selection;
import com.keycheck.post.domain.Board;
import com.keycheck.post.domain.Category;
import com.keycheck.post.domain.Post;
import com.keycheck.post.domain.SummaryStatus;
import com.keycheck.post.repository.PostImageRepository;
import com.keycheck.post.repository.PostRepository;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class CrawlStoreTest {
  private static final Instant NOW = Instant.parse("2026-11-03T00:00:00Z");

  @Autowired private CrawlStore store;
  @Autowired private PostRepository postRepository;
  @Autowired private PostImageRepository postImageRepository;
  @Autowired private JdbcTemplate jdbcTemplate;
  @Autowired private PlatformTransactionManager transactionManager;

  private Post skeleton;

  @BeforeEach
  void setUp() {
    postRepository.deleteAll();
    skeleton =
        postRepository.saveAndFlush(
            Post.createSkeleton(
                Board.IC,
                2000L,
                "[IC] Board",
                "maker",
                10,
                1,
                Instant.parse("2026-11-01T00:00:00Z"),
                "https://geekhack.test/index.php?topic=2000.0"));
  }

  /** 이미지 2개(둘째가 대표), 키캡 분류 */
  private TopicContent content() {
    TopicPage page =
        new TopicPage(
            "본문",
            List.of(
                new RawImage("https://geekhack.test/Smileys/a.gif", ImageSource.EXTERNAL, 0),
                new RawImage("https://i.imgur.com/b.jpg", ImageSource.EXTERNAL, 1)),
            512,
            Instant.parse("2026-10-13T13:29:35Z"));
    return new TopicContent(
        page,
        Category.KEYCAP,
        new Selection(1, Arrays.asList("site-asset", null)));
  }

  private long imageCount() {
    return postImageRepository.findAll().size();
  }

  @Test
  @DisplayName("틀 글을 본문·작성일·조회수·분류·이미지로 채우고 요약 대기로 둔다")
  void fillsSkeleton() {
    assertThat(store.fillSkeleton(skeleton.getId(), content(), NOW)).isTrue();

    Post post = postRepository.findById(skeleton.getId()).orElseThrow();
    assertThat(post.isDetailLoaded()).isTrue();
    assertThat(post.getDetailFetchedAt()).isEqualTo(NOW);
    // 댓글 수는 목록에서 읽은 옛 값이므로 확인 시각도 그대로 둔다
    assertThat(post.getStatsCheckedAt()).isEqualTo(Instant.parse("2026-11-01T00:00:00Z"));
    assertThat(post.getBodyText()).isEqualTo("본문");
    assertThat(post.getPostedAt()).isEqualTo(Instant.parse("2026-10-13T13:29:35Z"));
    assertThat(post.getViews()).isEqualTo(512);
    assertThat(post.getCategory()).isEqualTo(Category.KEYCAP);
    assertThat(post.getSummaryStatus()).isEqualTo(SummaryStatus.PENDING);
    assertThat(post.getSummaryLines()).isNull();
    assertThat(postImageRepository.findById(post.getRepresentativeImageId()))
        .hasValueSatisfying(
            image -> assertThat(image.getUrl()).isEqualTo("https://i.imgur.com/b.jpg"));
    assertThat(imageCount()).isEqualTo(2);
  }

  @Test
  @DisplayName("이미 채워진 글은 다시 채우지 않는다")
  void doesNotFillTwice() {
    store.fillSkeleton(skeleton.getId(), content(), NOW);

    assertThat(store.fillSkeleton(skeleton.getId(), content(), NOW)).isFalse();

    assertThat(imageCount()).isEqualTo(2);
  }

  @Test
  @DisplayName("다른 요청이 같은 글을 채우는 중이면 끝날 때까지 기다렸다가, 채워졌으면 손대지 않는다")
  void waitsForConcurrentFillAndSkips() throws Exception {
    CountDownLatch locked = new CountDownLatch(1);
    CountDownLatch release = new CountDownLatch(1);
    TransactionTemplate tx = new TransactionTemplate(transactionManager);
    // 다른 요청이 행을 잠그고 채우는 상황
    CompletableFuture<Void> other =
        CompletableFuture.runAsync(
            () ->
                tx.executeWithoutResult(
                    status -> {
                      jdbcTemplate.queryForList(
                          "select id from post where id = ? for update", skeleton.getId());
                      locked.countDown();
                      await(release);
                      jdbcTemplate.update(
                          "update post set detail_fetched_at = now(), body_text = '다른 요청'"
                              + " where id = ?",
                          skeleton.getId());
                    }));
    assertThat(locked.await(10, TimeUnit.SECONDS)).isTrue();

    CompletableFuture<Boolean> fill =
        CompletableFuture.supplyAsync(() -> store.fillSkeleton(skeleton.getId(), content(), NOW));
    Thread.sleep(500);
    assertThat(fill).isNotDone();
    release.countDown();
    other.get(10, TimeUnit.SECONDS);

    assertThat(fill.get(10, TimeUnit.SECONDS)).isFalse();
    assertThat(postRepository.findById(skeleton.getId()).orElseThrow().getBodyText())
        .isEqualTo("다른 요청");
    assertThat(imageCount()).isZero();
  }

  @Test
  @DisplayName("목록 값을 갱신하는 동안 저장된 요약·분류는 덮어쓰지 않는다")
  void updateDoesNotOverwriteConcurrentSummary() {
    store.fillSkeleton(skeleton.getId(), content(), NOW);
    BoardTopic topic = new BoardTopic(2000L, "[IC] 새 제목", "maker", 3, 900, NOW);

    new TransactionTemplate(transactionManager)
        .executeWithoutResult(
            status -> {
              store.updateIfExists(Board.IC, topic, NOW);
              // 수집이 글을 읽은 뒤, 커밋 전에 요약 작업이 요약을 저장한 상황
              jdbcTemplate.update(
                  "update post set summary_status = 'DONE', summarized_by = 'claude',"
                      + " category = 'OTHER' where id = ?",
                  skeleton.getId());
            });

    Post post = postRepository.findById(skeleton.getId()).orElseThrow();
    assertThat(post.getTitle()).isEqualTo("[IC] 새 제목");
    assertThat(post.getViews()).isEqualTo(900);
    assertThat(post.getSummaryStatus()).isEqualTo(SummaryStatus.DONE);
    assertThat(post.getSummarizedBy()).isEqualTo("claude");
    assertThat(post.getCategory()).isEqualTo(Category.OTHER);
  }

  private static void await(CountDownLatch latch) {
    try {
      latch.await(10, TimeUnit.SECONDS);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }
}

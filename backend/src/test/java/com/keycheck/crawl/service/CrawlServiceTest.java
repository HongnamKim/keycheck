package com.keycheck.crawl.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import com.keycheck.BoardHtml;
import com.keycheck.MutableClock;
import com.keycheck.TestcontainersConfiguration;
import com.keycheck.crawl.client.GeekhackClient;
import com.keycheck.crawl.domain.BoardBackfill;
import com.keycheck.crawl.repository.BoardBackfillRepository;
import com.keycheck.post.domain.Board;
import com.keycheck.post.domain.Category;
import com.keycheck.post.domain.Post;
import com.keycheck.post.domain.PostImage;
import com.keycheck.post.domain.SummaryStatus;
import com.keycheck.post.repository.PostImageRepository;
import com.keycheck.post.repository.PostRepository;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.client.RestClient;

// 픽스처의 글 2개: 2000(첨부 이미지, 댓글 2개), 3000(외부 이미지)
@SpringBootTest(
    properties = {
      "keycheck.geekhack.min-interval=0s",
      "keycheck.geekhack.base-url=https://geekhack.test"
    })
@Import({TestcontainersConfiguration.class, CrawlServiceTest.FakesConfig.class})
class CrawlServiceTest {
  private static final String EMPTY_BOARD =
      "<div id=\"messageindex\"><table class=\"table_grid\"><tbody></tbody></table></div>";

  @Autowired private CrawlService crawlService;
  @Autowired private PostRepository postRepository;
  @Autowired private PostImageRepository postImageRepository;
  @Autowired private FakeGeekhackClient client;
  @Autowired private MutableClock clock;
  @Autowired private JdbcTemplate jdbcTemplate;
  @Autowired private BoardBackfillRepository backfillRepository;

  private String board;

  @BeforeEach
  void setUp() throws IOException {
    postRepository.deleteAll();
    setBackfill(0, null, false);
    board = read("/fixtures/board-list.html");
    client.reset();
    client.boardPages.put(key(Board.IC, 0), board);
    client.boardPages.put(key(Board.IC, 1), EMPTY_BOARD);
    client.boardPages.put(key(Board.GB, 0), EMPTY_BOARD);
    client.boardPages.put(key(Board.GB, 1), EMPTY_BOARD);
    client.firstPostPages.put(key(Board.IC, 0), board);
    client.topics.put(2000L, read("/fixtures/topic-attachment.html"));
    client.topics.put(3000L, read("/fixtures/topic-external.html"));
  }

  /** FakeGeekhackClient의 목록 키 */
  private static String key(Board board, int page) {
    return board + " " + page;
  }

  /** 픽스처의 쪽 번호 목록이 page쪽(0부터)을 가리키게 바꾼다 */
  private static String onPage(String html, int page) {
    return html.replace("[<strong>1</strong>]", "[<strong>" + (page + 1) + "</strong>]");
  }

  private String read(String path) throws IOException {
    try (InputStream in = getClass().getResourceAsStream(path)) {
      return new String(in.readAllBytes(), StandardCharsets.UTF_8);
    }
  }

  private Post post(long topicId) {
    return postRepository.findByTopicId(topicId).orElseThrow();
  }

  private void setBackfill(int nextPage, Long floorTopicId, boolean exhausted) {
    jdbcTemplate.update(
        "update board_backfill set next_page = ?, floor_topic_id = ?, exhausted = ?",
        nextPage,
        floorTopicId,
        exhausted);
  }

  /** IC 게시판의 확보 상태 */
  private BoardBackfill backfill() {
    return backfillRepository.current(Board.IC);
  }

  @Test
  @DisplayName("새 글은 목록과 글 페이지의 값을 합쳐 저장한다")
  void savesNewPost() {
    crawlService.crawl();

    Post post = post(3000);
    assertThat(post.getTitle()).isEqualTo("[IC] Multi Page Topic");
    assertThat(post.getAuthor()).isEqualTo("bob");
    assertThat(post.getViews()).isEqualTo(82388);
    assertThat(post.getReplies()).isEqualTo(78);
    assertThat(post.getPostedAt()).isEqualTo(Instant.parse("2026-10-13T13:29:35Z"));
    assertThat(post.getBodyText()).isEqualTo("Test Board A small board — made up.");
    assertThat(post.getOriginalUrl()).isEqualTo("https://geekhack.test/index.php?topic=3000.0");
    assertThat(post.getStatsCheckedAt()).isEqualTo(clock.instant());
    assertThat(post.getLastPostAt()).isEqualTo(Instant.parse("2026-11-01T01:00:00Z"));
  }

  @Test
  @DisplayName("새 글을 제목과 본문으로 분류한다")
  void classifiesNewPost() {
    client.boardPages.put(key(Board.IC, 0), board.replace("[IC] Multi Page Topic", "[IC] GMK Ocean"));

    crawlService.crawl();

    assertThat(post(3000).getCategory()).isEqualTo(Category.KEYCAP);
    assertThat(post(2000).getCategory()).isEqualTo(Category.KEYBOARD);
  }

  @Test
  @DisplayName("이미지와 제외 사유를 저장하고 첫 후보를 대표 이미지로 정한다")
  void savesImagesAndRepresentative() {
    crawlService.crawl();

    Post post = post(3000);
    List<PostImage> images =
        postImageRepository.findAll().stream()
            .filter(image -> image.getUrl().contains("first") || image.getUrl().contains("blank"))
            .toList();
    assertThat(images)
        .extracting(PostImage::getUrl, PostImage::getExcludedReason)
        .containsExactlyInAnyOrder(
            tuple(
                "https://cdn.geekhack.test/Smileys/solosmileys/blank.gif", "site-asset"),
            tuple("https://i.imgur.com/first.jpg", null));
    assertThat(postImageRepository.findById(post.getRepresentativeImageId()))
        .hasValueSatisfying(
            image -> assertThat(image.getUrl()).isEqualTo("https://i.imgur.com/first.jpg"));
  }

  @Test
  @DisplayName("새 글은 요약하지 않고 요약 대기(PENDING)로 저장한다 (요약은 SummaryWorker가 한다)")
  void newPostIsPendingSummary() {
    crawlService.crawl();

    Post post = post(3000);
    assertThat(post.getSummaryStatus()).isEqualTo(SummaryStatus.PENDING);
    assertThat(post.getSummaryLines()).isNull();
    assertThat(post.getSummarizedBy()).isNull();
  }

  @Test
  @DisplayName("이미 있는 글은 글 페이지를 다시 읽지 않고 목록 값만 갱신한다")
  void updatesExistingPostFromBoardOnly() {
    crawlService.crawl();
    assertThat(client.fetchedTopics).containsExactlyInAnyOrder(2000L, 3000L);
    client.fetchedTopics.clear();
    clock.advance(Duration.ofMinutes(30));
    client.boardPages.put(
        key(Board.IC, 0),
        board
            .replace("82388 Views", "90000 Views")
            .replace("78 Replies", "80 Replies")
            .replace("[IC] Multi Page Topic", "[IC] Renamed Topic")
            .replace("Sat, 31 October 2026, 20:00:00", "Sun, 01 November 2026, 08:00:00"));

    crawlService.crawl();

    Post post = post(3000);
    assertThat(client.fetchedTopics).isEmpty();
    assertThat(post.getViews()).isEqualTo(90000);
    assertThat(post.getReplies()).isEqualTo(80);
    assertThat(post.getTitle()).isEqualTo("[IC] Renamed Topic");
    assertThat(post.getStatsCheckedAt()).isEqualTo(clock.instant());
    assertThat(post.getLastPostAt()).isEqualTo(Instant.parse("2026-11-01T14:00:00Z"));
  }

  @Test
  @DisplayName("목록에서 마지막 게시물 시각을 읽지 못하면 저장된 값을 그대로 둔다")
  void keepsLastPostAtWhenUnreadable() {
    crawlService.crawl();
    client.boardPages.put(key(Board.IC, 0), board.replace("Sat, 31 October 2026, 20:00:00", "알 수 없음"));

    crawlService.crawl();

    assertThat(post(3000).getLastPostAt()).isEqualTo(Instant.parse("2026-11-01T01:00:00Z"));
  }

  @Test
  @DisplayName("글 첫 페이지를 읽지 못해 틀로 저장한 새 글도 마지막 게시물 시각을 저장한다")
  void skeletonFromFailureSavesLastPostAt() {
    client.topics.remove(2000L);

    crawlService.crawl();

    assertThat(post(2000).getLastPostAt()).isEqualTo(Instant.parse("2026-11-02T04:21:35Z"));
  }

  @Test
  @DisplayName("글 첫 페이지를 읽지 못한 새 글은 틀 글로 저장하고, 나머지 글도 저장한다")
  void topicFailureSavesSkeleton() {
    client.topics.remove(2000L);

    crawlService.crawl();

    assertThat(post(2000).isDetailLoaded()).isFalse();
    assertThat(post(2000).getCategory()).isEqualTo(Category.UNCLASSIFIED);
    assertThat(post(2000).getTitle()).isEqualTo("[IC] Foo & Bar — A \"Test\" Board");
    assertThat(post(3000).isDetailLoaded()).isTrue();
  }

  @Test
  @DisplayName("글 첫 페이지를 읽지 못해 틀로 남은 글은 다음 정기 수집에서 채운다")
  void skeletonFromFailureIsFilledLater() throws IOException {
    client.topics.remove(2000L);
    crawlService.crawl();
    client.topics.put(2000L, read("/fixtures/topic-attachment.html"));

    crawlService.crawl();

    assertThat(post(2000).isDetailLoaded()).isTrue();
  }

  @Test
  @DisplayName("목록 한 페이지를 읽지 못해도 다음 페이지를 처리한다")
  void boardPageFailureDoesNotStopNextPage() {
    client.boardPages.remove(key(Board.IC, 0));
    client.boardPages.put(key(Board.IC, 1), board);

    crawlService.crawl();

    assertThat(postRepository.findByTopicId(3000L)).isPresent();
  }

  @Test
  @DisplayName("제목이 deleted인 새 글은 글 페이지를 읽지 않고 저장하지도 않는다")
  void skipsNewDeletedTopic() {
    client.boardPages.put(key(Board.IC, 0), board.replace("[IC] Multi Page Topic", "deleted"));

    crawlService.crawl();

    assertThat(postRepository.findByTopicId(3000L)).isEmpty();
    assertThat(client.fetchedTopics).containsExactly(2000L);
  }

  @Test
  @DisplayName("저장한 글의 제목이 deleted로 바뀌면 숨긴다")
  void hidesExistingPostWhenDeleted() {
    crawlService.crawl();
    assertThat(post(3000).isHidden()).isFalse();
    client.boardPages.put(key(Board.IC, 0), board.replace("[IC] Multi Page Topic", "deleted"));

    crawlService.crawl();

    assertThat(post(3000).isHidden()).isTrue();
    assertThat(post(2000).isHidden()).isFalse();
  }

  @Test
  @DisplayName("새 글은 본문까지 읽은 글로 저장한다")
  void newPostIsDetailLoaded() {
    crawlService.crawl();

    assertThat(post(3000).isDetailLoaded()).isTrue();
    assertThat(post(3000).getDetailFetchedAt()).isEqualTo(clock.instant());
  }

  @Test
  @DisplayName("정기 수집은 기본 정렬(마지막 댓글순) 0~1쪽을 읽고 커버리지는 바꾸지 않는다")
  void crawlReadsDefaultOrderWithoutCoverage() {
    crawlService.crawl();

    assertThat(client.fetchedBoardPages)
        .containsExactly(key(Board.IC, 0), key(Board.IC, 1), key(Board.GB, 0), key(Board.GB, 1));
    assertThat(client.fetchedFirstPostPages).isEmpty();
    assertThat(backfill().getNextPage()).isZero();
    assertThat(backfill().getFloorTopicId()).isNull();
    assertThat(backfill().isExhausted()).isFalse();
  }

  @Test
  @DisplayName("정기 수집은 0~1쪽에 있는 틀 글을 글 첫 페이지로 채운다")
  void crawlFillsSkeletonOnFirstPages() {
    crawlService.backfillNextPage(Board.IC, Duration.ZERO);
    assertThat(post(3000).isDetailLoaded()).isFalse();
    clock.advance(Duration.ofMinutes(30));

    crawlService.crawl();

    Post post = post(3000);
    assertThat(post.isDetailLoaded()).isTrue();
    assertThat(post.getDetailFetchedAt()).isEqualTo(clock.instant());
    assertThat(post.getBodyText()).isEqualTo("Test Board A small board — made up.");
    assertThat(post.getPostedAt()).isEqualTo(Instant.parse("2026-10-13T13:29:35Z"));
    assertThat(post.getSummaryStatus()).isEqualTo(SummaryStatus.PENDING);
    assertThat(postImageRepository.findById(post.getRepresentativeImageId()))
        .hasValueSatisfying(
            image -> assertThat(image.getUrl()).isEqualTo("https://i.imgur.com/first.jpg"));
    assertThat(client.fetchedTopics).containsExactlyInAnyOrder(2000L, 3000L);
  }

  @Test
  @DisplayName("과거 글 확보는 작성순 목록 다음 쪽의 새 글을 글 페이지 없이 미분류 틀로 저장한다")
  void backfillSavesSkeletons() {
    setBackfill(1, 5000L, false);
    client.firstPostPages.put(key(Board.IC, 1), onPage(board.replace("[IC] Multi Page Topic", "[IC] GMK Ocean"), 1));

    assertThat(crawlService.backfillNextPage(Board.IC, Duration.ZERO)).isTrue();

    Post post = post(3000);
    assertThat(client.fetchedTopics).isEmpty();
    assertThat(post.isDetailLoaded()).isFalse();
    assertThat(post.getDetailFetchedAt()).isNull();
    assertThat(post.getBodyText()).isNull();
    assertThat(post.getPostedAt()).isNull();
    assertThat(post.getRepresentativeImageId()).isNull();
    assertThat(postImageRepository.findAll()).isEmpty();
    assertThat(post.getSummaryStatus()).isEqualTo(SummaryStatus.PENDING);
    // 제목에 키캡 단어(GMK)가 있어도 본문을 읽기 전까지는 분류하지 않는다
    assertThat(post.getCategory()).isEqualTo(Category.UNCLASSIFIED);
    assertThat(post.getTitle()).isEqualTo("[IC] GMK Ocean");
    assertThat(post.getAuthor()).isEqualTo("bob");
    assertThat(post.getViews()).isEqualTo(82388);
    assertThat(post.getReplies()).isEqualTo(78);
    assertThat(post.getStatsCheckedAt()).isEqualTo(clock.instant());
    assertThat(post.getOriginalUrl()).isEqualTo("https://geekhack.test/index.php?topic=3000.0");
    assertThat(post.getLastPostAt()).isEqualTo(Instant.parse("2026-11-01T01:00:00Z"));
    assertThat(post(2000).getCategory()).isEqualTo(Category.UNCLASSIFIED);
  }

  @Test
  @DisplayName("과거 글 확보는 받은 쪽으로 커버리지를 넓힌다")
  void backfillRecordsCoverage() {
    client.firstPostPages.put(key(Board.IC, 1), BoardHtml.page(1, 1500, 1400));

    crawlService.backfillNextPage(Board.IC, Duration.ZERO);
    assertThat(backfill().getNextPage()).isEqualTo(1);
    assertThat(backfill().getFloorTopicId()).isEqualTo(2000L);

    crawlService.backfillNextPage(Board.IC, Duration.ZERO);
    assertThat(backfill().getNextPage()).isEqualTo(2);
    assertThat(backfill().getFloorTopicId()).isEqualTo(1400L);
    assertThat(postRepository.findByTopicId(1400L)).isPresent();
    assertThat(client.fetchedFirstPostPages)
        .containsExactly(key(Board.IC, 0), key(Board.IC, 1));
    assertThat(client.fetchedBoardPages).isEmpty();
  }

  @Test
  @DisplayName("끝을 넘은 쪽을 달라고 해 앞 쪽(마지막 쪽)이 오면 끝까지 받은 것으로 기록한다")
  void backfillClampedPageExhausts() {
    setBackfill(2, 1400L, false);
    client.firstPostPages.put(key(Board.IC, 2), BoardHtml.page(1, 1500, 1400));

    assertThat(crawlService.backfillNextPage(Board.IC, Duration.ZERO)).isTrue();

    assertThat(backfill().getNextPage()).isEqualTo(2);
    assertThat(backfill().getFloorTopicId()).isEqualTo(1400L);
    assertThat(backfill().isExhausted()).isTrue();
  }

  @Test
  @DisplayName("글을 한 건도 읽지 못한 쪽은 받지 못한 것으로 보고 커버리지를 바꾸지 않는다")
  void backfillEmptyPageIsFailure() {
    client.firstPostPages.put(key(Board.IC, 0), BoardHtml.page(0));

    assertThat(crawlService.backfillNextPage(Board.IC, Duration.ZERO)).isFalse();

    assertThat(backfill().getNextPage()).isZero();
    assertThat(backfill().getFloorTopicId()).isNull();
    assertThat(backfill().isExhausted()).isFalse();
  }

  @Test
  @DisplayName("과거 글 확보에서 이미 있는 글은 목록 값을 갱신하고, deleted 제목인 새 글은 저장하지 않는다")
  void backfillUpdatesExistingAndSkipsDeleted() {
    crawlService.crawl();
    setBackfill(0, null, false);
    client.firstPostPages.put(
        key(Board.IC, 0),
        board
            .replace("82388 Views", "90000 Views")
            .replace("Sat, 31 October 2026, 20:00:00", "Sun, 01 November 2026, 08:00:00")
            .replace("[IC] Foo &amp; Bar &#8212; A &quot;Test&quot; Board", "deleted"));
    postRepository.delete(post(2000));

    crawlService.backfillNextPage(Board.IC, Duration.ZERO);

    assertThat(post(3000).getViews()).isEqualTo(90000);
    assertThat(post(3000).getLastPostAt()).isEqualTo(Instant.parse("2026-11-01T14:00:00Z"));
    assertThat(post(3000).isDetailLoaded()).isTrue();
    assertThat(postRepository.findByTopicId(2000L)).isEmpty();
  }

  @Test
  @DisplayName("끝까지 받았으면 과거 글 확보는 목록을 요청하지 않는다")
  void backfillSkipsWhenExhausted() {
    setBackfill(3, 100L, true);

    assertThat(crawlService.backfillNextPage(Board.IC, Duration.ZERO)).isFalse();

    assertThat(client.fetchedFirstPostPages).isEmpty();
  }

  @Test
  @DisplayName("과거 글 확보에서 목록을 받지 못하면 false이고 커버리지는 그대로다")
  void backfillFailureKeepsCoverage() {
    client.firstPostPages.remove(key(Board.IC, 0));

    assertThat(crawlService.backfillNextPage(Board.IC, Duration.ZERO)).isFalse();

    assertThat(backfill().getNextPage()).isZero();
    assertThat(backfill().isExhausted()).isFalse();
  }

  @Test
  @DisplayName("다른 요청이 과거 글을 확보하는 중이면 기다리지 않고 포기한다")
  void concurrentBackfillGivesUp() throws Exception {
    CountDownLatch fetching = new CountDownLatch(1);
    CountDownLatch release = new CountDownLatch(1);
    client.onBoardFetch =
        () -> {
          fetching.countDown();
          await(release);
        };
    CompletableFuture<Boolean> first =
        CompletableFuture.supplyAsync(() -> crawlService.backfillNextPage(Board.IC, Duration.ZERO));
    assertThat(fetching.await(10, TimeUnit.SECONDS)).isTrue();

    boolean second = crawlService.backfillNextPage(Board.IC, Duration.ZERO);
    release.countDown();

    assertThat(second).isFalse();
    assertThat(first.get(10, TimeUnit.SECONDS)).isTrue();
    assertThat(client.fetchedFirstPostPages).containsExactly(key(Board.IC, 0));
  }

  @Test
  @DisplayName("정기 수집은 두 게시판을 읽고 글마다 읽은 게시판을 저장한다")
  void crawlSavesBoardOfEachList() throws IOException {
    client.boardPages.put(key(Board.GB, 0), BoardHtml.page(0, 4000));
    client.topics.put(4000L, read("/fixtures/topic-external.html"));

    crawlService.crawl();

    assertThat(post(3000).getBoard()).isEqualTo(Board.IC);
    assertThat(post(2000).getBoard()).isEqualTo(Board.IC);
    assertThat(post(4000).getBoard()).isEqualTo(Board.GB);
    assertThat(post(4000).isDetailLoaded()).isTrue();
  }

  @Test
  @DisplayName("글 첫 페이지를 읽지 못해 틀로 저장한 GB 글도 게시판을 저장한다")
  void skeletonFromFailureSavesBoard() {
    client.boardPages.put(key(Board.GB, 0), BoardHtml.page(0, 4000));

    crawlService.crawl();

    assertThat(post(4000).isDetailLoaded()).isFalse();
    assertThat(post(4000).getBoard()).isEqualTo(Board.GB);
  }

  @Test
  @DisplayName("이미 저장한 글이 다른 게시판 목록에 보이면(운영자가 옮긴 경우) 그 게시판으로 바꾼다")
  void movedPostUpdatesBoard() {
    crawlService.crawl();
    assertThat(post(3000).getBoard()).isEqualTo(Board.IC);
    client.boardPages.put(key(Board.IC, 0), BoardHtml.page(0, 2000));
    client.boardPages.put(key(Board.GB, 0), BoardHtml.page(0, 3000));
    client.fetchedTopics.clear();

    crawlService.crawl();

    assertThat(post(3000).getBoard()).isEqualTo(Board.GB);
    assertThat(post(3000).isDetailLoaded()).isTrue();
    assertThat(post(2000).getBoard()).isEqualTo(Board.IC);
    assertThat(client.fetchedTopics).isEmpty();
  }

  @Test
  @DisplayName("GB 과거 글 확보는 GB 작성순 목록을 받아 GB 틀 글로 저장하고 GB 커버리지만 넓힌다")
  void backfillGbUsesGbCoverage() {
    setBackfill(1, 2000L, false);
    jdbcTemplate.update(
        "update board_backfill set next_page = 0, floor_topic_id = null, exhausted = false"
            + " where board = 'GB'");
    client.firstPostPages.put(key(Board.GB, 0), BoardHtml.page(0, 4100, 4000));

    assertThat(crawlService.backfillNextPage(Board.GB, Duration.ZERO)).isTrue();

    assertThat(client.fetchedFirstPostPages).containsExactly(key(Board.GB, 0));
    assertThat(post(4100).getBoard()).isEqualTo(Board.GB);
    assertThat(post(4000).isDetailLoaded()).isFalse();
    BoardBackfill gb = backfillRepository.current(Board.GB);
    assertThat(gb.getNextPage()).isEqualTo(1);
    assertThat(gb.getFloorTopicId()).isEqualTo(4000L);
    assertThat(backfill().getNextPage()).isEqualTo(1);
    assertThat(backfill().getFloorTopicId()).isEqualTo(2000L);
  }

  @Test
  @DisplayName("IC 게시판을 확보하는 중이면 GB 게시판 확보도 기다리지 않고 포기한다 (게시판과 상관없이 한 번에 한 요청)")
  void backfillOfOtherBoardGivesUpWhileBusy() throws Exception {
    CountDownLatch fetching = new CountDownLatch(1);
    CountDownLatch release = new CountDownLatch(1);
    client.onBoardFetch =
        () -> {
          fetching.countDown();
          await(release);
        };
    client.firstPostPages.put(key(Board.GB, 0), BoardHtml.page(0, 4000));
    CompletableFuture<Boolean> first =
        CompletableFuture.supplyAsync(() -> crawlService.backfillNextPage(Board.IC, Duration.ZERO));
    assertThat(fetching.await(10, TimeUnit.SECONDS)).isTrue();

    boolean second = crawlService.backfillNextPage(Board.GB, Duration.ZERO);
    release.countDown();

    assertThat(second).isFalse();
    assertThat(first.get(10, TimeUnit.SECONDS)).isTrue();
    assertThat(client.fetchedFirstPostPages).containsExactly(key(Board.IC, 0));
  }

  private static void await(CountDownLatch latch) {
    try {
      latch.await(10, TimeUnit.SECONDS);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }

  @TestConfiguration(proxyBeanMethods = false)
  static class FakesConfig {
    @Bean
    @Primary
    FakeGeekhackClient fakeGeekhackClient() {
      return new FakeGeekhackClient();
    }

    @Bean
    @Primary
    MutableClock mutableClock() {
      return new MutableClock();
    }
  }

  /** 실제 사이트 대신 픽스처를 돌려준다. 등록되지 않은 페이지는 요청 실패처럼 예외를 던진다. */
  static class FakeGeekhackClient extends GeekhackClient {
    /** 기본 정렬 목록. 키는 key(게시판, 쪽) */
    final Map<String, String> boardPages = new HashMap<>();

    /** 작성순 목록. 키는 key(게시판, 쪽) */
    final Map<String, String> firstPostPages = new HashMap<>();

    final Map<Long, String> topics = new HashMap<>();
    final List<Long> fetchedTopics = new ArrayList<>();
    final List<String> fetchedBoardPages = new ArrayList<>();
    final List<String> fetchedFirstPostPages = new ArrayList<>();

    /** 작성순 목록 요청 중에 실행할 일 (동시성 테스트용) */
    Runnable onBoardFetch = () -> {};

    FakeGeekhackClient() {
      super(RestClient.create());
    }

    void reset() {
      boardPages.clear();
      firstPostPages.clear();
      topics.clear();
      fetchedTopics.clear();
      fetchedBoardPages.clear();
      fetchedFirstPostPages.clear();
      onBoardFetch = () -> {};
    }

    @Override
    public String fetchBoardPage(Board board, int page) {
      String key = key(board, page);
      fetchedBoardPages.add(key);
      if (!boardPages.containsKey(key)) {
        throw new IllegalStateException("목록 요청 실패");
      }
      return boardPages.get(key);
    }

    @Override
    public String fetchBoardPageByFirstPost(Board board, int page) {
      String key = key(board, page);
      fetchedFirstPostPages.add(key);
      onBoardFetch.run();
      if (!firstPostPages.containsKey(key)) {
        throw new IllegalStateException("목록 요청 실패");
      }
      return firstPostPages.get(key);
    }

    @Override
    public String fetchTopic(long topicId) {
      fetchedTopics.add(topicId);
      if (!topics.containsKey(topicId)) {
        throw new IllegalStateException("글 요청 실패");
      }
      return topics.get(topicId);
    }
  }
}

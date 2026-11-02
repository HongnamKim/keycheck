package com.keycheck.crawl.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import com.keycheck.MutableClock;
import com.keycheck.TestcontainersConfiguration;
import com.keycheck.crawl.client.GeekhackClient;
import com.keycheck.post.domain.Category;
import com.keycheck.post.domain.Post;
import com.keycheck.post.domain.PostImage;
import com.keycheck.post.domain.SummaryStatus;
import com.keycheck.post.repository.PostImageRepository;
import com.keycheck.post.repository.PostRepository;
import com.keycheck.summary.dto.PostSource;
import com.keycheck.summary.dto.SummaryResult;
import com.keycheck.summary.service.FakeSummarizer;
import com.keycheck.summary.service.Summarizer;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
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
  @Autowired private SwitchableSummarizer summarizer;
  @Autowired private MutableClock clock;

  private String board;

  @BeforeEach
  void setUp() throws IOException {
    postRepository.deleteAll();
    board = read("/fixtures/board-list.html");
    client.reset();
    client.boardPages.put(0, board);
    client.boardPages.put(1, EMPTY_BOARD);
    client.topics.put(2000L, read("/fixtures/topic-attachment.html"));
    client.topics.put(3000L, read("/fixtures/topic-external.html"));
    summarizer.fail = false;
  }

  private String read(String path) throws IOException {
    try (InputStream in = getClass().getResourceAsStream(path)) {
      return new String(in.readAllBytes(), StandardCharsets.UTF_8);
    }
  }

  private Post post(long topicId) {
    return postRepository.findByTopicId(topicId).orElseThrow();
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
  }

  @Test
  @DisplayName("새 글을 제목과 본문으로 분류한다")
  void classifiesNewPost() {
    client.boardPages.put(0, board.replace("[IC] Multi Page Topic", "[IC] GMK Ocean"));

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
  @DisplayName("요약에 성공하면 DONE으로 저장한다")
  void summaryDone() {
    crawlService.crawl();

    Post post = post(3000);
    assertThat(post.getSummaryStatus()).isEqualTo(SummaryStatus.DONE);
    assertThat(post.getSummaryLines().get(0)).isEqualTo("[가짜 요약] [IC] Multi Page Topic");
  }

  @Test
  @DisplayName("요약이 실패해도 글은 FAILED로 저장한다")
  void summaryFailed() {
    summarizer.fail = true;

    crawlService.crawl();

    assertThat(post(3000).getSummaryStatus()).isEqualTo(SummaryStatus.FAILED);
    assertThat(post(3000).getSummaryLines()).isNull();
  }

  @Test
  @DisplayName("이미 있는 글은 글 페이지를 다시 읽지 않고 목록 값만 갱신한다")
  void updatesExistingPostFromBoardOnly() {
    crawlService.crawl();
    assertThat(client.fetchedTopics).containsExactlyInAnyOrder(2000L, 3000L);
    client.fetchedTopics.clear();
    clock.advance(Duration.ofMinutes(30));
    client.boardPages.put(
        0,
        board
            .replace("82388 Views", "90000 Views")
            .replace("78 Replies", "80 Replies")
            .replace("[IC] Multi Page Topic", "[IC] Renamed Topic"));

    crawlService.crawl();

    Post post = post(3000);
    assertThat(client.fetchedTopics).isEmpty();
    assertThat(post.getViews()).isEqualTo(90000);
    assertThat(post.getReplies()).isEqualTo(80);
    assertThat(post.getTitle()).isEqualTo("[IC] Renamed Topic");
    assertThat(post.getStatsCheckedAt()).isEqualTo(clock.instant());
  }

  @Test
  @DisplayName("글 하나를 읽지 못해도 나머지 글은 저장한다")
  void oneTopicFailureDoesNotStopOthers() {
    client.topics.remove(2000L);

    crawlService.crawl();

    assertThat(postRepository.findByTopicId(2000L)).isEmpty();
    assertThat(postRepository.findByTopicId(3000L)).isPresent();
  }

  @Test
  @DisplayName("목록 한 페이지를 읽지 못해도 다음 페이지를 처리한다")
  void boardPageFailureDoesNotStopNextPage() {
    client.boardPages.remove(0);
    client.boardPages.put(1, board);

    crawlService.crawl();

    assertThat(postRepository.findByTopicId(3000L)).isPresent();
  }

  @Test
  @DisplayName("제목이 deleted인 새 글은 글 페이지를 읽지 않고 저장하지도 않는다")
  void skipsNewDeletedTopic() {
    client.boardPages.put(0, board.replace("[IC] Multi Page Topic", "deleted"));

    crawlService.crawl();

    assertThat(postRepository.findByTopicId(3000L)).isEmpty();
    assertThat(client.fetchedTopics).containsExactly(2000L);
  }

  @Test
  @DisplayName("저장한 글의 제목이 deleted로 바뀌면 숨긴다")
  void hidesExistingPostWhenDeleted() {
    crawlService.crawl();
    assertThat(post(3000).isHidden()).isFalse();
    client.boardPages.put(0, board.replace("[IC] Multi Page Topic", "deleted"));

    crawlService.crawl();

    assertThat(post(3000).isHidden()).isTrue();
    assertThat(post(2000).isHidden()).isFalse();
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
    SwitchableSummarizer switchableSummarizer() {
      return new SwitchableSummarizer();
    }

    @Bean
    @Primary
    MutableClock mutableClock() {
      return new MutableClock();
    }
  }

  /** 실제 사이트 대신 픽스처를 돌려준다. 등록되지 않은 페이지는 요청 실패처럼 예외를 던진다. */
  static class FakeGeekhackClient extends GeekhackClient {
    final Map<Integer, String> boardPages = new HashMap<>();
    final Map<Long, String> topics = new HashMap<>();
    final List<Long> fetchedTopics = new ArrayList<>();

    FakeGeekhackClient() {
      super(RestClient.create());
    }

    void reset() {
      boardPages.clear();
      topics.clear();
      fetchedTopics.clear();
    }

    @Override
    public String fetchBoardPage(int page) {
      if (!boardPages.containsKey(page)) {
        throw new IllegalStateException("목록 요청 실패");
      }
      return boardPages.get(page);
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

  static class SwitchableSummarizer implements Summarizer {
    private final Summarizer delegate = new FakeSummarizer();
    boolean fail;

    @Override
    public SummaryResult summarize(PostSource source) {
      if (fail) {
        throw new IllegalStateException("요약 실패");
      }
      return delegate.summarize(source);
    }
  }
}

package com.keycheck.summary.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import com.anthropic.core.JsonValue;
import com.anthropic.core.http.Headers;
import com.anthropic.errors.AnthropicIoException;
import com.anthropic.errors.UnauthorizedException;
import com.keycheck.TestcontainersConfiguration;
import com.keycheck.post.domain.Board;
import com.keycheck.post.domain.Category;
import com.keycheck.post.domain.Post;
import com.keycheck.post.domain.SpecItem;
import com.keycheck.post.domain.Subcategory;
import com.keycheck.post.domain.SummaryStatus;
import com.keycheck.post.repository.PostRepository;
import com.keycheck.summary.config.SummaryProperties;
import com.keycheck.summary.dto.PostSource;
import com.keycheck.summary.dto.SummaryResult;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class SummaryWorkerTest {
  private static final Instant CHECKED_AT = Instant.parse("2026-11-01T00:00:00Z");

  @Autowired private PostRepository postRepository;
  @Autowired private JdbcTemplate jdbcTemplate;

  private final RecordingSummarizer summarizer = new RecordingSummarizer();
  private final MutableClock clock = new MutableClock(CHECKED_AT);

  @BeforeEach
  void setUp() {
    postRepository.deleteAll();
  }

  private SummaryWorker worker(boolean redoFake) {
    return worker(redoFake, false);
  }

  private SummaryWorker worker(boolean redoFake, boolean redoMissingSubcategory) {
    return new SummaryWorker(
        postRepository,
        summarizer,
        new SummaryProperties(redoFake, redoMissingSubcategory),
        clock);
  }

  /** 본문까지 읽은 글. 규칙 분류기가 키보드로 임시 분류했다. */
  private Post saveLoaded(long topicId, Consumer<Post> customize) {
    Post post =
        Post.create(
            Board.IC,
            topicId,
            "[IC] 글 " + topicId,
            "maker",
            Category.KEYBOARD,
            Instant.parse("2026-10-13T00:00:00Z"),
            10,
            1,
            CHECKED_AT,
            "https://geekhack.test/index.php?topic=" + topicId + ".0",
            "본문 " + topicId);
    customize.accept(post);
    return postRepository.saveAndFlush(post);
  }

  private Post saveLoaded(long topicId) {
    return saveLoaded(topicId, post -> {});
  }

  private Post reload(Post post) {
    return postRepository.findById(post.getId()).orElseThrow();
  }

  private void markSummarizedBy(Post post, String summarizedBy) {
    jdbcTemplate.update(
        "update post set summary_status = 'DONE', summarized_by = ? where id = ?",
        summarizedBy,
        post.getId());
  }

  @Test
  @DisplayName("요약 대기 글을 요약해 DONE으로 바꾸고 요약기와 분류를 기록한다")
  void summarizesPendingPost() {
    Post post = saveLoaded(2000);
    summarizer.result =
        new SummaryResult(
            List.of("첫 줄", "둘째 줄"),
            List.of(new SpecItem("배열", "65%")),
            Category.KEYCAP,
            Subcategory.KC_GMK,
            "claude-opus-5-5");

    assertThat(worker(false).summarizeNext()).isTrue();

    Post saved = reload(post);
    assertThat(saved.getSummaryStatus()).isEqualTo(SummaryStatus.DONE);
    assertThat(saved.getSummaryLines()).containsExactly("첫 줄", "둘째 줄");
    assertThat(saved.getSpecs()).containsExactly(new SpecItem("배열", "65%"));
    assertThat(saved.getSummarizedBy()).isEqualTo("claude-opus-5-5");
    assertThat(saved.getCategory()).isEqualTo(Category.KEYCAP);
    assertThat(saved.getSubcategory()).isEqualTo(Subcategory.KC_GMK);
    assertThat(summarizer.sources)
        .extracting(PostSource::getBoard, PostSource::getTitle, PostSource::getBodyText)
        .containsExactly(tuple(Board.IC, "[IC] 글 2000", "본문 2000"));
  }

  @Test
  @DisplayName("요약 결과에 분류가 없으면(가짜 요약기) 분류와 세부 분류를 그대로 둔다")
  void keepsCategoryWhenResultHasNone() {
    Post post = saveLoaded(2000, p -> p.setSubcategory(Subcategory.KB_75));

    worker(false).summarizeNext();

    Post saved = reload(post);
    assertThat(saved.getCategory()).isEqualTo(Category.KEYBOARD);
    assertThat(saved.getSubcategory()).isEqualTo(Subcategory.KB_75);
    assertThat(saved.getSummarizedBy()).isEqualTo(FakeSummarizer.ID);
    assertThat(saved.getSummaryLines()).containsExactly("[가짜 요약] [IC] 글 2000", "본문 2000");
  }

  @Test
  @DisplayName("요약 결과에 분류는 있고 세부 분류가 없으면 세부 분류를 비운다")
  void clearsSubcategoryWhenResultHasCategoryOnly() {
    Post post = saveLoaded(2000, p -> p.setSubcategory(Subcategory.KB_75));
    summarizer.result =
        new SummaryResult(
            List.of("데스크매트예요."), List.of(), Category.OTHER, null, "claude-opus-5-5");

    worker(false).summarizeNext();

    Post saved = reload(post);
    assertThat(saved.getCategory()).isEqualTo(Category.OTHER);
    assertThat(saved.getSubcategory()).isNull();
  }

  private void markDetailFetchedAt(Post post, Instant fetchedAt) {
    jdbcTemplate.update(
        "update post set detail_fetched_at = ? where id = ?",
        Timestamp.from(fetchedAt),
        post.getId());
  }

  @Test
  @DisplayName("본문을 채운 시각이 같으면 글 번호가 큰(최근) 글부터 한 번에 하나씩 요약한다")
  void summarizesRecentFirstOneAtATime() {
    saveLoaded(2000);
    saveLoaded(3000);

    worker(false).summarizeNext();
    assertThat(summarizer.titles()).containsExactly("[IC] 글 3000");

    worker(false).summarizeNext();
    assertThat(summarizer.titles()).containsExactly("[IC] 글 3000", "[IC] 글 2000");

    assertThat(worker(false).summarizeNext()).isFalse();
  }

  @Test
  @DisplayName("방금 본문을 채운 글은 글 번호가 작아도 먼저 요약한다")
  void summarizesJustFilledFirst() {
    saveLoaded(3000);
    markDetailFetchedAt(saveLoaded(1000), CHECKED_AT.plusSeconds(60));

    SummaryWorker worker = worker(false);
    worker.summarizeNext();
    worker.summarizeNext();

    assertThat(summarizer.titles()).containsExactly("[IC] 글 1000", "[IC] 글 3000");
  }

  @Test
  @DisplayName("다시 요약할 글은 요약 대기 글 다음에, 본문을 채운 시각이 늦은 글부터 요약한다")
  void redoTargetsAfterPendingByFilledAt() {
    Post newerFake = saveLoaded(1000);
    markSummarizedBy(newerFake, FakeSummarizer.ID);
    markDetailFetchedAt(newerFake, CHECKED_AT.plusSeconds(120));
    Post olderMissing = saveMissingSubcategory(4000, Category.KEYCAP);
    markDetailFetchedAt(olderMissing, CHECKED_AT.plusSeconds(60));
    saveLoaded(2000);
    summarizer.result =
        new SummaryResult(
            List.of("다시 쓴 요약"),
            List.of(),
            Category.KEYBOARD,
            Subcategory.KB_65,
            "claude-opus-5-5");

    SummaryWorker worker = worker(true, true);
    while (worker.summarizeNext()) {
      // 대상이 없을 때까지
    }

    assertThat(summarizer.titles())
        .containsExactly("[IC] 글 2000", "[IC] 글 1000", "[IC] 글 4000");
  }

  @Test
  @DisplayName("틀 글, 숨김 글, 이미 요약했거나 실패한 글은 요약하지 않는다")
  void skipsNonTargets() {
    postRepository.saveAndFlush(
        Post.createSkeleton(
            Board.IC,
            5000L, "[IC] 틀", "maker", 1, 0, CHECKED_AT, "https://geekhack.test/?topic=5000.0"));
    saveLoaded(4000, post -> post.setHidden(true));
    saveLoaded(3000, post -> post.setSummaryStatus(SummaryStatus.FAILED));
    markSummarizedBy(saveLoaded(2000), "claude-opus-5-5");

    assertThat(worker(false).summarizeNext()).isFalse();

    assertThat(summarizer.sources).isEmpty();
  }

  @Test
  @DisplayName("글 때문에 요약할 수 없으면(거절·잘린 응답·형식 오류) FAILED로 바꾸고 다시 시도하지 않는다")
  void failureMarksFailed() {
    Post post = saveLoaded(2000);
    summarizer.fail = true;

    assertThat(worker(false).summarizeNext()).isTrue();

    Post saved = reload(post);
    assertThat(saved.getSummaryStatus()).isEqualTo(SummaryStatus.FAILED);
    assertThat(saved.getSummaryLines()).isNull();
    assertThat(worker(false).summarizeNext()).isFalse();
    assertThat(summarizer.sources).hasSize(1);
  }

  @Test
  @DisplayName("API를 쓸 수 없으면(연결·인증·과부하 오류) 요약 대기로 두고 잠시 쉬었다가 다시 시도한다")
  void apiErrorKeepsPendingAndPauses() {
    Post post = saveLoaded(2000);
    summarizer.error = new AnthropicIoException("연결 실패");
    SummaryWorker worker = worker(false);

    assertThat(worker.summarizeNext()).isTrue();
    assertThat(reload(post).getSummaryStatus()).isEqualTo(SummaryStatus.PENDING);

    clock.advance(SummaryWorker.ERROR_PAUSE.minusSeconds(1));
    assertThat(worker.summarizeNext()).isFalse();
    assertThat(summarizer.sources).hasSize(1);

    summarizer.error = null;
    clock.advance(Duration.ofSeconds(1));
    assertThat(worker.summarizeNext()).isTrue();
    assertThat(reload(post).getSummaryStatus()).isEqualTo(SummaryStatus.DONE);
  }

  @Test
  @DisplayName("redo-fake로 다시 요약하다 API 오류가 나면 보이던 가짜 요약을 그대로 둔다")
  void apiErrorKeepsFakeSummary() {
    Post post = saveLoaded(2000);
    worker(false).summarizeNext();
    summarizer.error =
        UnauthorizedException.builder()
            .headers(Headers.builder().build())
            .body(JsonValue.from("invalid x-api-key"))
            .build();

    assertThat(worker(true).summarizeNext()).isTrue();

    Post saved = reload(post);
    assertThat(saved.getSummaryStatus()).isEqualTo(SummaryStatus.DONE);
    assertThat(saved.getSummarizedBy()).isEqualTo(FakeSummarizer.ID);
    assertThat(saved.getSummaryLines()).isNotEmpty();
  }

  @Test
  @DisplayName("redo-fake가 꺼져 있으면 가짜 요약기로 만든 요약을 다시 요약하지 않는다")
  void redoFakeOffKeepsFakeSummaries() {
    markSummarizedBy(saveLoaded(2000), FakeSummarizer.ID);

    assertThat(worker(false).summarizeNext()).isFalse();
  }

  @Test
  @DisplayName("redo-fake가 켜져 있으면 가짜 요약기로 만든 요약만 다시 요약한다")
  void redoFakeOnResummarizesFakeSummaries() {
    Post fake = saveLoaded(2000);
    markSummarizedBy(fake, FakeSummarizer.ID);
    markSummarizedBy(saveLoaded(3000), "claude-opus-5-5");
    summarizer.result =
        new SummaryResult(
            List.of("다시 쓴 요약"), List.of(), Category.OTHER, null, "claude-opus-5-5");

    assertThat(worker(true).summarizeNext()).isTrue();
    assertThat(worker(true).summarizeNext()).isFalse();

    assertThat(summarizer.titles()).containsExactly("[IC] 글 2000");
    Post saved = reload(fake);
    assertThat(saved.getSummarizedBy()).isEqualTo("claude-opus-5-5");
    assertThat(saved.getSummaryLines()).containsExactly("다시 쓴 요약");
    assertThat(saved.getCategory()).isEqualTo(Category.OTHER);
  }

  /** Claude로 요약했지만 세부 분류가 없는 글 */
  private Post saveMissingSubcategory(long topicId, Category category) {
    Post post = saveLoaded(topicId, p -> p.setCategory(category));
    markSummarizedBy(post, "claude-opus-5-5");
    return post;
  }

  @Test
  @DisplayName("redo-missing-subcategory가 꺼져 있으면 세부 분류가 없는 요약을 다시 요약하지 않는다")
  void redoMissingSubcategoryOff() {
    saveMissingSubcategory(2000, Category.KEYBOARD);

    assertThat(worker(false, false).summarizeNext()).isFalse();
  }

  @Test
  @DisplayName("redo-missing-subcategory가 켜져 있으면 세부 분류가 없는 키보드·키캡 요약만 다시 요약한다")
  void redoMissingSubcategoryOn() {
    Post keyboard = saveMissingSubcategory(2000, Category.KEYBOARD);
    saveMissingSubcategory(2001, Category.KEYCAP);
    saveMissingSubcategory(3000, Category.OTHER);
    Post withSub = saveMissingSubcategory(4000, Category.KEYBOARD);
    jdbcTemplate.update("update post set subcategory = 'KB_60' where id = ?", withSub.getId());
    Post hidden = saveMissingSubcategory(5000, Category.KEYBOARD);
    jdbcTemplate.update("update post set hidden = true where id = ?", hidden.getId());
    jdbcTemplate.update(
        "update post set summary_status = 'FAILED' where id = ?",
        saveMissingSubcategory(6000, Category.KEYCAP).getId());
    summarizer.result =
        new SummaryResult(
            List.of("다시 쓴 요약"),
            List.of(),
            Category.KEYBOARD,
            Subcategory.KB_65,
            "claude-opus-5-5");

    SummaryWorker worker = worker(false, true);
    while (worker.summarizeNext()) {
      // 대상이 없을 때까지
    }

    assertThat(summarizer.titles()).containsExactly("[IC] 글 2001", "[IC] 글 2000");
    assertThat(reload(keyboard).getSubcategory()).isEqualTo(Subcategory.KB_65);
  }

  @Test
  @DisplayName("가짜 요약기에서는 redo-missing-subcategory가 켜져 있어도 Claude 요약을 덮지 않는다")
  void redoMissingSubcategoryIgnoredWithFakeSummarizer() {
    Post post = saveMissingSubcategory(2000, Category.KEYBOARD);
    jdbcTemplate.update(
        "update post set summary_lines = '[\"Claude 요약\"]'::jsonb where id = ?", post.getId());
    SummaryWorker worker =
        new SummaryWorker(
            postRepository, new FakeSummarizer(), new SummaryProperties(false, true), clock);

    assertThat(worker.summarizeNext()).isFalse();

    Post saved = reload(post);
    assertThat(saved.getSummarizedBy()).isEqualTo("claude-opus-5-5");
    assertThat(saved.getSummaryLines()).containsExactly("Claude 요약");
  }

  @Test
  @DisplayName("다시 요약한 글은 결과에 세부 분류가 없어도 한 프로세스에서 다시 요약하지 않는다")
  void redoesEachPostOnce() {
    Post post = saveMissingSubcategory(2000, Category.KEYBOARD);
    summarizer.result =
        new SummaryResult(
            List.of("다시 쓴 요약"), List.of(), Category.KEYBOARD, null, "claude-opus-5-5");
    SummaryWorker worker = worker(false, true);

    assertThat(worker.summarizeNext()).isTrue();
    assertThat(worker.summarizeNext()).isFalse();

    assertThat(summarizer.sources).hasSize(1);
    assertThat(reload(post).getSummaryLines()).containsExactly("다시 쓴 요약");
  }

  @Test
  @DisplayName("다시 요약한 글을 건너뛰어도 그 뒤의 대상은 요약한다")
  void skipsRedoneAndContinues() {
    saveMissingSubcategory(3000, Category.KEYBOARD);
    saveMissingSubcategory(2000, Category.KEYCAP);
    summarizer.result =
        new SummaryResult(
            List.of("다시 쓴 요약"), List.of(), Category.KEYBOARD, null, "claude-opus-5-5");
    SummaryWorker worker = worker(false, true);

    assertThat(worker.summarizeNext()).isTrue();
    assertThat(worker.summarizeNext()).isTrue();
    assertThat(worker.summarizeNext()).isFalse();

    assertThat(summarizer.titles()).containsExactly("[IC] 글 3000", "[IC] 글 2000");
  }

  @Test
  @DisplayName("요약하는 동안 바뀐 다른 값(조회수·숨김)은 덮어쓰지 않는다")
  void doesNotOverwriteConcurrentWrites() {
    Post post = saveLoaded(2000);
    summarizer.onSummarize =
        () ->
            jdbcTemplate.update(
                "update post set views = 999, hidden = true where id = ?", post.getId());

    worker(false).summarizeNext();

    Post saved = reload(post);
    assertThat(saved.getSummaryStatus()).isEqualTo(SummaryStatus.DONE);
    assertThat(saved.getViews()).isEqualTo(999);
    assertThat(saved.isHidden()).isTrue();
  }

  /** 받은 입력을 기록한다. result가 없으면 가짜 요약기 결과를 돌려준다. */
  static class RecordingSummarizer implements Summarizer {
    final List<PostSource> sources = new ArrayList<>();
    SummaryResult result;
    boolean fail;
    RuntimeException error;
    Runnable onSummarize = () -> {};

    @Override
    public SummaryResult summarize(PostSource source) {
      sources.add(source);
      onSummarize.run();
      if (fail) {
        throw new IllegalStateException("요약 실패");
      }
      if (error != null) {
        throw error;
      }
      return result != null ? result : new FakeSummarizer().summarize(source);
    }

    List<String> titles() {
      return sources.stream().map(PostSource::getTitle).toList();
    }
  }

  static class MutableClock extends Clock {
    private Instant now;

    MutableClock(Instant now) {
      this.now = now;
    }

    void advance(Duration duration) {
      now = now.plus(duration);
    }

    @Override
    public Instant instant() {
      return now;
    }

    @Override
    public ZoneId getZone() {
      return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId zone) {
      return this;
    }
  }
}

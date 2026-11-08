package com.keycheck.summary.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.AdditionalAnswers.delegatesTo;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import com.anthropic.core.JsonValue;
import com.anthropic.core.http.Headers;
import com.anthropic.errors.AnthropicIoException;
import com.anthropic.errors.UnauthorizedException;
import com.keycheck.TestcontainersConfiguration;
import com.keycheck.crawl.config.CollectProperties;
import com.keycheck.post.domain.Board;
import com.keycheck.post.domain.BodyLink;
import com.keycheck.post.domain.Category;
import com.keycheck.post.domain.Post;
import com.keycheck.post.domain.SpecItem;
import com.keycheck.post.domain.Subcategory;
import com.keycheck.post.domain.SummaryStatus;
import com.keycheck.post.domain.VendorItem;
import com.keycheck.post.repository.PostRepository;
import com.keycheck.summary.config.SummaryProperties;
import com.keycheck.summary.dto.PostSource;
import com.keycheck.summary.dto.SummaryResult;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
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
    return worker(redoFake, redoMissingSubcategory, false);
  }

  private SummaryWorker worker(
      boolean redoFake, boolean redoMissingSubcategory, boolean redoMissingProductName) {
    return worker(redoFake, redoMissingSubcategory, redoMissingProductName, 300);
  }

  private SummaryWorker worker(
      boolean redoFake,
      boolean redoMissingSubcategory,
      boolean redoMissingProductName,
      int dailyLimit) {
    return new SummaryWorker(
        postRepository,
        summarizer,
        new SummaryProperties(
            redoFake, redoMissingSubcategory, redoMissingProductName, dailyLimit),
        new CollectProperties(0),
        new DailySummaryBudget(
            new SummaryProperties(
                redoFake, redoMissingSubcategory, redoMissingProductName, dailyLimit),
            clock),
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
            "claude-opus-5-5",
            null,
            null,
            null,
            List.of());

    assertThat(worker(false).summarizeNext()).isTrue();

    Post saved = reload(post);
    assertThat(saved.getSummaryStatus()).isEqualTo(SummaryStatus.DONE);
    assertThat(saved.getSummaryLines()).containsExactly("첫 줄", "둘째 줄");
    assertThat(saved.getSpecs()).containsExactly(new SpecItem("배열", "65%"));
    assertThat(saved.getSummarizedBy()).isEqualTo("claude-opus-5-5");
    assertThat(saved.getCategory()).isEqualTo(Category.KEYCAP);
    assertThat(saved.getSubcategory()).isEqualTo(Subcategory.KC_GMK);
    assertThat(saved.getSummarizedAt()).isEqualTo(CHECKED_AT);
    assertThat(summarizer.sources)
        .extracting(
            PostSource::getBoard,
            PostSource::getTitle,
            PostSource::getBodyText,
            PostSource::getPostedAt)
        .containsExactly(
            tuple(
                Board.IC, "[IC] 글 2000", "본문 2000", Instant.parse("2026-10-13T00:00:00Z")));
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
            List.of("데스크매트예요."),
            List.of(),
            Category.OTHER,
            null,
            "claude-opus-5-5",
            null,
            null,
            null,
            List.of());

    worker(false).summarizeNext();

    Post saved = reload(post);
    assertThat(saved.getCategory()).isEqualTo(Category.OTHER);
    assertThat(saved.getSubcategory()).isNull();
  }

  @Test
  @DisplayName("저장된 본문 링크를 요약기에 넘기고 결과의 벤더를 저장하며, 가짜 요약이면 빈 목록을 저장한다")
  void savesVendorsAndPassesLinks() {
    Post linked = saveLoaded(2000);
    jdbcTemplate.update(
        "update post set body_links = '[{\"text\": \"Swagkeys\", \"url\": \"https://swagkeys.com/p\"}]'::jsonb"
            + " where id = ?",
        linked.getId());
    summarizer.result =
        new SummaryResult(
            List.of("GMK 키캡 공동구매예요."),
            List.of(),
            Category.KEYCAP,
            Subcategory.KC_GMK,
            "claude-opus-5-5",
            null,
            null,
            "GMK Test",
            List.of(
                new VendorItem("Swagkeys", "KR", "https://swagkeys.com/p"),
                new VendorItem("NovelKeys", "US", null)));

    worker(false).summarizeNext();

    assertThat(summarizer.sources.get(0).getLinks())
        .containsExactly(new BodyLink("Swagkeys", "https://swagkeys.com/p"));
    assertThat(reload(linked).getVendors())
        .containsExactly(
            new VendorItem("Swagkeys", "KR", "https://swagkeys.com/p"),
            new VendorItem("NovelKeys", "US", null));
    // jsonb에는 {name, region, url}만 저장한다
    assertThat(
            jdbcTemplate.queryForObject(
                "select vendors::text from post where id = ?", String.class, linked.getId()))
        .contains("\"name\"", "\"region\"", "\"url\"")
        .doesNotContain("korean");

    // 링크를 저장하지 않은 글(null)을 가짜 요약기로 요약
    Post unlinked = saveLoaded(1000);
    summarizer.result = null;

    worker(false).summarizeNext();

    assertThat(summarizer.sources.get(1).getLinks()).isEmpty();
    assertThat(reload(unlinked).getVendors()).isNotNull().isEmpty();
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
  @DisplayName("하루 요약 상한에 닿으면 대기 글이 있어도 쉬고, 한국 시간으로 다음 날이 되면 다시 요약한다")
  void stopsAtDailyLimit() {
    saveLoaded(2000);
    saveLoaded(3000);
    saveLoaded(4000);
    // 상한에 닿은 뒤 DB를 읽는지 보려고 실제 저장소에 넘기는 목을 쓴다
    PostRepository repository = mock(PostRepository.class, delegatesTo(postRepository));
    SummaryWorker worker =
        new SummaryWorker(
            repository,
            summarizer,
            new SummaryProperties(false, false, false, 2),
            new CollectProperties(0),
            new DailySummaryBudget(new SummaryProperties(false, false, false, 2), clock),
            clock);

    assertThat(worker.summarizeNext()).isTrue();
    assertThat(worker.summarizeNext()).isTrue();
    assertThat(worker.summarizeNext()).isFalse();
    assertThat(summarizer.titles()).containsExactly("[IC] 글 4000", "[IC] 글 3000");

    // CHECKED_AT은 한국 시간 11월 1일 9시라, 15시간 뒤가 한국 시간 다음 날 0시다(UTC로는 아직 11월 1일)
    clearInvocations(repository);
    clock.advance(Duration.ofHours(15).minusSeconds(1));
    assertThat(worker.summarizeNext()).isFalse();
    // 상한에 닿은 날에는 요약 대상을 다시 읽지 않는다(5초마다 도는 작업이 DB 전송량을 쓰지 않게)
    verifyNoInteractions(repository);
    clock.advance(Duration.ofSeconds(1));
    assertThat(worker.summarizeNext()).isTrue();
    assertThat(summarizer.titles())
        .containsExactly("[IC] 글 4000", "[IC] 글 3000", "[IC] 글 2000");
  }

  @Test
  @DisplayName("API가 오류 응답을 준 호출(과금되지 않음)은 하루 상한에 세지 않고, 연결 실패·시간 초과는 센다")
  void apiErrorResponseIsNotCountedTowardDailyLimit() {
    saveLoaded(2000);
    saveLoaded(3000);
    SummaryWorker worker = worker(false, false, false, 1);
    summarizer.error =
        UnauthorizedException.builder()
            .headers(Headers.builder().build())
            .body(JsonValue.from("invalid x-api-key"))
            .build();

    assertThat(worker.summarizeNext()).isTrue();
    summarizer.error = null;
    clock.advance(SummaryWorker.ERROR_PAUSE);
    assertThat(worker.summarizeNext()).isTrue();
    assertThat(worker.summarizeNext()).isFalse();
    assertThat(summarizer.titles()).containsExactly("[IC] 글 3000", "[IC] 글 3000");

    // 시간 초과는 Anthropic이 처리를 마쳐 과금됐을 수 있다
    SummaryWorker timedOut = worker(false, false, false, 1);
    summarizer.error = new AnthropicIoException("시간 초과");
    assertThat(timedOut.summarizeNext()).isTrue();
    summarizer.error = null;
    clock.advance(SummaryWorker.ERROR_PAUSE);
    assertThat(timedOut.summarizeNext()).isFalse();
  }

  @Test
  @DisplayName("가짜 요약기는 과금되지 않으니 하루 상한과 상관없이 요약한다")
  void fakeSummarizerIgnoresDailyLimit() {
    saveLoaded(2000);
    saveLoaded(3000);
    SummaryWorker worker =
        new SummaryWorker(
            postRepository,
            new FakeSummarizer(),
            new SummaryProperties(false, false, false, 1),
            new CollectProperties(0),
            new DailySummaryBudget(new SummaryProperties(false, false, false, 1), clock),
            clock);

    assertThat(worker.summarizeNext()).isTrue();
    assertThat(worker.summarizeNext()).isTrue();
    assertThat(postRepository.findAll())
        .extracting(Post::getSummaryStatus)
        .containsOnly(SummaryStatus.DONE);
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
  @DisplayName("미리 읽기가 더 늦게 채운 글이 있어도 사용자가 연 글(미리 읽기로 채우지 않은 글)을 먼저 요약한다")
  void summarizesOpenedBeforePrefetched() {
    Post prefetched = saveLoaded(3000);
    jdbcTemplate.update("update post set prefetched = true where id = ?", prefetched.getId());
    markDetailFetchedAt(prefetched, CHECKED_AT.plusSeconds(60));
    saveLoaded(2000);

    SummaryWorker worker = worker(false);
    worker.summarizeNext();
    worker.summarizeNext();

    assertThat(summarizer.titles()).containsExactly("[IC] 글 2000", "[IC] 글 3000");
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
            "claude-opus-5-5",
            null,
            null,
            null,
            List.of());

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
  @DisplayName("수집 기간 경계 아래 글은 요약하지 않고, 경계 글 번호 그 글은 요약한다")
  void skipsBeforeSince() {
    saveLoaded(2499);
    saveLoaded(2500);
    SummaryWorker worker =
        new SummaryWorker(
            postRepository,
            summarizer,
            new SummaryProperties(false, false, false, 300),
            new CollectProperties(2500),
            new DailySummaryBudget(new SummaryProperties(false, false, false, 300), clock),
            clock);

    while (worker.summarizeNext()) {
      // 대상이 없을 때까지
    }

    assertThat(summarizer.titles()).containsExactly("[IC] 글 2500");
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
    assertThat(saved.getSummarizedAt()).isNull();
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
            List.of("다시 쓴 요약"),
            List.of(),
            Category.OTHER,
            null,
            "claude-opus-5-5",
            null,
            null,
            null,
            List.of());

    assertThat(worker(true).summarizeNext()).isTrue();
    assertThat(worker(true).summarizeNext()).isFalse();

    assertThat(summarizer.titles()).containsExactly("[IC] 글 2000");
    Post saved = reload(fake);
    assertThat(saved.getSummarizedBy()).isEqualTo("claude-opus-5-5");
    assertThat(saved.getSummaryLines()).containsExactly("다시 쓴 요약");
    assertThat(saved.getCategory()).isEqualTo(Category.OTHER);
  }

  @Test
  @DisplayName("다시 요약하면 요약 저장 시각을 그때로 바꾼다")
  void resummaryUpdatesSummarizedAt() {
    Post post = saveLoaded(2000);
    worker(false).summarizeNext();
    assertThat(reload(post).getSummarizedAt()).isEqualTo(CHECKED_AT);
    clock.advance(Duration.ofHours(3));

    assertThat(worker(true).summarizeNext()).isTrue();

    assertThat(reload(post).getSummarizedAt()).isEqualTo(CHECKED_AT.plus(Duration.ofHours(3)));
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
            "claude-opus-5-5",
            null,
            null,
            null,
            List.of());

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
            postRepository,
            new FakeSummarizer(),
            new SummaryProperties(false, true, false, 300),
            new CollectProperties(0),
            new DailySummaryBudget(new SummaryProperties(false, true, false, 300), clock),
            clock);

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
            List.of("다시 쓴 요약"),
            List.of(),
            Category.KEYBOARD,
            null,
            "claude-opus-5-5",
            null,
            null,
            null,
            List.of());
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
            List.of("다시 쓴 요약"),
            List.of(),
            Category.KEYBOARD,
            null,
            "claude-opus-5-5",
            null,
            null,
            null,
            List.of());
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

  /** Claude가 요약한 결과(세부 분류 있음, 공동구매 기간 없음) */
  private static SummaryResult namedResult(String productName) {
    return new SummaryResult(
        List.of("다시 쓴 요약"),
        List.of(),
        Category.KEYBOARD,
        Subcategory.KB_60,
        "claude-opus-5-5",
        null,
        null,
        productName,
        List.of());
  }

  /** Claude로 요약했지만 상품 이름은 없는 글(이름을 뽑기 전의 요약). 세부 분류는 있어 다른 다시 요약 대상이 아니다. */
  private Post saveMissingProductName(long topicId) {
    Post post = saveLoaded(topicId, p -> p.setSubcategory(Subcategory.KB_60));
    markSummarizedBy(post, "claude-opus-5-5");
    jdbcTemplate.update(
        "update post set summary_lines = '[\"기존 요약\"]'::jsonb where id = ?", post.getId());
    return post;
  }

  @Test
  @DisplayName("요약 결과의 상품 이름을 저장한다")
  void savesProductName() {
    Post post = saveLoaded(2000);
    summarizer.result = namedResult("Class 60 V2");

    assertThat(worker(false).summarizeNext()).isTrue();

    assertThat(reload(post).getProductName()).isEqualTo("Class 60 V2");
  }

  @Test
  @DisplayName("redo-missing-product-name이 꺼져 있으면 상품 이름이 없는 요약을 다시 요약하지 않는다")
  void redoMissingProductNameOff() {
    saveMissingProductName(2000);

    assertThat(worker(false, false, false).summarizeNext()).isFalse();
    assertThat(summarizer.sources).isEmpty();
  }

  @Test
  @DisplayName("redo-missing-product-name이 켜져 있으면 상품 이름이 없는 요약 완료 글만 요약 대기 글 다음에 다시 요약해 이름을 채운다")
  void redoMissingProductNameOn() {
    Post missing = saveMissingProductName(2000);
    jdbcTemplate.update(
        "update post set product_name = 'Class 60' where id = ?",
        saveMissingProductName(3000).getId());
    jdbcTemplate.update(
        "update post set hidden = true where id = ?", saveMissingProductName(4000).getId());
    jdbcTemplate.update(
        "update post set summary_status = 'FAILED' where id = ?",
        saveMissingProductName(5000).getId());
    saveLoaded(1000);
    summarizer.result = namedResult("Class 60 V2");

    SummaryWorker worker = worker(false, false, true);
    // 같은 글을 끝없이 다시 고르면 테스트가 멈추지 않고 실패하도록 횟수를 묶는다
    for (int i = 0; i < 5 && worker.summarizeNext(); i++) {
      // 대상이 없을 때까지
    }
    assertThat(worker.summarizeNext()).isFalse();

    assertThat(summarizer.titles()).containsExactly("[IC] 글 1000", "[IC] 글 2000");
    Post saved = reload(missing);
    assertThat(saved.getProductName()).isEqualTo("Class 60 V2");
    assertThat(saved.getSummaryLines()).containsExactly("다시 쓴 요약");
  }

  @Test
  @DisplayName("가짜 요약기에서는 redo-missing-product-name이 켜져 있어도 Claude 요약을 덮지 않는다")
  void redoMissingProductNameIgnoredWithFakeSummarizer() {
    Post post = saveMissingProductName(2000);
    SummaryWorker worker =
        new SummaryWorker(
            postRepository,
            new FakeSummarizer(),
            new SummaryProperties(false, false, true, 300),
            new CollectProperties(0),
            new DailySummaryBudget(new SummaryProperties(false, false, true, 300), clock),
            clock);

    assertThat(worker.summarizeNext()).isFalse();

    Post saved = reload(post);
    assertThat(saved.getSummarizedBy()).isEqualTo("claude-opus-5-5");
    assertThat(saved.getSummaryLines()).containsExactly("기존 요약");
  }

  @Test
  @DisplayName("상품 이름을 채우려고 다시 요약한 글은 결과에 이름이 없어도 한 실행에서 다시 요약하지 않는다")
  void redoesMissingProductNameOnce() {
    Post post = saveMissingProductName(2000);
    summarizer.result = namedResult(null);
    SummaryWorker worker = worker(false, false, true);

    assertThat(worker.summarizeNext()).isTrue();
    assertThat(worker.summarizeNext()).isFalse();

    assertThat(summarizer.sources).hasSize(1);
    Post saved = reload(post);
    assertThat(saved.getSummaryLines()).containsExactly("다시 쓴 요약");
    assertThat(saved.getProductName()).isNull();
  }

  @Test
  @DisplayName("상품 이름을 채우려고 다시 요약하다 글 때문에 실패하면 요약은 그대로 두고, 같은 실행에서 다시 요약하지 않는다")
  void failedProductNameRedoKeepsSummary() {
    Post post = saveMissingProductName(2000);
    Post pending = saveLoaded(3000);
    summarizer.fail = true;
    SummaryWorker worker = worker(false, false, true);

    assertThat(worker.summarizeNext()).isTrue();
    assertThat(worker.summarizeNext()).isTrue();
    assertThat(worker.summarizeNext()).isFalse();

    assertThat(summarizer.titles()).containsExactly("[IC] 글 3000", "[IC] 글 2000");
    assertThat(reload(pending).getSummaryStatus()).isEqualTo(SummaryStatus.FAILED);
    Post saved = reload(post);
    assertThat(saved.getSummaryStatus()).isEqualTo(SummaryStatus.DONE);
    assertThat(saved.getSummaryLines()).containsExactly("기존 요약");
    assertThat(saved.getProductName()).isNull();
  }

  @Test
  @DisplayName("redo-missing-product-name이 켜져 있어도 요약 대기 글은 결과에 이름이 없으면 한 실행에서 한 번만 요약한다")
  void pendingWithoutProductNameIsSummarizedOnce() {
    Post post = saveLoaded(2000);
    summarizer.result = namedResult(null);
    SummaryWorker worker = worker(false, false, true);

    assertThat(worker.summarizeNext()).isTrue();
    assertThat(worker.summarizeNext()).isFalse();

    assertThat(summarizer.sources).hasSize(1);
    assertThat(reload(post).getProductName()).isNull();
  }

  @Test
  @DisplayName("다시 요약한 결과에 상품 이름이 없으면 저장해 둔 이름을 null로 덮는다")
  void redoOverwritesProductNameWithNull() {
    Post post = saveRenamedGb(2000);
    jdbcTemplate.update("update post set product_name = 'Old Name' where id = ?", post.getId());
    summarizer.result = namedResult(null);

    assertThat(worker(false).summarizeNext()).isTrue();

    assertThat(summarizer.titles()).containsExactly("새 제목");
    Post saved = reload(post);
    assertThat(saved.getSummaryLines()).containsExactly("다시 쓴 요약");
    assertThat(saved.getProductName()).isNull();
  }

  /** 본문까지 읽은 GB 글 */
  private Post saveLoadedGb(long topicId) {
    return saveLoaded(
        topicId,
        post -> {
          post.setBoard(Board.GB);
          post.setTitle("[GB] 글 " + topicId);
        });
  }

  /** Claude로 요약했지만 GB로 기간을 뽑아 본 적은 없는 GB 글(기간을 뽑기 전의 요약, IC에서 옮겨진 글) */
  private Post saveSummarizedGb(long topicId) {
    Post post = saveLoadedGb(topicId);
    markSummarizedBy(post, "claude-opus-5-5");
    jdbcTemplate.update(
        "update post set summary_lines = '[\"기존 요약\"]'::jsonb where id = ?", post.getId());
    return post;
  }

  /** '옛 제목'으로 요약해 기간(10/30~11/13)을 뽑았고, 그 뒤 수집이 제목을 '새 제목'으로 바꾼 GB 글 */
  private Post saveRenamedGb(long topicId) {
    Post post = saveSummarizedGb(topicId);
    jdbcTemplate.update(
        "update post set gb_start_date = '2026-10-30', gb_end_date = '2026-11-13',"
            + " gb_dates_extracted = true, summarized_title = '옛 제목', title = '새 제목'"
            + " where id = ?",
        post.getId());
    return post;
  }

  /** Claude가 IC 글로 요약한 뒤(요약기에 넘긴 제목이 지금 제목과 같다) 운영자가 GB로 옮긴 글 */
  private Post saveMovedFromIc(long topicId) {
    Post post = saveLoaded(topicId);
    markSummarizedBy(post, "claude-opus-5-5");
    jdbcTemplate.update(
        "update post set summary_lines = '[\"기존 요약\"]'::jsonb, summarized_title = title,"
            + " board = 'GB' where id = ?",
        post.getId());
    return post;
  }

  /** Claude가 요약한 결과 */
  private static SummaryResult claudeResult(LocalDate gbStartDate, LocalDate gbEndDate) {
    return new SummaryResult(
        List.of("Claude 요약"),
        List.of(),
        Category.KEYBOARD,
        Subcategory.KB_65,
        "claude-opus-5-5",
        gbStartDate,
        gbEndDate,
        null,
        List.of());
  }

  @Test
  @DisplayName("GB 글을 요약하면 기간을 저장하고, 기간을 뽑아 본 글로 요약기에 넘긴 제목과 함께 표시한다")
  void savesGbPeriodAndMarks() {
    Post post = saveLoadedGb(2000);
    summarizer.result = claudeResult(LocalDate.of(2026, 10, 30), LocalDate.of(2026, 11, 13));

    assertThat(worker(false).summarizeNext()).isTrue();

    Post saved = reload(post);
    assertThat(saved.getSummaryStatus()).isEqualTo(SummaryStatus.DONE);
    assertThat(saved.getGbStartDate()).isEqualTo(LocalDate.of(2026, 10, 30));
    assertThat(saved.getGbEndDate()).isEqualTo(LocalDate.of(2026, 11, 13));
    assertThat(saved.isGbDatesExtracted()).isTrue();
    assertThat(saved.getSummarizedTitle()).isEqualTo("[GB] 글 2000");
  }

  @Test
  @DisplayName("IC 글을 요약하면 요약기에 넘긴 제목만 남기고 기간을 뽑아 본 글로 표시하지 않는다")
  void icSummaryDoesNotMarkGb() {
    Post post = saveLoaded(2000);
    summarizer.result = claudeResult(null, null);

    assertThat(worker(false).summarizeNext()).isTrue();

    Post saved = reload(post);
    assertThat(saved.isGbDatesExtracted()).isFalse();
    assertThat(saved.getSummarizedTitle()).isEqualTo("[IC] 글 2000");
  }

  @Test
  @DisplayName("GB로 기간을 뽑아 본 적 없는 요약 완료 GB 글은 요약 대기 글 다음에 다시 요약하고, IC 글은 다시 요약하지 않는다")
  void redoesGbNeverExtracted() {
    Post gb = saveSummarizedGb(2000);
    markSummarizedBy(saveLoaded(3000), "claude-opus-5-5");
    saveLoaded(1000);
    summarizer.result = claudeResult(LocalDate.of(2026, 10, 30), LocalDate.of(2026, 11, 13));

    SummaryWorker worker = worker(false);
    // 같은 글을 끝없이 다시 고르면 테스트가 멈추지 않고 실패하도록 횟수를 묶는다
    for (int i = 0; i < 5 && worker.summarizeNext(); i++) {
      // 대상이 없을 때까지
    }
    assertThat(worker.summarizeNext()).isFalse();

    assertThat(summarizer.titles()).containsExactly("[IC] 글 1000", "[GB] 글 2000");
    Post saved = reload(gb);
    assertThat(saved.getSummaryLines()).containsExactly("Claude 요약");
    assertThat(saved.getGbStartDate()).isEqualTo(LocalDate.of(2026, 10, 30));
    assertThat(saved.getGbEndDate()).isEqualTo(LocalDate.of(2026, 11, 13));
    assertThat(saved.isGbDatesExtracted()).isTrue();
  }

  @Test
  @DisplayName("요약한 뒤 제목이 바뀐 GB 글은 새 제목으로 다시 요약하고, 그 제목으로는 한 번만 요약한다")
  void redoesGbWhenTitleChanged() {
    Post post = saveRenamedGb(2000);
    summarizer.result = claudeResult(LocalDate.of(2026, 10, 30), LocalDate.of(2026, 12, 10));
    SummaryWorker worker = worker(false);

    assertThat(worker.summarizeNext()).isTrue();

    assertThat(summarizer.titles()).containsExactly("새 제목");
    Post saved = reload(post);
    assertThat(saved.getSummarizedTitle()).isEqualTo("새 제목");
    assertThat(saved.getGbEndDate()).isEqualTo(LocalDate.of(2026, 12, 10));
    assertThat(worker.summarizeNext()).isFalse();
  }

  @Test
  @DisplayName("제목이 바뀐 GB 글을 다시 요약해 기간이 없다고 나오면(취소 등) 저장해 둔 기간을 지운다")
  void redoWithoutPeriodClearsSavedPeriod() {
    Post post = saveRenamedGb(2000);
    summarizer.result = claudeResult(null, null);

    assertThat(worker(false).summarizeNext()).isTrue();

    Post saved = reload(post);
    assertThat(saved.getSummaryLines()).containsExactly("Claude 요약");
    assertThat(saved.getGbStartDate()).isNull();
    assertThat(saved.getGbEndDate()).isNull();
    assertThat(saved.isGbDatesExtracted()).isTrue();
    assertThat(saved.getSummarizedTitle()).isEqualTo("새 제목");
  }

  @Test
  @DisplayName("IC 글은 요약한 뒤 제목이 바뀌어도 다시 요약하지 않는다")
  void icTitleChangeIsNotRedone() {
    Post post = saveLoaded(2000);
    markSummarizedBy(post, "claude-opus-5-5");
    jdbcTemplate.update(
        "update post set summarized_title = '옛 제목', title = '새 제목' where id = ?", post.getId());

    assertThat(worker(false).summarizeNext()).isFalse();
  }

  @Test
  @DisplayName("가짜 요약기는 GB 글을 기간을 뽑아 본 글로 표시하지 않고, GB 글을 다시 요약하지도 않는다")
  void fakeSummarizerSkipsGbRedo() {
    Post pending = saveLoadedGb(4000);
    saveSummarizedGb(3000);
    saveRenamedGb(2000);
    SummaryWorker worker =
        new SummaryWorker(
            postRepository,
            new FakeSummarizer(),
            new SummaryProperties(false, false, false, 300),
            new CollectProperties(0),
            new DailySummaryBudget(new SummaryProperties(false, false, false, 300), clock),
            clock);

    assertThat(worker.summarizeNext()).isTrue();
    assertThat(worker.summarizeNext()).isFalse();

    Post saved = reload(pending);
    assertThat(saved.getSummarizedBy()).isEqualTo(FakeSummarizer.ID);
    assertThat(saved.isGbDatesExtracted()).isFalse();
  }

  @Test
  @DisplayName("가짜 요약기에서 redo-fake가 켜져 있으면 가짜 요약한 GB 글은 GB 다시 요약으로 보지 않고 한 실행에서 한 번만 다시 요약한다")
  void fakeSummarizerRedoesFakeGbOnce() {
    Post post = saveLoadedGb(2000);
    markSummarizedBy(post, FakeSummarizer.ID);
    SummaryWorker worker =
        new SummaryWorker(
            postRepository,
            new FakeSummarizer(),
            new SummaryProperties(true, false, false, 300),
            new CollectProperties(0),
            new DailySummaryBudget(new SummaryProperties(true, false, false, 300), clock),
            clock);

    assertThat(worker.summarizeNext()).isTrue();
    assertThat(worker.summarizeNext()).isFalse();

    assertThat(reload(post).isGbDatesExtracted()).isFalse();
  }

  @Test
  @DisplayName("다시 요약하는 동안 제목이 또 바뀌면 같은 실행에서 바뀐 제목으로 한 번 더 요약한다")
  void titleChangedDuringSummaryIsRedoneAgain() {
    Post post = saveRenamedGb(2000);
    summarizer.result = claudeResult(null, null);
    summarizer.onSummarize =
        () -> {
          if (summarizer.sources.size() == 1) {
            jdbcTemplate.update("update post set title = '더 새 제목' where id = ?", post.getId());
          }
        };
    SummaryWorker worker = worker(false);

    assertThat(worker.summarizeNext()).isTrue();
    assertThat(reload(post).getSummarizedTitle()).isEqualTo("새 제목");

    assertThat(worker.summarizeNext()).isTrue();
    assertThat(worker.summarizeNext()).isFalse();

    assertThat(summarizer.titles()).containsExactly("새 제목", "더 새 제목");
    assertThat(reload(post).getSummarizedTitle()).isEqualTo("더 새 제목");
  }

  @Test
  @DisplayName("GB 글을 다시 요약하다 글 때문에 실패하면 요약은 두고 기간만 비우며, 재시작해도 그 제목으로는 다시 요약하지 않는다")
  void failedGbRedoKeepsSummary() {
    Post post = saveRenamedGb(2000);
    Instant summarizedAt = Instant.parse("2026-10-31T00:00:00Z");
    jdbcTemplate.update(
        "update post set summarized_at = ? where id = ?",
        Timestamp.from(summarizedAt),
        post.getId());
    Post pending = saveLoadedGb(3000);
    summarizer.fail = true;
    SummaryWorker worker = worker(false);

    assertThat(worker.summarizeNext()).isTrue();
    assertThat(worker.summarizeNext()).isTrue();

    assertThat(summarizer.titles()).containsExactly("[GB] 글 3000", "새 제목");
    assertThat(reload(pending).getSummaryStatus()).isEqualTo(SummaryStatus.FAILED);
    Post saved = reload(post);
    assertThat(saved.getSummaryStatus()).isEqualTo(SummaryStatus.DONE);
    assertThat(saved.getSummaryLines()).containsExactly("기존 요약");
    assertThat(saved.getGbStartDate()).isNull();
    assertThat(saved.getGbEndDate()).isNull();
    assertThat(saved.isGbDatesExtracted()).isTrue();
    assertThat(saved.getSummarizedTitle()).isEqualTo("새 제목");
    assertThat(saved.getSummarizedAt()).isEqualTo(summarizedAt);
    assertThat(reload(pending).getSummarizedAt()).isNull();
    assertThat(worker(false).summarizeNext()).isFalse();
  }

  @Test
  @DisplayName("기간을 뽑아 본 적 없는 GB 글을 다시 요약하다 글 때문에 실패해도 요약은 두고, 같은 실행에서도 재시작해도 다시 요약하지 않는다")
  void failedNeverExtractedGbRedoIsNotRetried() {
    Post post = saveSummarizedGb(2000);
    summarizer.fail = true;
    SummaryWorker worker = worker(false);

    assertThat(worker.summarizeNext()).isTrue();

    Post saved = reload(post);
    assertThat(saved.getSummaryStatus()).isEqualTo(SummaryStatus.DONE);
    assertThat(saved.getSummaryLines()).containsExactly("기존 요약");
    assertThat(saved.isGbDatesExtracted()).isTrue();
    assertThat(saved.getSummarizedTitle()).isEqualTo("[GB] 글 2000");
    assertThat(worker.summarizeNext()).isFalse();
    assertThat(worker(false).summarizeNext()).isFalse();
    assertThat(summarizer.sources).hasSize(1);
  }

  @Test
  @DisplayName("IC로 요약한 뒤 GB로 옮겨진 글은 제목이 그대로여도 다시 요약해 기간을 뽑는다")
  void redoesGbMovedFromIc() {
    Post post = saveMovedFromIc(2000);
    summarizer.result = claudeResult(LocalDate.of(2026, 10, 30), LocalDate.of(2026, 11, 13));
    SummaryWorker worker = worker(false);

    assertThat(worker.summarizeNext()).isTrue();
    assertThat(worker.summarizeNext()).isFalse();

    assertThat(summarizer.sources).extracting(PostSource::getBoard).containsExactly(Board.GB);
    Post saved = reload(post);
    assertThat(saved.getSummaryLines()).containsExactly("Claude 요약");
    assertThat(saved.getGbEndDate()).isEqualTo(LocalDate.of(2026, 11, 13));
    assertThat(saved.isGbDatesExtracted()).isTrue();
  }

  @Test
  @DisplayName("IC로 요약한 뒤 GB로 옮겨진 글을 다시 요약하다 글 때문에 실패해도 요약은 두고, 재시작해도 다시 요약하지 않는다")
  void failedGbRedoMovedFromIcKeepsSummary() {
    Post post = saveMovedFromIc(2000);
    summarizer.fail = true;

    assertThat(worker(false).summarizeNext()).isTrue();

    Post saved = reload(post);
    assertThat(saved.getSummaryStatus()).isEqualTo(SummaryStatus.DONE);
    assertThat(saved.getSummaryLines()).containsExactly("기존 요약");
    assertThat(saved.isGbDatesExtracted()).isTrue();
    assertThat(worker(false).summarizeNext()).isFalse();
    assertThat(summarizer.sources).hasSize(1);
  }

  @Test
  @DisplayName("세부 분류도 없는 GB 글의 다시 요약이 글 때문에 실패하면, 같은 실행에서 세부 분류를 채우려고 다시 고르지 않아 요약이 남는다")
  void failedGbRedoIsNotRetriedAsMissingSubcategory() {
    Post post = saveSummarizedGb(2000);
    summarizer.fail = true;
    SummaryWorker worker = worker(false, true);

    assertThat(worker.summarizeNext()).isTrue();
    assertThat(worker.summarizeNext()).isFalse();

    assertThat(summarizer.sources).hasSize(1);
    Post saved = reload(post);
    assertThat(saved.getSummaryStatus()).isEqualTo(SummaryStatus.DONE);
    assertThat(saved.getSummaryLines()).containsExactly("기존 요약");
  }

  @Test
  @DisplayName("세부 분류를 채우려고 다시 요약하다 글 때문에 실패하면 지금처럼 FAILED로 바꾼다")
  void failedOtherRedoStillFails() {
    Post post = saveMissingSubcategory(2000, Category.KEYBOARD);
    summarizer.fail = true;

    assertThat(worker(false, true).summarizeNext()).isTrue();

    assertThat(reload(post).getSummaryStatus()).isEqualTo(SummaryStatus.FAILED);
  }

  @Test
  @DisplayName("GB 글을 다시 요약하다 API를 쓸 수 없으면 기간과 표시를 그대로 두고 잠시 쉬었다가 다시 시도한다")
  void apiUnavailableDuringGbRedoChangesNothing() {
    Post post = saveRenamedGb(2000);
    summarizer.error = new AnthropicIoException("연결 실패");
    SummaryWorker worker = worker(false);

    assertThat(worker.summarizeNext()).isTrue();

    Post saved = reload(post);
    assertThat(saved.getSummaryStatus()).isEqualTo(SummaryStatus.DONE);
    assertThat(saved.getSummaryLines()).containsExactly("기존 요약");
    assertThat(saved.getGbStartDate()).isEqualTo(LocalDate.of(2026, 10, 30));
    assertThat(saved.getGbEndDate()).isEqualTo(LocalDate.of(2026, 11, 13));
    assertThat(saved.isGbDatesExtracted()).isTrue();
    assertThat(saved.getSummarizedTitle()).isEqualTo("옛 제목");
    assertThat(worker.summarizeNext()).isFalse();

    summarizer.error = null;
    clock.advance(SummaryWorker.ERROR_PAUSE);
    assertThat(worker.summarizeNext()).isTrue();
    assertThat(summarizer.titles()).containsExactly("새 제목", "새 제목");
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

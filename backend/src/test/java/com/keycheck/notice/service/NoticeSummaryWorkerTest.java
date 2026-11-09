package com.keycheck.notice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.anthropic.core.JsonValue;
import com.anthropic.core.http.Headers;
import com.anthropic.errors.AnthropicIoException;
import com.anthropic.errors.UnauthorizedException;
import com.keycheck.MutableClock;
import com.keycheck.TestcontainersConfiguration;
import com.keycheck.crawl.config.CollectProperties;
import com.keycheck.image.config.StorageProperties;
import com.keycheck.image.service.ThumbUrls;
import com.keycheck.notice.config.NoticeProperties;
import com.keycheck.notice.domain.ArchiveStatus;
import com.keycheck.notice.domain.Notice;
import com.keycheck.notice.domain.NoticeOrigin;
import com.keycheck.notice.domain.SaleCategory;
import com.keycheck.notice.domain.SaleItem;
import com.keycheck.notice.domain.SaleLink;
import com.keycheck.notice.domain.SalePhase;
import com.keycheck.notice.domain.SaleType;
import com.keycheck.notice.dto.NoticeImage;
import com.keycheck.notice.dto.NoticeSource;
import com.keycheck.notice.dto.NoticeSummaryResult;
import com.keycheck.notice.dto.SaleItemDraft;
import com.keycheck.notice.repository.NoticeAttachmentRepository;
import com.keycheck.notice.repository.NoticeRepository;
import com.keycheck.notice.repository.SaleItemRepository;
import com.keycheck.post.domain.Board;
import com.keycheck.post.domain.Category;
import com.keycheck.post.domain.KoreanVendor;
import com.keycheck.post.domain.Post;
import com.keycheck.post.domain.SummaryStatus;
import com.keycheck.post.repository.PostRepository;
import com.keycheck.summary.config.SummaryProperties;
import com.keycheck.summary.service.DailySummaryBudget;
import com.keycheck.summary.service.FakeSummarizer;
import com.keycheck.summary.service.SummaryInProgress;
import com.keycheck.summary.service.SummaryWorker;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Limit;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class NoticeSummaryWorkerTest {
  private static final Instant NOW = Instant.parse("2026-11-08T01:00:00Z");

  @Autowired private NoticeRepository noticeRepository;
  @Autowired private NoticeAttachmentRepository attachmentRepository;
  @Autowired private SaleItemRepository saleItemRepository;
  @Autowired private PostRepository postRepository;
  @Autowired private JdbcTemplate jdbcTemplate;
  @Autowired private TransactionTemplate transaction;

  private final RecordingSummarizer summarizer = new RecordingSummarizer();
  private final MutableClock clock = new MutableClock();
  private long nextId = 7_000_000L;

  @BeforeEach
  void setUp() {
    jdbcTemplate.update("update notice set parent_id = null");
    noticeRepository.deleteAll();
    postRepository.deleteAll();
    clock.set(NOW);
  }

  private NoticeSummaryWorker worker(NoticeSummarizer notice, DailySummaryBudget budget) {
    return worker(notice, budget, transaction);
  }

  private NoticeSummaryWorker workerWith(TransactionTemplate template) {
    return worker(summarizer, budget(300), template);
  }

  private NoticeSummaryWorker worker(
      NoticeSummarizer notice, DailySummaryBudget budget, TransactionTemplate template) {
    NoticeProperties properties =
        new NoticeProperties(
            "token",
            "http://x",
            "ua",
            java.util.Map.of(),
            Duration.ofHours(24),
            new NoticeProperties.Summary(Duration.ofMinutes(10)));
    ThumbUrls thumbUrls = new ThumbUrls(new StorageProperties(null, null, null, null, "https://img.test"));
    return new NoticeSummaryWorker(
        noticeRepository,
        attachmentRepository,
        saleItemRepository,
        notice,
        properties,
        budget,
        thumbUrls,
        template,
        clock);
  }

  private DailySummaryBudget budget(int dailyLimit) {
    return new DailySummaryBudget(new SummaryProperties(false, false, false, dailyLimit, 1), clock);
  }

  private NoticeSummaryWorker worker() {
    return worker(summarizer, budget(300));
  }

  private Notice saveNotice(Instant postedAt, boolean hasText, Notice parent) {
    long id = nextId++;
    Notice notice =
        Notice.create(
            id,
            id + 1,
            555L,
            KoreanVendor.SWAGKEYS,
            NoticeOrigin.BOT,
            postedAt,
            hasText ? "# 판매 공지 " + id : "",
            hasText,
            "{}");
    notice.setParent(parent);
    return noticeRepository.save(notice);
  }

  private Notice saveNotice(Instant postedAt) {
    return saveNotice(postedAt, true, null);
  }

  /** 첨부를 더하고 보관 상태를 정한다 */
  private void addAttachment(Notice notice, long attachmentId, int position, ArchiveStatus status) {
    transaction.executeWithoutResult(
        s -> {
          Notice loaded = noticeRepository.findById(notice.getId()).orElseThrow();
          loaded.addAttachment(attachmentId, position, "a" + attachmentId + ".png", "http://cdn/x");
          noticeRepository.saveAndFlush(loaded);
        });
    attachmentRepository.markArchived(attachmentId, status);
  }

  private SummaryStatus statusOf(Notice notice) {
    return noticeRepository.findById(notice.getId()).orElseThrow().getSummaryStatus();
  }

  private static SaleItemDraft draft(String name) {
    return new SaleItemDraft(
        name,
        SaleCategory.KEYBOARD,
        SaleType.SALE,
        SalePhase.OPEN,
        null,
        null,
        null,
        null,
        null,
        List.of("줄"),
        List.of(),
        List.of());
  }

  private List<SaleItem> itemsOf(Notice notice) {
    return saleItemRepository.findAll().stream()
        .filter(i -> i.getNotice().getId().equals(notice.getId()))
        .toList();
  }

  private static UnauthorizedException unauthorized() {
    return UnauthorizedException.builder()
        .headers(Headers.builder().build())
        .body(JsonValue.from("invalid x-api-key"))
        .build();
  }

  @Test
  @DisplayName("올라온 지 10분이 안 된 공지는 기다린다")
  void 올라온_지_10분이_안_된_공지는_기다린다() {
    Notice notice = saveNotice(Instant.parse("2026-11-08T00:55:00Z"));
    clock.set(Instant.parse("2026-11-08T01:00:00Z"));
    NoticeSummaryWorker worker = worker();

    assertThat(worker.summarizeNext()).isFalse();
    assertThat(summarizer.sources).isEmpty();

    clock.set(Instant.parse("2026-11-08T01:05:00Z"));
    assertThat(worker.summarizeNext()).isTrue();
    assertThat(statusOf(notice)).isEqualTo(SummaryStatus.DONE);
  }

  @Test
  @DisplayName("사진 보관이 끝나지 않은 공지는 기다린다")
  void 사진_보관이_끝나지_않은_공지는_기다린다() {
    Notice notice = saveNotice(Instant.parse("2026-11-08T00:00:00Z"));
    addAttachment(notice, 1001L, 0, ArchiveStatus.PENDING);
    NoticeSummaryWorker worker = worker();

    assertThat(worker.summarizeNext()).isFalse();

    attachmentRepository.markArchived(1001L, ArchiveStatus.FAILED);
    assertThat(worker.summarizeNext()).isTrue();
    assertThat(statusOf(notice)).isEqualTo(SummaryStatus.DONE);
  }

  @Test
  @DisplayName("딸린 메시지의 사진 보관이 끝나지 않았어도 기다린다")
  void 딸린_메시지의_사진_보관이_끝나지_않았어도_기다린다() {
    Notice notice = saveNotice(Instant.parse("2026-11-08T00:00:00Z"));
    Notice child = saveNotice(Instant.parse("2026-11-08T00:01:00Z"), false, notice);
    addAttachment(child, 1101L, 0, ArchiveStatus.PENDING);

    assertThat(worker().summarizeNext()).isFalse();
  }

  @Test
  @DisplayName("딸린 메시지의 사진을 시간 순으로 함께 보낸다")
  void 딸린_메시지의_사진을_시간_순으로_함께_보낸다() {
    Notice notice = saveNotice(Instant.parse("2026-11-08T00:00:00Z"));
    addAttachment(notice, 2002L, 1, ArchiveStatus.DONE);
    addAttachment(notice, 2001L, 0, ArchiveStatus.DONE);
    Notice second = saveNotice(Instant.parse("2026-11-08T00:03:00Z"), false, notice);
    addAttachment(second, 2201L, 0, ArchiveStatus.DONE);
    Notice first = saveNotice(Instant.parse("2026-11-08T00:02:00Z"), false, notice);
    addAttachment(first, 2102L, 1, ArchiveStatus.DONE);
    addAttachment(first, 2101L, 0, ArchiveStatus.FAILED);
    addAttachment(first, 2103L, 2, ArchiveStatus.DONE);

    assertThat(worker().summarizeNext()).isTrue();

    assertThat(summarizer.sources).hasSize(1);
    List<NoticeImage> images = summarizer.sources.get(0).getImages();
    assertThat(images.stream().map(NoticeImage::getAttachmentId))
        .containsExactly(2001L, 2002L, 2102L, 2103L, 2201L);
    assertThat(images.get(0).getThumbUrl()).isEqualTo("https://img.test/notices/2001-640.webp");
    assertThat(images.get(0).getFilename()).isEqualTo("a2001.png");
  }

  @Test
  @DisplayName("본문 링크를 뽑아 보낸다")
  void 본문_링크를_뽑아_보낸다() {
    Notice notice = saveNotice(Instant.parse("2026-11-08T00:00:00Z"));
    jdbcTemplate.update(
        "update notice set content = ? where id = ?",
        "[판매 페이지](https://www.swagkey.kr/shop_view?idx=1912)\n"
            + "문의 https://open.kakao.com/o/abc.\n"
            + "다시 https://www.swagkey.kr/shop_view?idx=1912 와 [같은 곳](https://www.swagkey.kr/shop_view?idx=1912)",
        notice.getId());

    assertThat(worker().summarizeNext()).isTrue();

    List<SaleLink> links = summarizer.sources.get(0).getLinks();
    assertThat(links)
        .containsExactly(
            new SaleLink("판매 페이지", "https://www.swagkey.kr/shop_view?idx=1912"),
            new SaleLink("https://open.kakao.com/o/abc", "https://open.kakao.com/o/abc"));
  }

  @Test
  @DisplayName("요약 결과를 판매 건으로 저장하고 DONE으로 바꾼다")
  void 요약_결과를_판매_건으로_저장하고_DONE으로_바꾼다() {
    Notice notice = saveNotice(Instant.parse("2026-11-08T00:00:00Z"));
    summarizer.result = new NoticeSummaryResult(List.of(draft("A"), draft("B")), "claude-x");

    assertThat(worker().summarizeNext()).isTrue();

    List<SaleItem> items = itemsOf(notice);
    assertThat(items).extracting(SaleItem::getProductName).containsExactlyInAnyOrder("A", "B");
    assertThat(items).extracting(SaleItem::getPosition).containsExactlyInAnyOrder(0, 1);
    Notice saved = noticeRepository.findById(notice.getId()).orElseThrow();
    assertThat(saved.getSummaryStatus()).isEqualTo(SummaryStatus.DONE);
    assertThat(saved.getSummarizedBy()).isEqualTo("claude-x");
    assertThat(saved.getSummarizedAt()).isEqualTo(NOW);
  }

  @Test
  @DisplayName("다시 요약하면 판매 건을 새로 만든다")
  void 다시_요약하면_판매_건을_새로_만든다() {
    Notice notice = saveNotice(Instant.parse("2026-11-08T00:00:00Z"));
    summarizer.result = new NoticeSummaryResult(List.of(draft("A"), draft("B")), "claude-x");
    NoticeSummaryWorker worker = worker();
    worker.summarizeNext();
    assertThat(itemsOf(notice)).hasSize(2);

    noticeRepository.updateSummaryStatus(notice.getId(), SummaryStatus.PENDING);
    summarizer.result = new NoticeSummaryResult(List.of(draft("C")), "claude-x");
    assertThat(worker.summarizeNext()).isTrue();

    assertThat(itemsOf(notice)).extracting(SaleItem::getProductName).containsExactly("C");
  }

  @Test
  @DisplayName("요약 실패는 FAILED")
  void 요약_실패는_FAILED() {
    Notice notice = saveNotice(Instant.parse("2026-11-08T00:00:00Z"));
    summarizer.error = new IllegalStateException("요약 실패");
    NoticeSummaryWorker worker = worker();

    assertThat(worker.summarizeNext()).isTrue();

    assertThat(statusOf(notice)).isEqualTo(SummaryStatus.FAILED);
    assertThat(worker.summarizeNext()).isFalse();
  }

  @Test
  @DisplayName("API를 쓸 수 없으면 상태를 두고 쉰다")
  void API를_쓸_수_없으면_상태를_두고_쉰다() {
    Notice notice = saveNotice(Instant.parse("2026-11-08T00:00:00Z"));
    summarizer.error = new AnthropicIoException("연결 실패");
    NoticeSummaryWorker worker = worker();

    assertThat(worker.summarizeNext()).isTrue();
    assertThat(statusOf(notice)).isEqualTo(SummaryStatus.PENDING);

    clock.advance(NoticeSummaryWorker.ERROR_PAUSE.minusSeconds(1));
    assertThat(worker.summarizeNext()).isFalse();
    assertThat(summarizer.sources).hasSize(1);

    summarizer.error = null;
    clock.advance(Duration.ofSeconds(1));
    assertThat(worker.summarizeNext()).isTrue();
    assertThat(statusOf(notice)).isEqualTo(SummaryStatus.DONE);
  }

  @Test
  @DisplayName("오류 응답은 하루 한도에서 되돌리고 시간 초과는 센다")
  void 오류_응답은_하루_한도에서_되돌리고_시간_초과는_센다() {
    saveNotice(Instant.parse("2026-11-08T00:00:00Z"));
    NoticeSummaryWorker worker = worker(summarizer, budget(1));
    summarizer.error = unauthorized();
    assertThat(worker.summarizeNext()).isTrue();
    summarizer.error = null;
    clock.advance(NoticeSummaryWorker.ERROR_PAUSE);
    assertThat(worker.summarizeNext()).isTrue();
    assertThat(summarizer.sources).hasSize(2);

    saveNotice(Instant.parse("2026-11-08T00:00:00Z"));
    NoticeSummaryWorker timedOut = worker(summarizer, budget(1));
    summarizer.error = new AnthropicIoException("시간 초과");
    assertThat(timedOut.summarizeNext()).isTrue();
    summarizer.error = null;
    clock.advance(NoticeSummaryWorker.ERROR_PAUSE);
    assertThat(timedOut.summarizeNext()).isFalse();
  }

  @Test
  @DisplayName("하루 한도를 geekhack 요약과 같이 쓴다")
  void 하루_한도를_geekhack_요약과_같이_쓴다() {
    Post post =
        Post.create(
            Board.IC,
            2000L,
            "[IC] 글",
            "maker",
            Category.KEYBOARD,
            Instant.parse("2026-10-13T00:00:00Z"),
            10,
            1,
            Instant.parse("2026-11-01T00:00:00Z"),
            "https://geekhack.test/index.php?topic=2000.0",
            "본문");
    postRepository.saveAndFlush(post);
    saveNotice(Instant.parse("2026-11-08T00:00:00Z"));
    SummaryProperties summaryProperties = new SummaryProperties(false, false, false, 1, 1);
    DailySummaryBudget shared = new DailySummaryBudget(summaryProperties, clock);
    SummaryWorker geekhackWorker =
        new SummaryWorker(
            postRepository,
            source -> new FakeSummarizer().summarize(source),
            summaryProperties,
            new CollectProperties(0),
            shared,
            new SummaryInProgress(),
            clock);

    assertThat(geekhackWorker.summarizeNext()).isTrue();

    assertThat(worker(summarizer, shared).summarizeNext()).isFalse();
    assertThat(summarizer.sources).isEmpty();
  }

  @Test
  @DisplayName("가짜 요약기는 한도를 세지 않는다")
  void 가짜_요약기는_한도를_세지_않는다() {
    Notice a = saveNotice(Instant.parse("2026-11-08T00:00:00Z"));
    Notice b = saveNotice(Instant.parse("2026-11-08T00:01:00Z"));
    NoticeSummaryWorker worker = worker(new FakeNoticeSummarizer(), budget(0));

    assertThat(worker.summarizeNext()).isTrue();
    assertThat(worker.summarizeNext()).isTrue();

    assertThat(statusOf(a)).isEqualTo(SummaryStatus.DONE);
    assertThat(statusOf(b)).isEqualTo(SummaryStatus.DONE);
    assertThat(noticeRepository.findById(a.getId()).orElseThrow().getSummarizedBy())
        .isEqualTo(FakeNoticeSummarizer.ID);
  }

  @Test
  @DisplayName("요약하는 사이 딸린 사진이 더해지면 저장하지 않고 요약 대기로 둔다")
  void 요약하는_사이_딸린_사진이_더해지면_저장하지_않고_요약_대기로_둔다() {
    Notice notice = saveNotice(Instant.parse("2026-11-08T00:00:00Z"));
    summarizer.result = new NoticeSummaryResult(List.of(draft("A")), "claude-x");
    summarizer.onSummarize =
        () -> {
          Notice child = saveNotice(Instant.parse("2026-11-08T00:30:00Z"), false, notice);
          addAttachment(child, 3001L, 0, ArchiveStatus.DONE);
        };
    NoticeSummaryWorker worker = worker();

    assertThat(worker.summarizeNext()).isTrue();

    assertThat(statusOf(notice)).isEqualTo(SummaryStatus.PENDING);
    assertThat(itemsOf(notice)).isEmpty();

    summarizer.onSummarize = () -> {};
    assertThat(worker.summarizeNext()).isTrue();
    assertThat(statusOf(notice)).isEqualTo(SummaryStatus.DONE);
    assertThat(itemsOf(notice)).hasSize(1);
    assertThat(summarizer.sources.get(1).getImages().stream().map(NoticeImage::getAttachmentId))
        .containsExactly(3001L);
  }

  @Test
  @DisplayName("저장이 데이터 오류로 실패하면 FAILED로 두고 다음 공지로 넘어간다")
  void 저장이_데이터_오류로_실패하면_FAILED로_두고_다음_공지로_넘어간다() {
    Notice first = saveNotice(Instant.parse("2026-11-08T00:00:00Z"));
    Notice second = saveNotice(Instant.parse("2026-11-08T00:01:00Z"));
    summarizer.result = new NoticeSummaryResult(List.of(draft("가".repeat(300))), "claude-x");
    NoticeSummaryWorker worker = worker();

    assertThat(worker.summarizeNext()).isTrue();

    assertThat(statusOf(first)).isEqualTo(SummaryStatus.FAILED);
    assertThat(itemsOf(first)).isEmpty();
    summarizer.result = new NoticeSummaryResult(List.of(draft("B")), "claude-x");
    assertThat(worker.summarizeNext()).isTrue();
    assertThat(statusOf(second)).isEqualTo(SummaryStatus.DONE);
    assertThat(summarizer.sources).hasSize(2);
  }

  @Test
  @DisplayName("저장소 일시 오류로 저장이 실패하면 요약 대기로 두고 쉰다")
  void 저장소_일시_오류로_저장이_실패하면_요약_대기로_두고_쉰다() {
    Notice notice = saveNotice(Instant.parse("2026-11-08T00:00:00Z"));
    TransactionTemplate broken = mock(TransactionTemplate.class);
    when(broken.execute(any())).thenThrow(new CannotCreateTransactionException("db down"));
    NoticeSummaryWorker worker = workerWith(broken);

    assertThat(worker.summarizeNext()).isTrue();

    assertThat(statusOf(notice)).isEqualTo(SummaryStatus.PENDING);
    clock.advance(NoticeSummaryWorker.ERROR_PAUSE.minusSeconds(1));
    assertThat(worker.summarizeNext()).isFalse();
    assertThat(summarizer.sources).hasSize(1);
    clock.advance(Duration.ofSeconds(1));
    assertThat(worker.summarizeNext()).isTrue();
    assertThat(summarizer.sources).hasSize(2);
  }

  @Test
  @DisplayName("요약 대상은 글이 있는 글 공지만, 오래된 순으로")
  void 요약_대상은_글이_있는_글_공지만_오래된_순으로() {
    Notice newer = saveNotice(Instant.parse("2026-11-08T00:20:00Z"));
    Notice older = saveNotice(Instant.parse("2026-11-08T00:10:00Z"));
    saveNotice(Instant.parse("2026-11-08T00:05:00Z"), false, null);
    saveNotice(Instant.parse("2026-11-08T00:06:00Z"), true, older);

    List<Notice> targets = noticeRepository.findSummaryTargets(NOW, Limit.of(10));

    assertThat(targets).extracting(Notice::getId).containsExactly(older.getId(), newer.getId());
  }

  static class RecordingSummarizer implements NoticeSummarizer {
    final List<NoticeSource> sources = new ArrayList<>();
    NoticeSummaryResult result;
    RuntimeException error;
    Runnable onSummarize = () -> {};

    @Override
    public NoticeSummaryResult summarize(NoticeSource source) {
      sources.add(source);
      onSummarize.run();
      if (error != null) {
        throw error;
      }
      return result != null ? result : new NoticeSummaryResult(List.of(draft("기본")), "claude-test");
    }
  }
}

package com.keycheck.notice.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.keycheck.TestcontainersConfiguration;
import com.keycheck.notice.domain.ArchiveStatus;
import com.keycheck.notice.domain.Notice;
import com.keycheck.notice.domain.NoticeOrigin;
import com.keycheck.notice.domain.SaleCategory;
import com.keycheck.notice.domain.SaleItem;
import com.keycheck.notice.domain.SalePhase;
import com.keycheck.notice.domain.SaleType;
import com.keycheck.notice.dto.SaleHighlightReason;
import com.keycheck.notice.dto.SaleItemDraft;
import com.keycheck.notice.dto.SalePickResponse;
import com.keycheck.notice.repository.NoticeAttachmentRepository;
import com.keycheck.notice.repository.NoticeRepository;
import com.keycheck.notice.repository.SaleItemRepository;
import com.keycheck.post.domain.KoreanVendor;
import com.keycheck.post.domain.SummaryStatus;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class SaleHighlightServiceTest {
  /** 한국 시각 2026-11-08 15:00 */
  private static final Instant NOW = Instant.parse("2026-11-08T06:00:00Z");

  private static final LocalDate TODAY = LocalDate.of(2026, 11, 8);

  @Autowired private SaleHighlightService service;
  @Autowired private NoticeRepository noticeRepository;
  @Autowired private NoticeAttachmentRepository attachmentRepository;
  @Autowired private SaleItemRepository saleItemRepository;
  @Autowired private JdbcTemplate jdbcTemplate;
  @Autowired private TransactionTemplate transaction;

  private long nextId = 9_000_000L;

  @BeforeEach
  void setUp() {
    jdbcTemplate.update("update notice set parent_id = null");
    noticeRepository.deleteAll();
  }

  /** 대표 사진이 보관된 건을 만든다 */
  private SaleItem item(
      String name,
      Instant postedAt,
      SalePhase phase,
      LocalDate startDate,
      LocalTime startTime,
      LocalDate endDate,
      LocalTime endTime) {
    return save(name, postedAt, phase, SaleCategory.KEYBOARD, startDate, startTime, endDate, endTime, true);
  }

  private SaleItem save(
      String name,
      Instant postedAt,
      SalePhase phase,
      SaleCategory category,
      LocalDate startDate,
      LocalTime startTime,
      LocalDate endDate,
      LocalTime endTime,
      boolean withImage) {
    long id = nextId++;
    Notice notice =
        noticeRepository.save(
            Notice.create(
                id, id + 1, 555L, KoreanVendor.SWAGKEYS, NoticeOrigin.BOT, postedAt, "공지", true, "{}"));
    noticeRepository.markSummarized(notice.getId(), SummaryStatus.DONE, "m", NOW);
    if (withImage) {
      transaction.executeWithoutResult(
          s -> {
            Notice loaded = noticeRepository.findById(notice.getId()).orElseThrow();
            loaded.addAttachment(id, 0, "a.png", "http://cdn/x");
            noticeRepository.saveAndFlush(loaded);
          });
      attachmentRepository.markArchivedWithSize(id, 2000, 1500);
    }
    return saleItemRepository.save(
        SaleItem.create(
            notice,
            0,
            new SaleItemDraft(
                name,
                category,
                SaleType.SALE,
                phase,
                startDate,
                startTime,
                endDate,
                endTime,
                null,
                List.of("줄"),
                List.of(),
                withImage ? List.of(id) : List.of())));
  }

  private static Instant ago(Duration d) {
    return NOW.minus(d);
  }

  private List<String> names(List<SalePickResponse> picks) {
    return picks.stream().map(p -> p.getSale().getProductName()).toList();
  }

  private List<SaleHighlightReason> reasons(List<SalePickResponse> picks) {
    return picks.stream().map(SalePickResponse::getReason).toList();
  }

  @Test
  @DisplayName("곧 시작이 먼저")
  void upcomingFirst() {
    item("마감 임박", ago(Duration.ofDays(5)), SalePhase.OPEN, TODAY.minusDays(2), LocalTime.of(10, 0), TODAY.plusDays(3), LocalTime.of(23, 0));
    item("오늘 저녁", ago(Duration.ofHours(1)), SalePhase.ANNOUNCE, TODAY, LocalTime.of(20, 30), null, null);

    List<SalePickResponse> picks = service.pick(NOW);

    assertThat(names(picks)).containsExactly("오늘 저녁", "마감 임박");
    assertThat(reasons(picks))
        .containsExactly(SaleHighlightReason.UPCOMING, SaleHighlightReason.CLOSING);
  }

  @Test
  @DisplayName("곧 시작이 둘이면 이른 순")
  void upcomingEarliestFirst() {
    item("모레", ago(Duration.ofHours(1)), SalePhase.ANNOUNCE, TODAY.plusDays(2), LocalTime.of(10, 0), null, null);
    item("내일", ago(Duration.ofHours(2)), SalePhase.ANNOUNCE, TODAY.plusDays(1), LocalTime.of(10, 0), null, null);
    item("오늘", ago(Duration.ofHours(3)), SalePhase.ANNOUNCE, TODAY, LocalTime.of(21, 0), null, null);

    assertThat(names(service.pick(NOW))).containsExactly("오늘", "내일");
  }

  @Test
  @DisplayName("날짜만 아는 시작은 그날 시각 아는 건 뒤")
  void dateOnlyStartSortsAfterKnownTimeSameDay() {
    item("날짜만", ago(Duration.ofHours(1)), SalePhase.ANNOUNCE, TODAY.plusDays(1), null, null, null);
    item("열 시", ago(Duration.ofHours(2)), SalePhase.ANNOUNCE, TODAY.plusDays(1), LocalTime.of(10, 0), null, null);

    assertThat(names(service.pick(NOW))).containsExactly("열 시", "날짜만");
  }

  @Test
  @DisplayName("날짜만 아는 마감은 그날 23:59:59, 시각 아는 건 뒤")
  void dateOnlyEndCountsEndOfDay() {
    item("날짜만 마감", ago(Duration.ofDays(3)), SalePhase.OPEN, TODAY.minusDays(1), null, TODAY, null);
    item("오후 마감", ago(Duration.ofDays(4)), SalePhase.OPEN, TODAY.minusDays(1), null, TODAY, LocalTime.of(18, 0));
    // 어제가 마감인 날짜만 건은 이미 지났다
    item("지난 마감", ago(Duration.ofDays(20)), SalePhase.OPEN, TODAY.minusDays(9), null, TODAY.minusDays(1), null);

    List<SalePickResponse> picks = service.pick(NOW);

    assertThat(names(picks)).containsExactly("오후 마감", "날짜만 마감");
    assertThat(reasons(picks)).containsOnly(SaleHighlightReason.CLOSING);
  }

  @Test
  @DisplayName("시작을 모르는 예고는 판매 중이 아니라 곧 마감이 아니다")
  void unknownStartAnnounceIsNotOnSale() {
    item("예고", ago(Duration.ofDays(30)), SalePhase.ANNOUNCE, null, null, TODAY.plusDays(2), null);
    item("시작 모름 판매", ago(Duration.ofDays(30)), SalePhase.OPEN, null, null, TODAY.plusDays(3), null);

    assertThat(names(service.pick(NOW))).containsExactly("시작 모름 판매");
  }

  @Test
  @DisplayName("7일 밖의 시작과 마감은 고르지 않는다")
  void outsideSevenDays() {
    item("먼 시작", ago(Duration.ofDays(30)), SalePhase.ANNOUNCE, TODAY.plusDays(8), LocalTime.of(10, 0), null, null);
    item("먼 마감", ago(Duration.ofDays(30)), SalePhase.OPEN, TODAY.minusDays(1), null, TODAY.plusDays(8), null);

    assertThat(service.pick(NOW)).isEmpty();
  }

  @Test
  @DisplayName("없으면 최근 소식")
  void recentWhenNothingElse() {
    item("이틀 전", ago(Duration.ofDays(2)), SalePhase.OPEN, null, null, null, null);
    item("열흘 전", ago(Duration.ofDays(10)), SalePhase.OPEN, null, null, null, null);
    item("15일 전", ago(Duration.ofDays(15)), SalePhase.OPEN, null, null, null, null);

    List<SalePickResponse> picks = service.pick(NOW);

    assertThat(names(picks)).containsExactly("이틀 전", "열흘 전");
    assertThat(reasons(picks)).containsOnly(SaleHighlightReason.RECENT);
  }

  @Test
  @DisplayName("사진 없는 건과 OTHER와 숨김은 고르지 않는다")
  void skipsNoImageOtherAndHidden() {
    save("사진 없음", ago(Duration.ofHours(1)), SalePhase.OPEN, SaleCategory.KEYBOARD, null, null, null, null, false);
    save("기타", ago(Duration.ofHours(2)), SalePhase.OPEN, SaleCategory.OTHER, null, null, null, null, true);
    SaleItem hidden = item("숨김", ago(Duration.ofHours(3)), SalePhase.OPEN, null, null, null, null);
    hidden.setHidden(true);
    saleItemRepository.save(hidden);
    item("보이는 건", ago(Duration.ofHours(4)), SalePhase.OPEN, null, null, null, null);

    assertThat(names(service.pick(NOW))).containsExactly("보이는 건");
  }

  @Test
  @DisplayName("사진 보관이 끝나지 않은 건은 고르지 않는다")
  void skipsUnarchivedImage() {
    SaleItem item = item("보관 전", ago(Duration.ofHours(1)), SalePhase.OPEN, null, null, null, null);
    attachmentRepository.markArchived(item.getImageIds().getFirst(), ArchiveStatus.FAILED);

    assertThat(service.pick(NOW)).isEmpty();
  }

  @Test
  @DisplayName("대표 사진 폭이 1024 미만이거나 모르면 고르지 않는다")
  void skipsNarrowOrUnknownWidth() {
    SaleItem narrow = item("좁음", ago(Duration.ofHours(1)), SalePhase.OPEN, null, null, null, null);
    SaleItem exact = item("딱 1024", ago(Duration.ofHours(2)), SalePhase.OPEN, null, null, null, null);
    SaleItem unknown = item("크기 모름", ago(Duration.ofHours(3)), SalePhase.OPEN, null, null, null, null);
    attachmentRepository.markArchivedWithSize(narrow.getImageIds().getFirst(), 800, 600);
    attachmentRepository.markArchivedWithSize(exact.getImageIds().getFirst(), 1024, 768);
    jdbcTemplate.update(
        "update notice_attachment set width = null, height = null where id = ?",
        unknown.getImageIds().getFirst());

    assertThat(names(service.pick(NOW))).containsExactly("딱 1024");
  }

  @Test
  @DisplayName("같은 건은 한 번만")
  void sameItemOnce() {
    // 곧 시작이면서 최근 소식이기도 한 건
    item("하나뿐", ago(Duration.ofHours(1)), SalePhase.ANNOUNCE, TODAY, LocalTime.of(20, 0), null, null);

    List<SalePickResponse> picks = service.pick(NOW);

    assertThat(names(picks)).containsExactly("하나뿐");
    assertThat(reasons(picks)).containsExactly(SaleHighlightReason.UPCOMING);
  }

  @Test
  @DisplayName("곧 시작이 둘을 채우면 뒤 단계는 보지 않는다")
  void stopsAtTwo() {
    item("A", ago(Duration.ofHours(1)), SalePhase.ANNOUNCE, TODAY, LocalTime.of(20, 0), null, null);
    item("B", ago(Duration.ofHours(2)), SalePhase.ANNOUNCE, TODAY, LocalTime.of(21, 0), null, null);
    item("C", ago(Duration.ofHours(3)), SalePhase.OPEN, TODAY.minusDays(1), null, TODAY.plusDays(1), null);

    assertThat(names(service.pick(NOW))).containsExactly("A", "B");
  }

  @Test
  @DisplayName("날짜 줄 수: 최근 7일에 올라온 판매 건만 세고, 숨김과 OTHER는 빼고, 사진은 묻지 않는다")
  void countsNewSales() {
    item("하루 전", ago(Duration.ofDays(1)), SalePhase.OPEN, null, null, null, null);
    // 7일 경계: 7일에서 1시간 모자란 건은 세고, 1시간 넘은 건은 세지 않는다
    item("7일 안", ago(Duration.ofDays(7).minusHours(1)), SalePhase.OPEN, null, null, null, null);
    item("7일 밖", ago(Duration.ofDays(7).plusHours(1)), SalePhase.ANNOUNCE, TODAY.plusDays(1), null, null, null);
    save("사진 없음", ago(Duration.ofHours(1)), SalePhase.OPEN, SaleCategory.KEYBOARD, null, null, null, null, false);
    save("기타", ago(Duration.ofHours(2)), SalePhase.OPEN, SaleCategory.OTHER, null, null, null, null, true);
    SaleItem hidden = item("숨김", ago(Duration.ofHours(3)), SalePhase.OPEN, null, null, null, null);
    hidden.setHidden(true);
    saleItemRepository.save(hidden);

    assertThat(service.countNew(NOW)).isEqualTo(3);
  }
}

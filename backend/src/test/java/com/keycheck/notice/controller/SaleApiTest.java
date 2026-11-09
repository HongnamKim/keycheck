package com.keycheck.notice.controller;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.keycheck.MutableClock;
import com.keycheck.TestcontainersConfiguration;
import com.keycheck.notice.domain.ArchiveStatus;
import com.keycheck.notice.domain.Notice;
import com.keycheck.notice.domain.NoticeOrigin;
import com.keycheck.notice.domain.SaleCategory;
import com.keycheck.notice.domain.SaleItem;
import com.keycheck.notice.domain.SaleLink;
import com.keycheck.notice.domain.SalePhase;
import com.keycheck.notice.domain.SaleType;
import com.keycheck.notice.dto.SaleItemDraft;
import com.keycheck.notice.repository.NoticeAttachmentRepository;
import com.keycheck.notice.repository.NoticeRepository;
import com.keycheck.notice.repository.SaleItemRepository;
import com.keycheck.post.domain.KoreanVendor;
import com.keycheck.post.domain.SummaryStatus;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, SaleApiTest.ClockConfig.class})
class SaleApiTest {
  private static final Instant BASE = Instant.parse("2026-11-01T00:00:00Z");

  @Autowired private MockMvc mockMvc;
  @Autowired private NoticeRepository noticeRepository;
  @Autowired private NoticeAttachmentRepository attachmentRepository;
  @Autowired private SaleItemRepository saleItemRepository;
  @Autowired private JdbcTemplate jdbcTemplate;
  @Autowired private TransactionTemplate transaction;
  @Autowired private MutableClock clock;

  private long nextId = 8_000_000L;

  @BeforeEach
  void setUp() {
    jdbcTemplate.update("update notice set parent_id = null");
    noticeRepository.deleteAll();
  }

  private Notice notice(KoreanVendor vendor, Instant postedAt, SummaryStatus status) {
    long id = nextId++;
    Notice notice =
        Notice.create(id, id + 1, 555L, vendor, NoticeOrigin.BOT, postedAt, "공지", true, "{}");
    notice = noticeRepository.save(notice);
    if (status == SummaryStatus.DONE) {
      noticeRepository.markSummarized(
          notice.getId(), SummaryStatus.DONE, "model", Instant.parse("2026-11-05T09:30:00Z"));
    }
    return notice;
  }

  private SaleItem item(Notice notice, int position, String name, SaleCategory category) {
    return item(notice, position, name, category, List.of());
  }

  private SaleItem item(
      Notice notice, int position, String name, SaleCategory category, List<Long> imageIds) {
    return saleItemRepository.save(
        SaleItem.create(
            notice,
            position,
            new SaleItemDraft(
                name,
                category,
                SaleType.SALE,
                SalePhase.OPEN,
                LocalDate.of(2026, 11, 10),
                LocalTime.of(20, 30),
                LocalDate.of(2026, 11, 20),
                null,
                "12월 배송",
                List.of("첫 줄", "둘째 줄"),
                List.of(new SaleLink("구매", "https://shop.test/a")),
                imageIds)));
  }

  private void attachment(Notice notice, long attachmentId, int position, ArchiveStatus status) {
    transaction.executeWithoutResult(
        s -> {
          Notice loaded = noticeRepository.findById(notice.getId()).orElseThrow();
          loaded.addAttachment(attachmentId, position, "a.png", "http://cdn/x");
          noticeRepository.saveAndFlush(loaded);
        });
    if (status == ArchiveStatus.DONE) {
      attachmentRepository.markArchivedWithSize(attachmentId, 3000, 2000);
    } else {
      attachmentRepository.markArchived(attachmentId, status);
    }
  }

  @Test
  @DisplayName("최근 공지 순으로 20개씩 준다")
  void pagesTwentyAtATime() throws Exception {
    for (int i = 0; i < 25; i++) {
      Notice n = notice(KoreanVendor.SWAGKEYS, BASE.plusSeconds(i * 60L), SummaryStatus.DONE);
      item(n, 0, "상품 " + i, SaleCategory.KEYBOARD);
    }

    String body =
        mockMvc
            .perform(get("/api/sales"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items", hasSize(20)))
            .andExpect(jsonPath("$.items[0].productName").value("상품 24"))
            .andExpect(jsonPath("$.items[19].productName").value("상품 5"))
            .andExpect(jsonPath("$.hasMore").value(true))
            .andReturn()
            .getResponse()
            .getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
    String cursor = com.jayway.jsonpath.JsonPath.read(body, "$.nextCursor");

    mockMvc
        .perform(get("/api/sales").param("cursor", cursor))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items", hasSize(5)))
        .andExpect(jsonPath("$.items[0].productName").value("상품 4"))
        .andExpect(jsonPath("$.items[4].productName").value("상품 0"))
        .andExpect(jsonPath("$.hasMore").value(false))
        .andExpect(jsonPath("$.nextCursor").value(nullValue()));
  }

  @Test
  @DisplayName("같은 공지의 건은 공지 안 순서로 나오고 쪽 경계에서도 빠지거나 겹치지 않는다")
  void sameNoticeKeepsPositionAcrossPages() throws Exception {
    Notice n = notice(KoreanVendor.SWAGKEYS, BASE, SummaryStatus.DONE);
    for (int i = 0; i < 22; i++) {
      item(n, i, "건 " + i, SaleCategory.KEYCAP);
    }
    String body =
        mockMvc
            .perform(get("/api/sales"))
            .andExpect(jsonPath("$.items[0].productName").value("건 0"))
            .andExpect(jsonPath("$.items[19].productName").value("건 19"))
            .andReturn()
            .getResponse()
            .getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
    String cursor = com.jayway.jsonpath.JsonPath.read(body, "$.nextCursor");

    mockMvc
        .perform(get("/api/sales").param("cursor", cursor))
        .andExpect(jsonPath("$.items", hasSize(2)))
        .andExpect(jsonPath("$.items[0].productName").value("건 20"))
        .andExpect(jsonPath("$.items[1].productName").value("건 21"));
  }

  @Test
  @DisplayName("공지 시각에 마이크로초가 남아 있어도 커서가 건을 건너뛰거나 겹치지 않는다")
  void microsecondsDoNotBreakCursor() throws Exception {
    Notice n = notice(KoreanVendor.SWAGKEYS, BASE, SummaryStatus.DONE);
    for (int i = 0; i < 21; i++) {
      item(n, i, "건 " + i, SaleCategory.KEYCAP);
    }
    jdbcTemplate.update(
        "update sale_item set posted_at = posted_at + (id % 3) * interval '100 microseconds'"
            + " where notice_id = ?",
        n.getId());
    String body =
        mockMvc
            .perform(get("/api/sales"))
            .andExpect(jsonPath("$.items", hasSize(20)))
            .andReturn()
            .getResponse()
            .getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
    String cursor = com.jayway.jsonpath.JsonPath.read(body, "$.nextCursor");

    mockMvc
        .perform(get("/api/sales").param("cursor", cursor))
        .andExpect(jsonPath("$.items", hasSize(1)))
        .andExpect(jsonPath("$.items[0].productName").value("건 20"));
  }

  @Test
  @DisplayName("판매처와 분류로 거른다")
  void filtersByVendorAndCategory() throws Exception {
    Notice swag = notice(KoreanVendor.SWAGKEYS, BASE, SummaryStatus.DONE);
    Notice geon = notice(KoreanVendor.GEONWORKS, BASE.plusSeconds(60), SummaryStatus.DONE);
    item(swag, 0, "스웩 키캡", SaleCategory.KEYCAP);
    item(geon, 0, "건 키보드", SaleCategory.KEYBOARD);
    item(geon, 1, "건 키캡", SaleCategory.KEYCAP);

    mockMvc
        .perform(get("/api/sales").param("vendor", "geonworks").param("category", "keycap"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items[*].productName", contains("건 키캡")))
        .andExpect(jsonPath("$.items[0].vendor").value("GEONWORKS"));
    mockMvc
        .perform(get("/api/sales").param("category", "keycap"))
        .andExpect(jsonPath("$.items[*].productName", contains("건 키캡", "스웩 키캡")));
  }

  @Test
  @DisplayName("숨김, OTHER 건은 내보내지 않는다")
  void excludesHiddenAndOther() throws Exception {
    Notice done = notice(KoreanVendor.SWAGKEYS, BASE, SummaryStatus.DONE);
    item(done, 0, "보이는 건", SaleCategory.KEYBOARD);
    SaleItem hidden = item(done, 1, "숨긴 건", SaleCategory.KEYBOARD);
    hidden.setHidden(true);
    saleItemRepository.save(hidden);
    item(done, 2, "기타 건", SaleCategory.OTHER);

    mockMvc
        .perform(get("/api/sales"))
        .andExpect(jsonPath("$.items[*].productName", contains("보이는 건")));
    mockMvc
        .perform(get("/api/sales/sitemap"))
        .andExpect(jsonPath("$", hasSize(1)));
    mockMvc.perform(get("/api/sales/" + hidden.getId())).andExpect(status().isNotFound());
  }

  @Test
  @DisplayName("잘못된 필터와 커서는 400")
  void rejectsBadParams() throws Exception {
    mockMvc.perform(get("/api/sales").param("vendor", "foo")).andExpect(status().isBadRequest());
    mockMvc.perform(get("/api/sales").param("vendor", "klc_playground"))
        .andExpect(status().isBadRequest());
    mockMvc.perform(get("/api/sales").param("category", "other")).andExpect(status().isBadRequest());
    mockMvc.perform(get("/api/sales").param("cursor", "abc")).andExpect(status().isBadRequest());
    mockMvc.perform(get("/api/sales/past").param("cursor", "1_abc")).andExpect(status().isBadRequest());
    mockMvc.perform(get("/api/sales").param("cursor", "1_x")).andExpect(status().isBadRequest());
    mockMvc.perform(get("/api/sales").param("cursor", "1_2_3")).andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("카드는 대표 사진이 보관됐을 때만 사진 주소를 주고 시각은 HH:mm으로 준다")
  void cardImageOnlyWhenArchived() throws Exception {
    Notice n = notice(KoreanVendor.SWAGKEYS, BASE, SummaryStatus.DONE);
    attachment(n, 7001L, 0, ArchiveStatus.DONE);
    attachment(n, 7002L, 1, ArchiveStatus.FAILED);
    item(n, 0, "사진 있음", SaleCategory.KEYBOARD, List.of(7001L));
    item(n, 1, "사진 실패", SaleCategory.KEYBOARD, List.of(7002L));
    item(n, 2, "사진 없음", SaleCategory.KEYBOARD);

    mockMvc
        .perform(get("/api/sales"))
        .andExpect(jsonPath("$.items[0].noticeId").value(n.getId()))
        .andExpect(jsonPath("$.items[0].thumbUrl").value("https://img.test/notices/7001-640.webp"))
        .andExpect(jsonPath("$.items[0].largeUrl").value("https://img.test/notices/7001-1280.webp"))
        .andExpect(jsonPath("$.items[0].imageUrl").value("https://img.test/notices/7001.webp"))
        .andExpect(jsonPath("$.items[0].startDate").value("2026-11-10"))
        .andExpect(jsonPath("$.items[0].startTime").value("20:30"))
        .andExpect(jsonPath("$.items[0].endDate").value("2026-11-20"))
        .andExpect(jsonPath("$.items[0].endTime").value(nullValue()))
        .andExpect(jsonPath("$.items[0].summaryFirstLine").value("첫 줄"))
        .andExpect(jsonPath("$.items[0].postedAt").value("2026-11-01T00:00:00Z"))
        .andExpect(jsonPath("$.items[1].thumbUrl").value(nullValue()))
        .andExpect(jsonPath("$.items[1].largeUrl").value(nullValue()))
        .andExpect(jsonPath("$.items[1].imageUrl").value(nullValue()))
        .andExpect(jsonPath("$.items[2].thumbUrl").value(nullValue()));
  }

  @Test
  @DisplayName("상세는 사진과 링크를 준다")
  void detailHasImagesAndLinks() throws Exception {
    Notice n = notice(KoreanVendor.SWAGKEYS, BASE, SummaryStatus.DONE);
    attachment(n, 7, 0, ArchiveStatus.DONE);
    attachment(n, 8, 1, ArchiveStatus.FAILED);
    attachment(n, 9, 2, ArchiveStatus.DONE);
    SaleItem saved = item(n, 0, "상세 건", SaleCategory.KEYCAP, List.of(9L, 8L, 7L));

    mockMvc
        .perform(get("/api/sales/" + saved.getId()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.productName").value("상세 건"))
        .andExpect(jsonPath("$.summaryLines", contains("첫 줄", "둘째 줄")))
        .andExpect(jsonPath("$.shippingNote").value("12월 배송"))
        .andExpect(jsonPath("$.links[0].text").value("구매"))
        .andExpect(jsonPath("$.links[0].url").value("https://shop.test/a"))
        .andExpect(jsonPath("$.images", hasSize(2)))
        .andExpect(jsonPath("$.images[0].url").value("https://img.test/notices/9.webp"))
        .andExpect(jsonPath("$.images[0].largeUrl").value("https://img.test/notices/9-1280.webp"))
        .andExpect(jsonPath("$.images[0].thumbUrl").value("https://img.test/notices/9-640.webp"))
        .andExpect(jsonPath("$.images[1].url").value("https://img.test/notices/7.webp"))
        .andExpect(jsonPath("$.thumbUrl").value("https://img.test/notices/9-640.webp"));
  }

  @Test
  @DisplayName("하이라이트는 고른 이유와 판매 카드(id, 큰 사진 주소)를 준다")
  void highlightsHavePickWithImage() throws Exception {
    clock.set(Instant.parse("2026-11-05T00:00:00Z"));
    Notice n = notice(KoreanVendor.SWAGKEYS, BASE, SummaryStatus.DONE);
    attachment(n, 7101L, 0, ArchiveStatus.DONE);
    SaleItem saved = item(n, 0, "하이라이트 건", SaleCategory.KEYBOARD, List.of(7101L));

    mockMvc
        .perform(get("/api/sales/highlights"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.picks", hasSize(1)))
        .andExpect(jsonPath("$.picks[0].reason").value("UPCOMING"))
        .andExpect(jsonPath("$.picks[0].sale.id").value(saved.getId()))
        .andExpect(jsonPath("$.picks[0].sale.imageUrl").value("https://img.test/notices/7101.webp"));
  }

  @Test
  @DisplayName("없는 판매 건은 404")
  void unknownIs404() throws Exception {
    mockMvc.perform(get("/api/sales/999999999")).andExpect(status().isNotFound());
  }

  @Test
  @DisplayName("사이트맵은 id와 요약 시각")
  void sitemapHasIdAndSummarizedAt() throws Exception {
    Notice n = notice(KoreanVendor.SWAGKEYS, BASE, SummaryStatus.DONE);
    SaleItem saved = item(n, 0, "건", SaleCategory.KEYBOARD);

    mockMvc
        .perform(get("/api/sales/sitemap"))
        .andExpect(status().isOk())
        .andExpect(
            content()
                .json(
                    "[{\"id\": %d, \"lastModified\": \"2026-11-05T09:30:00Z\"}]"
                        .formatted(saved.getId()),
                    JsonCompareMode.STRICT));
  }

  /** 한국 시간 2026-11-13 12:00 */
  private static final Instant NOW = Instant.parse("2026-11-13T03:00:00Z");

  private SaleItem scheduled(
      String name,
      SalePhase phase,
      Instant postedAt,
      LocalDate startDate,
      LocalDate endDate,
      SaleCategory category,
      KoreanVendor vendor) {
    Notice n = notice(vendor, postedAt, SummaryStatus.DONE);
    return saleItemRepository.save(
        SaleItem.create(
            n,
            0,
            new SaleItemDraft(
                name,
                category,
                SaleType.SALE,
                phase,
                startDate,
                null,
                endDate,
                null,
                null,
                List.of("줄"),
                List.of(),
                List.of())));
  }

  private SaleItem scheduled(
      String name, SalePhase phase, Instant postedAt, LocalDate startDate, LocalDate endDate) {
    return scheduled(name, phase, postedAt, startDate, endDate, SaleCategory.KEYBOARD, KoreanVendor.SWAGKEYS);
  }

  private static Instant daysAgo(int days) {
    return NOW.minus(java.time.Duration.ofDays(days));
  }

  @Test
  @DisplayName("일정 목록은 끝나지 않은 판매만: 마감, 예정 시작, 7일, 30일 경계")
  void scheduleBoundaries() throws Exception {
    clock.set(NOW);
    LocalDate today = LocalDate.of(2026, 11, 13);
    scheduled("어제 마감", SalePhase.OPEN, daysAgo(3), today.minusDays(5), today.minusDays(1));
    scheduled("오늘 날짜만 마감", SalePhase.OPEN, daysAgo(3), today.minusDays(5), today);
    scheduled("마감 없고 미래 시작", SalePhase.ANNOUNCE, daysAgo(40), today.plusDays(3), null);
    scheduled("8일 전 시작", SalePhase.OPEN, daysAgo(20), today.minusDays(8), null);
    scheduled("6일 전 시작", SalePhase.OPEN, daysAgo(20), today.minusDays(6), null);
    scheduled("공지 8일 전 시작 없음", SalePhase.OPEN, daysAgo(8), null, null);
    scheduled("공지 6일 전 시작 없음", SalePhase.OPEN, daysAgo(6), null, null);
    scheduled("31일 전 일정 미정", SalePhase.ANNOUNCE, daysAgo(31), null, null);
    scheduled("29일 전 일정 미정", SalePhase.ANNOUNCE, daysAgo(29), null, null);

    mockMvc
        .perform(get("/api/sales/schedule"))
        .andExpect(status().isOk())
        .andExpect(
            jsonPath(
                "$.items[*].productName",
                org.hamcrest.Matchers.containsInAnyOrder(
                    "오늘 날짜만 마감",
                    "마감 없고 미래 시작",
                    "6일 전 시작",
                    "공지 6일 전 시작 없음",
                    "29일 전 일정 미정")))
        .andExpect(jsonPath("$.items[0].noticeId").isNumber());
  }

  @Test
  @DisplayName("일정 목록은 시작이 지난 예고도 시작 뒤 7일 안이면 넣고, 공지가 120일보다 옛이어도 넣는다")
  void scheduleIncludesAnnounceWithPassedStart() throws Exception {
    clock.set(NOW);
    LocalDate today = LocalDate.of(2026, 11, 13);
    scheduled("예고 3일 전 시작", SalePhase.ANNOUNCE, daysAgo(10), today.minusDays(3), null);
    scheduled("예고 8일 전 시작", SalePhase.ANNOUNCE, daysAgo(10), today.minusDays(8), null);
    scheduled("옛 공지 3일 전 시작", SalePhase.OPEN, daysAgo(130), today.minusDays(3), null);

    mockMvc
        .perform(get("/api/sales/schedule"))
        .andExpect(
            jsonPath(
                "$.items[*].productName",
                org.hamcrest.Matchers.containsInAnyOrder("예고 3일 전 시작", "옛 공지 3일 전 시작")));
  }

  @Test
  @DisplayName("일정 목록은 오래전에 올라왔어도 마감이 남았으면 포함하고 최근 공지 순이다")
  void scheduleKeepsOldPostWithFutureEnd() throws Exception {
    clock.set(NOW);
    LocalDate today = LocalDate.of(2026, 11, 13);
    scheduled("옛 공지 긴 판매", SalePhase.OPEN, daysAgo(200), today.minusDays(100), today.plusDays(5));
    scheduled("최근", SalePhase.OPEN, daysAgo(1), null, null);

    mockMvc
        .perform(get("/api/sales/schedule"))
        .andExpect(jsonPath("$.items[*].productName", contains("최근", "옛 공지 긴 판매")));
  }

  @Test
  @DisplayName("일정 목록도 판매처·분류로 거르고 숨김·OTHER는 뺀다")
  void scheduleFiltersAndExcludes() throws Exception {
    clock.set(NOW);
    scheduled("스웩 키보드", SalePhase.OPEN, daysAgo(1), null, null);
    scheduled("건 키캡", SalePhase.OPEN, daysAgo(2), null, null, SaleCategory.KEYCAP, KoreanVendor.GEONWORKS);
    scheduled("기타", SalePhase.OPEN, daysAgo(2), null, null, SaleCategory.OTHER, KoreanVendor.GEONWORKS);
    SaleItem hidden = scheduled("숨김", SalePhase.OPEN, daysAgo(2), null, null);
    hidden.setHidden(true);
    saleItemRepository.save(hidden);

    mockMvc
        .perform(get("/api/sales/schedule").param("vendor", "geonworks").param("category", "keycap"))
        .andExpect(jsonPath("$.items[*].productName", contains("건 키캡")));
    mockMvc
        .perform(get("/api/sales/schedule"))
        .andExpect(jsonPath("$.items[*].productName", contains("스웩 키보드", "건 키캡")));
  }

  @Test
  @DisplayName("일정 목록은 300건까지만 준다")
  void scheduleCapsAtThreeHundred() throws Exception {
    clock.set(NOW);
    for (int i = 0; i < 305; i++) {
      scheduled("건 " + i, SalePhase.OPEN, NOW.minusSeconds(60L * (i + 1)), null, null);
    }

    mockMvc
        .perform(get("/api/sales/schedule"))
        .andExpect(jsonPath("$.items", hasSize(300)))
        .andExpect(jsonPath("$.items[0].productName").value("건 0"))
        .andExpect(jsonPath("$.items[299].productName").value("건 299"));
  }

  @Test
  @DisplayName("일정 목록의 잘못된 필터는 400")
  void scheduleRejectsBadParams() throws Exception {
    mockMvc.perform(get("/api/sales/schedule").param("vendor", "foo")).andExpect(status().isBadRequest());
    mockMvc.perform(get("/api/sales/schedule").param("category", "other")).andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("지난 판매는 일정 목록에 들지 않는 건 전부이고, 두 목록을 합치면 전체가 된다")
  void pastIsComplementOfSchedule() throws Exception {
    clock.set(NOW);
    LocalDate today = LocalDate.of(2026, 11, 13);
    scheduled("어제 마감", SalePhase.OPEN, daysAgo(3), today.minusDays(5), today.minusDays(1));
    scheduled("오늘 날짜만 마감", SalePhase.OPEN, daysAgo(3), today.minusDays(5), today);
    scheduled("마감 없고 미래 시작", SalePhase.ANNOUNCE, daysAgo(40), today.plusDays(3), null);
    scheduled("8일 전 시작", SalePhase.OPEN, daysAgo(20), today.minusDays(8), null);
    scheduled("6일 전 시작", SalePhase.OPEN, daysAgo(20), today.minusDays(6), null);
    scheduled("공지 8일 전 시작 없음", SalePhase.OPEN, daysAgo(8), null, null);
    scheduled("공지 6일 전 시작 없음", SalePhase.OPEN, daysAgo(6), null, null);
    scheduled("31일 전 일정 미정", SalePhase.ANNOUNCE, daysAgo(31), null, null);
    scheduled("29일 전 일정 미정", SalePhase.ANNOUNCE, daysAgo(29), null, null);
    scheduled("아주 옛 공지", SalePhase.OPEN, daysAgo(400), null, null);

    mockMvc
        .perform(get("/api/sales/past"))
        .andExpect(status().isOk())
        .andExpect(
            jsonPath(
                "$.items[*].productName",
                org.hamcrest.Matchers.containsInAnyOrder(
                    "어제 마감", "8일 전 시작", "공지 8일 전 시작 없음", "31일 전 일정 미정", "아주 옛 공지")))
        .andExpect(jsonPath("$.hasMore").value(false))
        .andExpect(jsonPath("$.nextCursor").value(nullValue()));

    List<String> past = names(mockMvc.perform(get("/api/sales/past")).andReturn().getResponse().getContentAsString());
    List<String> schedule = names(mockMvc.perform(get("/api/sales/schedule")).andReturn().getResponse().getContentAsString());
    org.assertj.core.api.Assertions.assertThat(past).doesNotContainAnyElementsOf(schedule);
    org.assertj.core.api.Assertions.assertThat(past.size() + schedule.size()).isEqualTo(10);
  }

  private static List<String> names(String json) {
    java.util.regex.Matcher m =
        java.util.regex.Pattern.compile("\"productName\":\"([^\"]*)\"").matcher(json);
    List<String> out = new java.util.ArrayList<>();
    while (m.find()) {
      out.add(m.group(1));
    }
    return out;
  }

  @Test
  @DisplayName("지난 판매는 마감, 시작, 공지 시각 순서로 기준을 잡아 지난 시각 내림차순")
  void pastOrdering() throws Exception {
    clock.set(NOW);
    LocalDate today = LocalDate.of(2026, 11, 13);
    // 지난 시각: 마감 어제 23:59:59 KST
    scheduled("마감 어제", SalePhase.OPEN, daysAgo(50), today.minusDays(30), today.minusDays(1));
    // 시작 10일 전 00:00 KST (마감 없음)
    scheduled("시작 10일 전", SalePhase.OPEN, daysAgo(60), today.minusDays(10), null);
    // 마감 20일 전 (시작이 더 늦어도 마감이 기준)
    scheduled("마감 20일 전", SalePhase.OPEN, daysAgo(70), today.minusDays(40), today.minusDays(20));
    // 공지 40일 전 (시작·마감 없음)
    scheduled("공지 40일 전", SalePhase.ANNOUNCE, daysAgo(40), null, null);

    mockMvc
        .perform(get("/api/sales/past"))
        .andExpect(
            jsonPath("$.items[*].productName")
                .value(contains("마감 어제", "시작 10일 전", "마감 20일 전", "공지 40일 전")));
  }

  @Test
  @DisplayName("지난 판매는 30건씩 커서로 이어 받고 겹치거나 빠지지 않는다")
  void pastPaging() throws Exception {
    clock.set(NOW);
    for (int i = 0; i < 29; i++) {
      scheduled("건 " + i, SalePhase.OPEN, daysAgo(100 + i), null, null);
    }
    // 지난 시각이 같은 두 건이 쪽 경계(30번째·31번째)에 걸친다: id 큰 쪽이 먼저
    Instant same = daysAgo(200);
    scheduled("같은 시각 먼저 저장", SalePhase.OPEN, same, null, null);
    scheduled("같은 시각 나중 저장", SalePhase.OPEN, same, null, null);
    for (int i = 0; i < 5; i++) {
      scheduled("옛 건 " + i, SalePhase.OPEN, daysAgo(300 + i), null, null);
    }

    String first =
        mockMvc
            .perform(get("/api/sales/past"))
            .andExpect(jsonPath("$.items", hasSize(30)))
            .andExpect(jsonPath("$.hasMore").value(true))
            .andReturn()
            .getResponse()
            .getContentAsString();
    java.util.regex.Matcher m =
        java.util.regex.Pattern.compile("\"nextCursor\":\"([^\"]+)\"").matcher(first);
    org.assertj.core.api.Assertions.assertThat(m.find()).isTrue();
    String cursor = m.group(1);

    String second =
        mockMvc
            .perform(get("/api/sales/past").param("cursor", cursor))
            .andExpect(jsonPath("$.items", hasSize(6)))
            .andExpect(jsonPath("$.items[0].productName").value("같은 시각 먼저 저장"))
            .andExpect(jsonPath("$.hasMore").value(false))
            .andExpect(jsonPath("$.nextCursor").value(nullValue()))
            .andReturn()
            .getResponse()
            .getContentAsString();
    List<String> all = new java.util.ArrayList<>(names(first));
    all.addAll(names(second));
    org.assertj.core.api.Assertions.assertThat(all).hasSize(36).doesNotHaveDuplicates();
    org.assertj.core.api.Assertions.assertThat(all.getFirst()).isEqualTo("건 0");
    org.assertj.core.api.Assertions.assertThat(all.get(29)).isEqualTo("같은 시각 나중 저장");
    org.assertj.core.api.Assertions.assertThat(all.get(30)).isEqualTo("같은 시각 먼저 저장");
  }

  @Test
  @DisplayName("지난 판매에도 필터가 적용되고 숨김·OTHER는 뺀다")
  void pastFiltersAndExcludes() throws Exception {
    clock.set(NOW);
    scheduled("스웩 키보드", SalePhase.OPEN, daysAgo(50), null, null);
    scheduled("건 키캡", SalePhase.OPEN, daysAgo(51), null, null, SaleCategory.KEYCAP, KoreanVendor.GEONWORKS);
    scheduled("기타", SalePhase.OPEN, daysAgo(52), null, null, SaleCategory.OTHER, KoreanVendor.GEONWORKS);
    SaleItem hidden = scheduled("숨김", SalePhase.OPEN, daysAgo(53), null, null);
    jdbcTemplate.update("update sale_item set hidden = true where id = ?", hidden.getId());

    mockMvc
        .perform(get("/api/sales/past").param("vendor", "geonworks").param("category", "keycap"))
        .andExpect(jsonPath("$.items[*].productName").value(contains("건 키캡")));
    mockMvc
        .perform(get("/api/sales/past"))
        .andExpect(jsonPath("$.items[*].productName").value(contains("스웩 키보드", "건 키캡")));
  }

  @Test
  @DisplayName("다시 요약을 기다리거나 다시 요약에 실패한 공지의 판매 건도 계속 내보낸다")
  void keepsItemsOfNoticeBeingResummarized() throws Exception {
    clock.set(NOW);
    Notice waiting = notice(KoreanVendor.SWAGKEYS, daysAgo(1), SummaryStatus.DONE);
    attachment(waiting, 7201L, 0, ArchiveStatus.DONE);
    SaleItem active = item(waiting, 0, "다시 요약 대기 건", SaleCategory.KEYBOARD, List.of(7201L));
    SaleItem ended = scheduled("다시 요약 실패 건", SalePhase.OPEN, daysAgo(50), null, null);
    noticeRepository.updateSummaryStatus(waiting.getId(), SummaryStatus.PENDING);
    noticeRepository.updateSummaryStatus(ended.getNotice().getId(), SummaryStatus.FAILED);

    mockMvc
        .perform(get("/api/sales"))
        .andExpect(jsonPath("$.items[*].productName", contains("다시 요약 대기 건", "다시 요약 실패 건")));
    mockMvc.perform(get("/api/sales/" + active.getId())).andExpect(status().isOk());
    mockMvc.perform(get("/api/sales/" + ended.getId())).andExpect(status().isOk());
    mockMvc
        .perform(get("/api/sales/schedule"))
        .andExpect(jsonPath("$.items[*].productName", contains("다시 요약 대기 건")));
    mockMvc
        .perform(get("/api/sales/past"))
        .andExpect(jsonPath("$.items[*].productName").value(contains("다시 요약 실패 건")));
    mockMvc
        .perform(get("/api/sales/highlights"))
        .andExpect(jsonPath("$.picks", hasSize(1)))
        .andExpect(jsonPath("$.picks[0].sale.id").value(active.getId()));
    mockMvc
        .perform(get("/api/sales/sitemap"))
        .andExpect(
            content()
                .json(
                    ("[{\"id\": %d, \"lastModified\": \"2026-11-05T09:30:00Z\"},"
                            + " {\"id\": %d, \"lastModified\": \"2026-11-05T09:30:00Z\"}]")
                        .formatted(active.getId(), ended.getId()),
                    JsonCompareMode.STRICT));
  }

  @Test
  @DisplayName("지난 판매의 잘못된 필터·커서는 400")
  void pastRejectsBadParams() throws Exception {
    mockMvc.perform(get("/api/sales/past").param("vendor", "foo")).andExpect(status().isBadRequest());
    mockMvc.perform(get("/api/sales/past").param("category", "other")).andExpect(status().isBadRequest());
    mockMvc.perform(get("/api/sales/past").param("cursor", "abc")).andExpect(status().isBadRequest());
    mockMvc.perform(get("/api/sales/past").param("cursor", "1_abc")).andExpect(status().isBadRequest());
  }

  @TestConfiguration(proxyBeanMethods = false)
  static class ClockConfig {
    @Bean
    @Primary
    MutableClock mutableClock() {
      return new MutableClock();
    }
  }
}

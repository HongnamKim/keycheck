package com.keycheck.notice.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.keycheck.TestcontainersConfiguration;
import com.keycheck.notice.domain.Notice;
import com.keycheck.notice.domain.NoticeAttachment;
import com.keycheck.notice.domain.NoticeOrigin;
import com.keycheck.notice.domain.SaleCategory;
import com.keycheck.notice.domain.SaleItem;
import com.keycheck.notice.domain.SalePhase;
import com.keycheck.notice.domain.SaleType;
import com.keycheck.notice.dto.SaleItemDraft;
import com.keycheck.post.domain.KoreanVendor;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Limit;

// ddl-auto=validate: Flyway로 만든 스키마와 엔티티 매핑이 일치하는지도 함께 검증한다.
@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=validate")
@Import(TestcontainersConfiguration.class)
class NoticeRepositoryTest {
  @Autowired private NoticeRepository noticeRepository;
  @Autowired private NoticeAttachmentRepository attachmentRepository;
  @Autowired private SaleItemRepository saleItemRepository;
  @Autowired private EntityManager em;

  private Notice notice(long sourceId, KoreanVendor vendor, String postedAt, boolean hasText) {
    return Notice.create(
        sourceId, null, 1L, vendor, NoticeOrigin.BOT, Instant.parse(postedAt), "본문", hasText, "{}");
  }

  @Test
  @DisplayName("원래 메시지 id는 한 번만 저장된다")
  void 원래_메시지_id는_한_번만_저장된다() {
    noticeRepository.saveAndFlush(
        notice(100L, KoreanVendor.SWAGKEYS, "2026-11-06T10:00:00Z", true));

    assertThat(noticeRepository.existsBySourceMessageId(100L)).isTrue();
    assertThatThrownBy(
            () ->
                noticeRepository.saveAndFlush(
                    notice(100L, KoreanVendor.SWAGKEYS, "2026-11-06T11:00:00Z", true)))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  @DisplayName("가장 최근 글 공지를 찾는다")
  void 가장_최근_글_공지를_찾는다() {
    noticeRepository.save(notice(1L, KoreanVendor.SWAGKEYS, "2026-11-06T10:00:00Z", true));
    Notice latest = noticeRepository.save(notice(2L, KoreanVendor.SWAGKEYS, "2026-11-06T10:30:00Z", true));
    Notice child = notice(3L, KoreanVendor.SWAGKEYS, "2026-11-06T10:31:00Z", false);
    child.setParent(latest);
    noticeRepository.save(child);
    noticeRepository.save(notice(4L, KoreanVendor.GEONWORKS, "2026-11-06T10:40:00Z", true));

    Instant before = Instant.parse("2026-11-06T10:45:00Z");
    var found =
        noticeRepository.findLatestTextNotice(
            KoreanVendor.SWAGKEYS, before.minusSeconds(24 * 3600), before);

    assertThat(found).isPresent();
    assertThat(found.get().getId()).isEqualTo(latest.getId());
  }

  @Test
  @DisplayName("24시간 밖의 공지는 찾지 않는다")
  void 하루_밖의_공지는_찾지_않는다() {
    noticeRepository.save(notice(1L, KoreanVendor.SWAGKEYS, "2026-11-05T09:00:00Z", true));

    Instant before = Instant.parse("2026-11-06T09:30:00Z");
    assertThat(
            noticeRepository.findLatestTextNotice(
                KoreanVendor.SWAGKEYS, before.minusSeconds(24 * 3600), before))
        .isEmpty();
  }

  @Test
  @DisplayName("첨부는 순서대로 읽힌다")
  void 첨부는_순서대로_읽힌다() {
    Notice notice = notice(1L, KoreanVendor.SWAGKEYS, "2026-11-06T10:00:00Z", true);
    notice.addAttachment(11L, 1, "b.png", "https://cdn/b.png");
    notice.addAttachment(10L, 0, "a.png", "https://cdn/a.png");
    noticeRepository.saveAndFlush(notice);
    em.clear();

    Notice read = noticeRepository.findById(notice.getId()).orElseThrow();

    assertThat(read.getAttachments()).extracting(NoticeAttachment::getId).containsExactly(10L, 11L);
  }

  @Test
  @DisplayName("판매 건은 공지를 지우면 같이 지워진다")
  void 판매_건은_공지를_지우면_같이_지워진다() {
    Notice notice = noticeRepository.save(notice(1L, KoreanVendor.SWAGKEYS, "2026-11-06T10:00:00Z", true));
    saleItemRepository.saveAndFlush(
        SaleItem.create(
            notice,
            0,
            new SaleItemDraft(
                "상품",
                SaleCategory.KEYBOARD,
                SaleType.SALE,
                SalePhase.OPEN,
                null,
                null,
                null,
                null,
                null,
                List.of("요약"),
                List.of(),
                List.of(10L))));
    em.clear();

    em.createQuery("delete from Notice n where n.id = :id").setParameter("id", notice.getId()).executeUpdate();
    em.clear();

    assertThat(saleItemRepository.count()).isZero();
  }

  @Test
  @DisplayName("보관할 첨부는 실패가 적은 것만 오래된 공지부터 읽는다")
  void 보관할_첨부는_오래된_공지부터_읽는다() {
    Notice old = notice(1L, KoreanVendor.SWAGKEYS, "2026-11-06T10:00:00Z", true);
    old.addAttachment(10L, 0, "a.png", "u");
    Notice recent = notice(2L, KoreanVendor.SWAGKEYS, "2026-11-06T11:00:00Z", true);
    recent.addAttachment(20L, 0, "b.png", "u");
    recent.addAttachment(21L, 1, "c.png", "u");
    noticeRepository.saveAndFlush(old);
    noticeRepository.saveAndFlush(recent);
    attachmentRepository.incrementFailures(21L);
    attachmentRepository.incrementFailures(21L);
    attachmentRepository.incrementFailures(21L);
    attachmentRepository.markArchived(20L, com.keycheck.notice.domain.ArchiveStatus.DONE);
    em.clear();

    assertThat(attachmentRepository.findArchiveTargets(3, Limit.of(10)))
        .extracting(NoticeAttachment::getId)
        .containsExactly(10L);
    attachmentRepository.decrementFailures(21L);
    em.clear();
    assertThat(attachmentRepository.findArchiveTargets(3, Limit.of(10)))
        .extracting(NoticeAttachment::getId)
        .containsExactly(10L, 21L);
  }
}

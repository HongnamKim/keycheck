package com.keycheck.notice.repository;

import com.keycheck.notice.domain.SaleItem;
import com.keycheck.notice.dto.SaleSitemapRow;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

public interface SaleItemRepository extends JpaRepository<SaleItem, Long> {
  /** 공지의 판매 건을 모두 지운다 (다시 요약하기 전에) */
  @Transactional
  @Modifying
  @Query("delete from SaleItem s where s.notice.id = :noticeId")
  void deleteByNoticeId(Long noticeId);

  /**
   * 내보내는 판매 건 목록: 숨김·OTHER·요약 전 공지의 건을 뺀다. 공지 시각 내림차순, 같으면 id 오름차순(한 공지의 건은 순서대로 저장돼 id 순서가
   * 곧 공지 안 순서다). 공지 시각은 밀리초로 잘라 비교한다(커서가 밀리초라 마이크로초가 남은 값도 건너뛰거나 겹치지 않게).
   *
   * @param vendor null이면 모든 판매처 (이름 문자열)
   * @param category null이면 모든 분류 (이름 문자열)
   * @param cursorAt 이 시각(밀리초)보다 앞선 건, 같으면 cursorId보다 id가 큰 건만. 첫 쪽은 먼 미래
   */
  @Query(
      nativeQuery = true,
      value =
          "select s.* from sale_item s join notice n on n.id = s.notice_id"
              + " where s.hidden = false and s.category <> 'OTHER' and n.summary_status = 'DONE'"
              + " and (cast(:vendor as text) is null or s.vendor = cast(:vendor as text))"
              + " and (cast(:category as text) is null or s.category = cast(:category as text))"
              + " and (date_trunc('milliseconds', s.posted_at) < cast(:cursorAt as timestamptz)"
              + " or (date_trunc('milliseconds', s.posted_at) = cast(:cursorAt as timestamptz)"
              + " and s.id > :cursorId))"
              + " order by date_trunc('milliseconds', s.posted_at) desc, s.id asc")
  List<SaleItem> findPage(
      String vendor, String category, Instant cursorAt, long cursorId, Limit limit);

  /** 내보내는 판매 건 하나. 숨김·OTHER·요약 전 공지의 건이면 empty */
  @Query(
      "select s from SaleItem s join s.notice n where s.id = :id and s.hidden = false"
          + " and s.category <> com.keycheck.notice.domain.SaleCategory.OTHER"
          + " and n.summaryStatus = com.keycheck.post.domain.SummaryStatus.DONE")
  Optional<SaleItem> findVisible(Long id);

  /**
   * 하이라이트 후보: 내보내는 판매 건 중 공지가 postedFrom 이후이거나 시작·마감 날짜가 from~to 안인 것. 시각 계산은 서비스가 한국 시각으로 한다.
   */
  @Query(
      "select s from SaleItem s join s.notice n where s.hidden = false"
          + " and s.category <> com.keycheck.notice.domain.SaleCategory.OTHER"
          + " and n.summaryStatus = com.keycheck.post.domain.SummaryStatus.DONE"
          + " and (s.postedAt >= :postedFrom or s.startDate between :from and :to"
          + " or s.endDate between :from and :to)")
  List<SaleItem> findHighlightCandidates(Instant postedFrom, LocalDate from, LocalDate to);

  /**
   * 판매 일정 후보: 내보내는 판매 건 중 공지가 postedFrom 이후이거나 시작 날짜가 startFrom 이후이거나 마감 날짜가 from 이후인 것. 끝났는지는 서비스가 한국 시각으로 가린다.
   *
   * @param vendor null이면 모든 판매처 (이름 문자열)
   * @param category null이면 모든 분류 (이름 문자열)
   */
  @Query(
      nativeQuery = true,
      value =
          "select s.* from sale_item s join notice n on n.id = s.notice_id"
              + " where s.hidden = false and s.category <> 'OTHER' and n.summary_status = 'DONE'"
              + " and (cast(:vendor as text) is null or s.vendor = cast(:vendor as text))"
              + " and (cast(:category as text) is null or s.category = cast(:category as text))"
              + " and (s.posted_at >= cast(:postedFrom as timestamptz)"
              + " or s.start_date >= cast(:startFrom as date) or s.end_date >= cast(:from as date))"
              + " order by s.posted_at desc, s.id asc")
  List<SaleItem> findScheduleCandidates(
      String vendor, String category, Instant postedFrom, LocalDate startFrom, LocalDate from);

  /**
   * 보이는 판매 건 전부(지난 판매용). 끝났는지와 정렬·쪽 나눔은 서비스가 한다.
   *
   * @param vendor null이면 모든 판매처 (이름 문자열)
   * @param category null이면 모든 분류 (이름 문자열)
   */
  @Query(
      nativeQuery = true,
      value =
          "select s.* from sale_item s join notice n on n.id = s.notice_id"
              + " where s.hidden = false and s.category <> 'OTHER' and n.summary_status = 'DONE'"
              + " and (cast(:vendor as text) is null or s.vendor = cast(:vendor as text))"
              + " and (cast(:category as text) is null or s.category = cast(:category as text))")
  List<SaleItem> findAllVisible(String vendor, String category);

  /** 사이트맵: 내보내는 판매 건의 id와 공지 요약 시각, 목록과 같은 순서 */
  @Query(
      "select s.id as id, n.summarizedAt as summarizedAt from SaleItem s join s.notice n"
          + " where s.hidden = false and s.category <> com.keycheck.notice.domain.SaleCategory.OTHER"
          + " and n.summaryStatus = com.keycheck.post.domain.SummaryStatus.DONE"
          + " order by s.postedAt desc, s.id asc")
  List<SaleSitemapRow> findSitemapRows();

  /** 날짜 줄: 공지가 postedFrom 이후인 판매 건 중 숨김·OTHER를 뺀 수. 요약 상태는 묻지 않는다(판매 건은 요약이 끝나야 생긴다) */
  @Query(
      "select count(s) from SaleItem s where s.hidden = false"
          + " and s.category <> com.keycheck.notice.domain.SaleCategory.OTHER"
          + " and s.postedAt >= :postedFrom")
  long countPostedSince(Instant postedFrom);
}

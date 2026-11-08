package com.keycheck.notice.repository;

import com.keycheck.notice.domain.Notice;
import com.keycheck.post.domain.KoreanVendor;
import com.keycheck.post.domain.SummaryStatus;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

public interface NoticeRepository extends JpaRepository<Notice, Long> {
  boolean existsBySourceMessageId(long sourceMessageId);

  /**
   * 딸린 메시지가 붙을 글 공지: 같은 판매처의 글이 있는(딸린 메시지가 아닌) 공지 중 from 이상 before 미만에서 가장 최근 것
   *
   * @return 없으면 empty
   */
  @Query(
      "select n from Notice n where n.vendor = :vendor and n.parent is null and n.hasText = true"
          + " and n.postedAt >= :from and n.postedAt < :before order by n.postedAt desc, n.id desc"
          + " limit 1")
  Optional<Notice> findLatestTextNotice(KoreanVendor vendor, Instant from, Instant before);

  /** 요약 결과와 함께 요약 상태를 끝으로 바꾼다. 다른 컬럼은 건드리지 않는다. */
  @Transactional
  @Modifying
  @Query(
      "update Notice n set n.summaryStatus = :status, n.summarizedBy = :summarizedBy,"
          + " n.summarizedAt = :at where n.id = :id")
  void markSummarized(Long id, SummaryStatus status, String summarizedBy, Instant at);

  @Transactional
  @Modifying
  @Query("update Notice n set n.summaryStatus = :status where n.id = :id")
  void updateSummaryStatus(Long id, SummaryStatus status);

  /**
   * 요약할 글 공지: 요약 대기이고 글이 있는 글 공지 중 postedBefore 이전에 올라왔고, 자기와 딸린 메시지의 첨부 중 보관 대기(PENDING)가
   * 없는 것. 오래된 순
   */
  @Query(
      "select n from Notice n where n.summaryStatus = com.keycheck.post.domain.SummaryStatus.PENDING"
          + " and n.parent is null and n.hasText = true and n.postedAt <= :postedBefore"
          + " and not exists (select a.id from NoticeAttachment a where a.archiveStatus ="
          + " com.keycheck.notice.domain.ArchiveStatus.PENDING"
          + " and (a.notice = n or a.notice.parent = n))"
          + " order by n.postedAt asc, n.id asc")
  List<Notice> findSummaryTargets(Instant postedBefore, Limit limit);
}

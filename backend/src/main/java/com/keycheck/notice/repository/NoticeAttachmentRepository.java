package com.keycheck.notice.repository;

import com.keycheck.notice.domain.ArchiveStatus;
import com.keycheck.notice.domain.NoticeAttachment;
import java.util.List;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

public interface NoticeAttachmentRepository extends JpaRepository<NoticeAttachment, Long> {
  /** 아직 보관하지 못한 첨부 중 실패가 maxFailures번 미만인 것을 공지가 오래된 순으로 */
  @Query(
      "select a from NoticeAttachment a join fetch a.notice n"
          + " where a.archiveStatus = com.keycheck.notice.domain.ArchiveStatus.PENDING"
          + " and a.failures < :maxFailures order by n.postedAt asc, a.position asc")
  List<NoticeAttachment> findArchiveTargets(int maxFailures, Limit limit);

  @Transactional
  @Modifying
  @Query("update NoticeAttachment a set a.failures = a.failures + 1 where a.id = :id")
  void incrementFailures(Long id);

  /** 실패로 세지 않을 일(저장소 장애 등)로 못 했을 때 올렸던 횟수를 되돌린다 */
  @Transactional
  @Modifying
  @Query("update NoticeAttachment a set a.failures = a.failures - 1 where a.id = :id and a.failures > 0")
  void decrementFailures(Long id);

  @Transactional
  @Modifying
  @Query("update NoticeAttachment a set a.archiveStatus = :status where a.id = :id")
  void markArchived(Long id, ArchiveStatus status);

  /** 보관을 끝내며(DONE) 원본 크기를 같은 업데이트로 저장한다 */
  @Transactional
  @Modifying
  @Query(
      "update NoticeAttachment a set a.archiveStatus = com.keycheck.notice.domain.ArchiveStatus.DONE,"
          + " a.width = :width, a.height = :height where a.id = :id")
  void markArchivedWithSize(Long id, int width, int height);

  @Transactional
  @Modifying
  @Query("update NoticeAttachment a set a.sourceUrl = :url where a.id = :id")
  void updateSourceUrl(Long id, String url);

  /** 글 공지의 첨부 뒤에 딸린 메시지의 첨부(시간 순)를 붙여 모든 상태로 */
  @Query(
      "select a from NoticeAttachment a join a.notice n where n.id = :noticeId or n.parent.id ="
          + " :noticeId order by case when n.id = :noticeId then 0 else 1 end, n.postedAt, n.id,"
          + " a.position")
  List<NoticeAttachment> findWithChildren(Long noticeId);
}

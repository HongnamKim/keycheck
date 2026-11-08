package com.keycheck.notice.repository;

import com.keycheck.notice.domain.SaleItem;
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
}

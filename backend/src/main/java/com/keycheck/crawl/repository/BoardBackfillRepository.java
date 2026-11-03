package com.keycheck.crawl.repository;

import com.keycheck.crawl.domain.BoardBackfill;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface BoardBackfillRepository extends JpaRepository<BoardBackfill, Integer> {
  /** 한 줄짜리 상태. V2 마이그레이션이 만들어 둔다. */
  default BoardBackfill current() {
    return findById(BoardBackfill.ID).orElseThrow();
  }

  /** 정기 수집과 과거 글 확보가 동시에 커버리지를 바꾸지 않도록 행을 잠그고 읽는다 */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select b from BoardBackfill b where b.id = " + BoardBackfill.ID)
  BoardBackfill lockCurrent();
}

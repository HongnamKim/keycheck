package com.keycheck.crawl.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.Collections;
import java.util.List;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 과거 글 확보 상태 (한 줄, V2 마이그레이션이 만든다).
 *
 * <p>작성순 목록의 0..nextPage-1 쪽을 받아 두었고, 글 번호가 floorTopicId 이상인 글은 모두 DB에 있다(커버리지).
 * floorTopicId가 null이면 아무것도 커버하지 않는다.
 */
@Entity
@Getter
@Table(name = "board_backfill")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BoardBackfill {
  public static final int ID = 1;

  @Id private Integer id;

  private int nextPage;

  private Long floorTopicId;

  /** 목록 끝까지 받았다 */
  private boolean exhausted;

  public static BoardBackfill create() {
    BoardBackfill backfill = new BoardBackfill();
    backfill.id = ID;
    return backfill;
  }

  /**
   * 작성순 목록 page쪽의 글을 모두 DB에 반영한 뒤 부른다. 이어서 받은 쪽(page == nextPage)일 때만 커버리지를 넓힌다.
   *
   * @param shownPage 받은 HTML의 쪽 번호(0부터). 끝을 넘은 쪽을 달라고 하면 SMF가 마지막 쪽을 대신 주므로 page보다 작다.
   * @param topicIds 그 쪽의 글 번호 (고정글 제외, 비어 있지 않다)
   */
  public void recordPage(int page, int shownPage, List<Long> topicIds) {
    if (page != nextPage) {
      return;
    }
    if (shownPage != page) {
      exhausted = true;
      return;
    }
    long min = Collections.min(topicIds);
    floorTopicId = floorTopicId == null ? min : Math.min(floorTopicId, min);
    nextPage = page + 1;
  }
}

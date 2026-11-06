package com.keycheck.crawl.domain;

import com.keycheck.post.domain.Board;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.Collections;
import java.util.List;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 게시판 하나의 과거 글 확보 상태 (게시판마다 한 줄, V2·V6 마이그레이션이 만든다).
 *
 * <p>그 게시판 작성순 목록의 0..nextPage-1 쪽을 받아 두었고, 그 게시판 글 중 글 번호가 floorTopicId 이상인 글은 모두 DB에 있다
 * (커버리지, 수집 기간 경계보다 작은 글은 빼고). floorTopicId가 null이면 아무것도 커버하지 않는다.
 */
@Entity
@Getter
@Table(name = "board_backfill")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BoardBackfill {
  @Id
  @Enumerated(EnumType.STRING)
  @Column(length = 10)
  private Board board;

  private int nextPage;

  private Long floorTopicId;

  /** 목록 끝까지 받았다 */
  private boolean exhausted;

  public static BoardBackfill create(Board board) {
    BoardBackfill backfill = new BoardBackfill();
    backfill.board = board;
    return backfill;
  }

  /**
   * 작성순 목록 page쪽의 글을 모두 DB에 반영한 뒤 부른다. 이어서 받은 쪽(page == nextPage)일 때만 커버리지를 넓힌다.
   *
   * @param shownPage 받은 HTML의 쪽 번호(0부터). 끝을 넘은 쪽을 달라고 하면 SMF가 마지막 쪽을 대신 주므로 page보다 작다.
   * @param topicIds 그 쪽의 글 번호 (고정글 제외, 비어 있지 않다)
   * @param sinceTopicId 수집 기간 경계. 그 쪽에 이보다 작은 글이 있으면 경계에 닿은 것이므로 끝까지 받은 것으로 본다(경계보다 작은
   *     글은 저장하지 않는다).
   */
  public void recordPage(int page, int shownPage, List<Long> topicIds, long sinceTopicId) {
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
    if (min < sinceTopicId) {
      exhausted = true;
    }
  }

  /** 경계가 생기기 전에 이미 경계 아래까지 받아 둔 게시판을 끝까지 받은 것으로 둔다 */
  public void markExhausted() {
    exhausted = true;
  }
}

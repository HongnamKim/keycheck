package com.keycheck.crawl.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.keycheck.post.domain.Board;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class BoardBackfillTest {
  private final BoardBackfill backfill = BoardBackfill.create(Board.GB);

  private void assertState(int nextPage, Long floorTopicId, boolean exhausted) {
    assertThat(backfill.getNextPage()).isEqualTo(nextPage);
    assertThat(backfill.getFloorTopicId()).isEqualTo(floorTopicId);
    assertThat(backfill.isExhausted()).isEqualTo(exhausted);
  }

  @Test
  @DisplayName("게시판마다 만들고, 처음에는 아무것도 커버하지 않는다")
  void startsEmpty() {
    assertThat(backfill.getBoard()).isEqualTo(Board.GB);
    assertState(0, null, false);
  }

  @Test
  @DisplayName("0쪽을 반영하면 그 쪽의 가장 작은 글 번호가 floor가 되고 다음 쪽은 1")
  void firstPageSetsFloor() {
    backfill.recordPage(0, 0, List.of(500L, 480L, 490L));

    assertState(1, 480L, false);
  }

  @Test
  @DisplayName("이어서 받은 쪽이면 floor를 더 낮추고 다음 쪽으로 넘어간다")
  void nextPageLowersFloor() {
    backfill.recordPage(0, 0, List.of(500L, 480L));
    backfill.recordPage(1, 1, List.of(470L, 450L));

    assertState(2, 450L, false);
  }

  @Test
  @DisplayName("이미 받은 쪽은 커버리지를 바꾸지 않는다")
  void earlierPageKeepsCoverage() {
    backfill.recordPage(0, 0, List.of(500L, 480L));
    backfill.recordPage(1, 1, List.of(470L, 450L));

    backfill.recordPage(0, 0, List.of(510L, 400L));

    assertState(2, 450L, false);
  }

  @Test
  @DisplayName("다음 쪽이 아닌 더 뒤의 쪽도 커버리지를 바꾸지 않는다")
  void laterPageKeepsCoverage() {
    backfill.recordPage(3, 3, List.of(300L));

    assertState(0, null, false);
  }

  @Test
  @DisplayName("새 글이 많이 올라와 이미 받은 글만 있는 쪽이면 floor는 그대로 두고 다음 쪽으로 넘어간다")
  void shiftedPageKeepsFloorAndAdvances() {
    backfill.recordPage(0, 0, List.of(500L, 480L));

    backfill.recordPage(1, 1, List.of(495L, 485L));

    assertState(2, 480L, false);
  }

  @Test
  @DisplayName("끝을 넘은 쪽을 달라고 해 SMF가 앞 쪽(마지막 쪽)을 주면 끝까지 받은 것이다")
  void clampedPageExhausts() {
    backfill.recordPage(0, 0, List.of(500L, 480L));
    backfill.recordPage(1, 1, List.of(470L, 450L));

    backfill.recordPage(2, 1, List.of(470L, 450L));

    assertState(2, 450L, true);
  }

  @Test
  @DisplayName("새 글이 꼭 한 쪽만큼 올라와 앞 쪽과 같은 글이 와도, 요청한 쪽이 맞으면 끝으로 보지 않는다")
  void shiftedByWholePageDoesNotExhaust() {
    backfill.recordPage(0, 0, List.of(500L, 480L));

    backfill.recordPage(1, 1, List.of(500L, 480L));

    assertState(2, 480L, false);
  }
}

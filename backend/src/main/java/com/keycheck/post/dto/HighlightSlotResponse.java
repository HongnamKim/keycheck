package com.keycheck.post.dto;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 하이라이트 한 칸 */
@Getter
@RequiredArgsConstructor
public class HighlightSlotResponse {
  private final HighlightSlot slot;

  /** 마감 띠로 고른 글이면 true. DEADLINE 칸이라도 마감 전 공동구매가 없어 사진 칸으로 고른 글이면 false다. */
  private final boolean deadline;

  /** 고른 창(최근 N일). 전체로 넓혔거나 마감 띠로 골랐으면 null. */
  private final Integer windowDays;

  /** 그 창의 정렬(조회수 또는 댓글 수) 순위, 1부터. 앞 칸에 들어간 글도 순위에 센다. 마감 띠로 골랐으면 null. */
  private final Integer rank;

  /** 카드 형식은 목록과 같다 */
  private final PostCardResponse post;
}

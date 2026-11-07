package com.keycheck.post.dto;

import java.time.LocalDate;
import java.util.List;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 메인 하이라이트 칸과 날짜 줄 숫자 */
@Getter
@RequiredArgsConstructor
public class HighlightResponse {
  /** 오늘(한국 날짜). 마감 띠와 마감 임박 수의 기준이다. */
  private final LocalDate today;

  /** 최근 7일에 작성된 IC 글 수 */
  private final long newIcCount;

  /** 최근 7일에 작성된 공동구매 글 수 */
  private final long newGbCount;

  /** 오늘부터 14일 뒤까지(양끝 포함) 마감하는 공동구매 글 수 */
  private final long endingSoonGbCount;

  /** 고른 칸. HighlightSlot 순서이고, 고를 글이 없는 칸은 빠진다. */
  private final List<HighlightSlotResponse> slots;
}

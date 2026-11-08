package com.keycheck.notice.dto;

import java.util.List;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public class NoticeSummaryResult {
  /** 공지에서 뽑은 판매 건, 본문 순서. 판매 소식이 아니면 빈 목록 */
  private final List<SaleItemDraft> items;

  /** 요약을 만든 요약기 ('fake' 또는 응답한 Claude 모델 ID) */
  private final String summarizedBy;
}

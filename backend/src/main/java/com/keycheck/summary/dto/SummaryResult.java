package com.keycheck.summary.dto;

import com.keycheck.post.domain.Category;
import com.keycheck.post.domain.SpecItem;
import com.keycheck.post.domain.Subcategory;
import java.util.List;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public class SummaryResult {
  /** 한국어 요약 문장 목록 */
  private final List<String> lines;

  /** 스펙표 (레이아웃, 마운트, 소재 등) */
  private final List<SpecItem> specs;

  /** 요약기가 정한 분류. 정하지 않으면 null (가짜 요약기) */
  private final Category category;

  /** 요약기가 정한 세부 분류. category에 속한다. 없거나 정하지 않으면 null */
  private final Subcategory subcategory;

  /** 요약을 만든 요약기 ('fake' 또는 응답한 Claude 모델 ID) */
  private final String summarizedBy;
}

package com.keycheck.summary;

import com.keycheck.post.SpecItem;
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
}

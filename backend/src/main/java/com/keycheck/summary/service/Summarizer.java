package com.keycheck.summary.service;

import com.keycheck.summary.dto.PostSource;
import com.keycheck.summary.dto.SummaryResult;

/** 요약 구현체는 설정 keycheck.summarizer로 고른다 (fake: FakeSummarizer, claude: ClaudeSummarizer). */
public interface Summarizer {
  /**
   * @throws RuntimeException 요약하지 못하면
   */
  SummaryResult summarize(PostSource source);
}

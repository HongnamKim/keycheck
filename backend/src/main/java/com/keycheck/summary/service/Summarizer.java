package com.keycheck.summary.service;

import com.keycheck.summary.dto.PostSource;
import com.keycheck.summary.dto.SummaryResult;

/** 요약 구현체는 설정 keycheck.summarizer로 고른다. 실제 AI 구현체는 나중에 추가한다. */
public interface Summarizer {
  SummaryResult summarize(PostSource source);
}

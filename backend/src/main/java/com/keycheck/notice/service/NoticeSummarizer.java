package com.keycheck.notice.service;

import com.keycheck.notice.dto.NoticeSource;
import com.keycheck.notice.dto.NoticeSummaryResult;

/**
 * 공지 하나를 판매 건으로 나누고 요약한다. 구현체는 설정 keycheck.summarizer로 고른다 (fake: FakeNoticeSummarizer, claude:
 * ClaudeNoticeSummarizer).
 */
public interface NoticeSummarizer {
  /**
   * @throws RuntimeException 요약하지 못하면
   */
  NoticeSummaryResult summarize(NoticeSource source);
}

package com.keycheck.summary.service;

import com.keycheck.summary.dto.PostSource;
import com.keycheck.summary.dto.SummaryResult;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** 개발용 가짜 요약기. 번역하지 않고 제목과 본문 앞부분으로 고정 형식의 결과를 만든다. 분류·세부 분류·공동구매 기간·벤더는 정하지 않는다. */
@Component
@ConditionalOnProperty(name = "keycheck.summarizer", havingValue = "fake", matchIfMissing = true)
public class FakeSummarizer implements Summarizer {
  /** post.summarized_by에 남는 값 */
  public static final String ID = "fake";

  private static final int BODY_PREVIEW_LENGTH = 100;

  @Override
  public SummaryResult summarize(PostSource source) {
    String body = source.getBodyText();
    String preview = body.substring(0, Math.min(body.length(), BODY_PREVIEW_LENGTH));
    return new SummaryResult(
        List.of("[가짜 요약] " + source.getTitle(), preview),
        List.of(),
        null,
        null,
        ID,
        null,
        null,
        null,
        List.of());
  }
}

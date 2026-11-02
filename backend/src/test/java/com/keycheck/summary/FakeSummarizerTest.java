package com.keycheck.summary;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class FakeSummarizerTest {
  private final FakeSummarizer summarizer = new FakeSummarizer();

  @Test
  @DisplayName("제목과 본문 앞 100자로 가짜 요약을 만든다")
  void summarizesWithTitleAndBodyPreview() {
    SummaryResult result = summarizer.summarize(new PostSource("[IC] Example", "가".repeat(150)));

    assertThat(result.lines()).containsExactly("[가짜 요약] [IC] Example", "가".repeat(100));
    assertThat(result.specs()).isEmpty();
  }

  @Test
  @DisplayName("본문이 짧으면 본문 전체를 쓴다")
  void shortBodyIsUsedWhole() {
    SummaryResult result = summarizer.summarize(new PostSource("제목", "짧은 본문"));

    assertThat(result.lines().get(1)).isEqualTo("짧은 본문");
  }
}

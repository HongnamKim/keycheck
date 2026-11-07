package com.keycheck.summary.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.keycheck.post.domain.Board;
import com.keycheck.summary.dto.PostSource;
import com.keycheck.summary.dto.SummaryResult;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class FakeSummarizerTest {
  private static final Instant POSTED_AT = Instant.parse("2026-10-13T00:00:00Z");

  private final FakeSummarizer summarizer = new FakeSummarizer();

  @Test
  @DisplayName("제목과 본문 앞 100자로 가짜 요약을 만든다")
  void summarizesWithTitleAndBodyPreview() {
    SummaryResult result =
        summarizer.summarize(
            new PostSource(Board.IC, "[IC] Example", "가".repeat(150), POSTED_AT));

    assertThat(result.getLines()).containsExactly("[가짜 요약] [IC] Example", "가".repeat(100));
    assertThat(result.getSpecs()).isEmpty();
    assertThat(result.getCategory()).isNull();
    assertThat(result.getSummarizedBy()).isEqualTo("fake");
  }

  @Test
  @DisplayName("본문이 짧으면 본문 전체를 쓴다")
  void shortBodyIsUsedWhole() {
    SummaryResult result =
        summarizer.summarize(new PostSource(Board.IC, "제목", "짧은 본문", POSTED_AT));

    assertThat(result.getLines().get(1)).isEqualTo("짧은 본문");
  }

  @Test
  @DisplayName("GB 글이어도 공동구매 기간을 뽑지 않는다")
  void doesNotExtractGbPeriod() {
    SummaryResult result =
        summarizer.summarize(
            new PostSource(Board.GB, "[GB] Example", "Group buy runs Oct 30 - Nov 13.", POSTED_AT));

    assertThat(result.getGbStartDate()).isNull();
    assertThat(result.getGbEndDate()).isNull();
  }

  @Test
  @DisplayName("상품 이름을 뽑지 않는다")
  void doesNotExtractProductName() {
    SummaryResult result =
        summarizer.summarize(
            new PostSource(Board.GB, "[GB] SWG Nightshift | Oct 30th -> Nov 13th", "본문", POSTED_AT));

    assertThat(result.getProductName()).isNull();
  }
}

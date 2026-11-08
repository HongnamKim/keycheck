package com.keycheck.notice.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.keycheck.notice.domain.SaleCategory;
import com.keycheck.notice.domain.SaleLink;
import com.keycheck.notice.domain.SalePhase;
import com.keycheck.notice.domain.SaleType;
import com.keycheck.notice.dto.NoticeImage;
import com.keycheck.notice.dto.NoticeSource;
import com.keycheck.notice.dto.NoticeSummaryResult;
import com.keycheck.notice.dto.SaleItemDraft;
import com.keycheck.post.domain.KoreanVendor;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class FakeNoticeSummarizerTest {
  private static final Instant POSTED_AT = Instant.parse("2026-11-06T09:42:00Z");

  private static final SaleLink STORE = new SaleLink("판매 페이지", "https://shop.test/p/1");
  private static final SaleLink OFFICIAL = new SaleLink("공식 스토어", "https://shop.test/p/2");

  private final FakeNoticeSummarizer summarizer = new FakeNoticeSummarizer();

  private static NoticeSource source(
      String content, List<SaleLink> links, List<NoticeImage> images) {
    return new NoticeSource(KoreanVendor.SWAGKEYS, POSTED_AT, content, links, images);
  }

  @Test
  @DisplayName("첫 제목을 이름으로 한 판매 건 하나를 첫 링크·첫 사진으로 만든다")
  void 첫_제목을_이름으로_한_건을_만든다() {
    NoticeSummaryResult result =
        summarizer.summarize(
            source(
                "@KR\n공지 안내예요.\n##\n## Test Board 재입고\n### 다른 제목\n본문",
                List.of(STORE, OFFICIAL),
                List.of(new NoticeImage(7L, "a.png", null), new NoticeImage(8L, "b.png", null))));

    assertThat(result.getSummarizedBy()).isEqualTo("fake");
    assertThat(result.getItems()).hasSize(1);
    SaleItemDraft item = result.getItems().get(0);
    assertThat(item.getProductName()).isEqualTo("Test Board 재입고");
    assertThat(item.getCategory()).isEqualTo(SaleCategory.KEYBOARD);
    assertThat(item.getSaleType()).isEqualTo(SaleType.SALE);
    assertThat(item.getPhase()).isEqualTo(SalePhase.OPEN);
    assertThat(item.getSummaryLines()).containsExactly("[가짜 요약] Test Board 재입고");
    assertThat(item.getLinks()).containsExactly(STORE);
    assertThat(item.getImageIds()).containsExactly(7L);
  }

  @Test
  @DisplayName("제목이 없으면 비지 않은 첫 줄을 80자까지 이름으로 쓰고, 링크·사진이 없으면 빈 목록")
  void 제목이_없으면_첫_줄을_이름으로_쓴다() {
    NoticeSummaryResult result =
        summarizer.summarize(source("\n  " + "가".repeat(90) + "\n둘째 줄", List.of(), List.of()));

    SaleItemDraft item = result.getItems().get(0);
    assertThat(item.getProductName()).isEqualTo("가".repeat(80));
    assertThat(item.getLinks()).isEmpty();
    assertThat(item.getImageIds()).isEmpty();
  }

  @Test
  @DisplayName("글자가 없으면 0건")
  void 글자가_없으면_0건() {
    for (String content : new String[] {"", " \n "}) {
      NoticeSummaryResult result = summarizer.summarize(source(content, List.of(), List.of()));

      assertThat(result.getItems()).isEmpty();
      assertThat(result.getSummarizedBy()).isEqualTo("fake");
    }
  }
}

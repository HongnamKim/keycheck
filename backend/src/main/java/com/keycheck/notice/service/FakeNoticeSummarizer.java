package com.keycheck.notice.service;

import com.keycheck.notice.domain.SaleCategory;
import com.keycheck.notice.domain.SalePhase;
import com.keycheck.notice.domain.SaleType;
import com.keycheck.notice.dto.NoticeImage;
import com.keycheck.notice.dto.NoticeSource;
import com.keycheck.notice.dto.NoticeSummaryResult;
import com.keycheck.notice.dto.SaleItemDraft;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 개발용 가짜 공지 요약기(로컬 화면 확인용). 글자가 있으면 첫 제목 줄을 이름으로 한 키보드 판매 건 하나를 만들고, 첫 링크와 첫 사진을 붙인다.
 * 글자가 없으면 0건이다.
 */
@Component
@ConditionalOnProperty(name = "keycheck.summarizer", havingValue = "fake", matchIfMissing = true)
public class FakeNoticeSummarizer implements NoticeSummarizer {
  /** notice.summarized_by에 남는 값 */
  public static final String ID = "fake";

  private static final int MAX_NAME = 80;

  @Override
  public NoticeSummaryResult summarize(NoticeSource source) {
    String content = source.getContent();
    if (content == null || content.isBlank()) {
      return new NoticeSummaryResult(List.of(), ID);
    }
    String name = name(content);
    SaleItemDraft item =
        new SaleItemDraft(
            name,
            SaleCategory.KEYBOARD,
            SaleType.SALE,
            SalePhase.OPEN,
            null,
            null,
            null,
            null,
            null,
            List.of("[가짜 요약] " + name),
            source.getLinks().stream().limit(1).toList(),
            source.getImages().stream().limit(1).map(NoticeImage::getAttachmentId).toList());
    return new NoticeSummaryResult(List.of(item), ID);
  }

  /** 첫 제목 줄(앞의 #을 뺀 글자), 없으면 비지 않은 첫 줄. 앞의 MAX_NAME자 */
  private static String name(String content) {
    List<String> lines =
        content.lines().map(String::strip).filter(line -> !line.isEmpty()).toList();
    String name =
        lines.stream()
            .filter(line -> line.startsWith("#"))
            .map(line -> line.replaceFirst("^#+", "").strip())
            .filter(heading -> !heading.isEmpty())
            .findFirst()
            .orElse(lines.get(0));
    return name.substring(0, Math.min(name.length(), MAX_NAME));
  }
}

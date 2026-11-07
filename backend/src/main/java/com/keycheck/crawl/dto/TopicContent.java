package com.keycheck.crawl.dto;

import com.keycheck.image.dto.Selection;
import com.keycheck.post.domain.Category;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 글 첫 페이지와 그것으로 정한 분류·대표 이미지. 트랜잭션 밖에서 만들어 저장 단계에 넘긴다. 요약은 SummaryWorker가 따로 한다. */
@Getter
@RequiredArgsConstructor
public class TopicContent {
  private final TopicPage page;
  private final Category category;
  private final Selection selection;
}

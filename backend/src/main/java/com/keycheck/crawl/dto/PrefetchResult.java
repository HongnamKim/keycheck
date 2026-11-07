package com.keycheck.crawl.dto;

/** 틀 글 미리 읽기 한 번의 결과 (CrawlService.prefetchSkeleton) */
public enum PrefetchResult {
  /** 글 첫 페이지를 읽어 채웠다. 그사이 상세 열기가 먼저 채웠어도 이것이다. */
  FILLED,
  /** 글 첫 페이지를 읽거나 저장하지 못했다(요청·해석·저장 실패) */
  FAILED,
  /** 빈 간격이 없어 요청하지 않았다 */
  NO_SLOT
}

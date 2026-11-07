package com.keycheck.post.dto;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 상세 벤더(판매처) 하나 */
@Getter
@RequiredArgsConstructor
public class VendorResponse {
  /** 원문 표기 이름 */
  private final String name;

  /** 대문자 지역 코드(한국은 "KR"). 모르면 null. */
  private final String region;

  /** 첫 글 본문에 있던 그 벤더 판매 페이지 주소. 없으면 null. */
  private final String url;
}

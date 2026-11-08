package com.keycheck.notice.dto;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 판매 건의 사진 하나 */
@Getter
@RequiredArgsConstructor
public class SaleImageResponse {
  /** 원본을 webp로 바꾼 것(가로 최대 2048) */
  private final String url;

  /** 가로 최대 1280 */
  private final String largeUrl;

  /** 가로 최대 640 */
  private final String thumbUrl;
}

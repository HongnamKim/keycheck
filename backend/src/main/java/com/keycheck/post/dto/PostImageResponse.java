package com.keycheck.post.dto;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 상세 갤러리 이미지 한 장 */
@Getter
@RequiredArgsConstructor
public class PostImageResponse {
  /** 원본 주소. 확대 보기와 원본 열기에 쓴다. */
  private final String url;

  /** 축소본(가로 최대 640px) 공개 주소. 아직 없으면 null이고 화면은 원본을 쓴다. */
  private final String thumbUrl;
}

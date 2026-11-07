package com.keycheck.crawl.dto;

import com.keycheck.image.domain.ImageSource;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 글 페이지에서 뽑아낸, 아직 저장하지 않은 이미지 */
@Getter
@RequiredArgsConstructor
public class RawImage {
  private final String url;
  private final ImageSource source;
  private final int position;
}

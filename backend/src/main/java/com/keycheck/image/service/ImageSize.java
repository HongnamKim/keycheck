package com.keycheck.image.service;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 이미지의 보이는 폭·높이(픽셀) */
@Getter
@RequiredArgsConstructor
public class ImageSize {
  private final int width;
  private final int height;
}

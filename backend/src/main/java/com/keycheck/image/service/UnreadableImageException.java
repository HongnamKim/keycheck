package com.keycheck.image.service;

/** 원본을 이미지로 읽거나 축소본으로 만들 수 없다. 이미지 탓이므로 실패 횟수에 넣는다. */
public class UnreadableImageException extends RuntimeException {
  public UnreadableImageException(String message) {
    super(message);
  }

  public UnreadableImageException(String message, Throwable cause) {
    super(message, cause);
  }
}

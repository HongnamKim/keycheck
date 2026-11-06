package com.keycheck.image.client;

/**
 * 원본 이미지를 받지 못했다(주소 형식, 막힌 주소, 4xx·5xx, 시간 초과, 크기 초과). 이미지 탓이므로 실패 횟수에 넣는다.
 */
public class ImageFetchException extends RuntimeException {
  public ImageFetchException(String message) {
    super(message);
  }

  public ImageFetchException(String message, Throwable cause) {
    super(message, cause);
  }
}

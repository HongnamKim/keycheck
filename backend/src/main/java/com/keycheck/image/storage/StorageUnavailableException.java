package com.keycheck.image.storage;

/**
 * 저장소에 올리지 못했다(접속·인증·서버 오류). 이미지 탓이 아니므로 실패 횟수에 넣지 않고 작업을 잠시 쉰다 (설계 3.7).
 *
 * <p>메시지에 객체 이름이나 접속 키를 싣지 않는다.
 */
public class StorageUnavailableException extends RuntimeException {
  public StorageUnavailableException(String message) {
    super(message);
  }

  public StorageUnavailableException(String message, Throwable cause) {
    super(message, cause);
  }
}

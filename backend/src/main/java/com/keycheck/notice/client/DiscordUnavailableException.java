package com.keycheck.notice.client;

/** 디스코드가 오류를 돌려주거나 연결에 실패한 경우. 메시지에 토큰 등 요청 헤더를 싣지 않는다 */
public class DiscordUnavailableException extends RuntimeException {
  public DiscordUnavailableException(String message) {
    super(message);
  }

  public DiscordUnavailableException(String message, Throwable cause) {
    super(message, cause);
  }
}

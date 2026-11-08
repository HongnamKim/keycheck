package com.keycheck.notice.domain;

/** 공지 첨부를 저장소(R2)에 두는 객체 이름 */
public final class NoticeKeys {
  private NoticeKeys() {}

  /** 원본을 webp로 바꾼 것 */
  public static String archive(long attachmentId) {
    return "notices/" + attachmentId + ".webp";
  }

  /** 가로 최대 1280px 축소본 */
  public static String large(long attachmentId) {
    return "notices/" + attachmentId + "-1280.webp";
  }

  /** 가로 최대 640px 축소본 */
  public static String thumb(long attachmentId) {
    return "notices/" + attachmentId + "-640.webp";
  }
}

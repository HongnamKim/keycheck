package com.keycheck.image.storage;

/** 축소본 저장소. 올린 객체는 공개 읽기이고 브라우저가 공개 주소로 직접 받는다 (설계 3.1). */
public interface ThumbStorage {
  /**
   * 축소본을 이 객체 이름으로 올린다. 같은 이름이 있으면 덮어쓴다.
   *
   * @throws StorageUnavailableException 접속·인증·서버 오류로 올리지 못했다(이미지 탓이 아니다)
   */
  void put(String key, byte[] webp);
}

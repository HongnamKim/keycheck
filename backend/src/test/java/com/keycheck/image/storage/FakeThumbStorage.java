package com.keycheck.image.storage;

import java.util.HashMap;
import java.util.Map;

/** 실제 R2 대신 메모리에 객체를 둔다. unavailable이면 저장소 장애처럼 올리지 못한다. */
public class FakeThumbStorage implements ThumbStorage {
  /** 객체 이름 → 올린 WebP 바이트 */
  public final Map<String, byte[]> objects = new HashMap<>();

  public boolean unavailable;

  @Override
  public void put(String key, byte[] webp) {
    if (unavailable) {
      throw new StorageUnavailableException("가짜 저장소 장애");
    }
    objects.put(key, webp);
  }
}

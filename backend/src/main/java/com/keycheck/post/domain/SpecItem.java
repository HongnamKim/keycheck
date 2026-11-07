package com.keycheck.post.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.EqualsAndHashCode;
import lombok.Getter;

/** 스펙표 한 줄 (예: label = "레이아웃", value = "65%"). post.specs jsonb에 목록으로 저장된다. */
@Getter
@EqualsAndHashCode
public class SpecItem {
  private final String label;
  private final String value;

  /** jsonb에서 다시 읽을 때 Jackson이 쓰는 생성자 */
  @JsonCreator
  public SpecItem(@JsonProperty("label") String label, @JsonProperty("value") String value) {
    this.label = label;
    this.value = value;
  }
}

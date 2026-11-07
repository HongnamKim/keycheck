package com.keycheck.post.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.EqualsAndHashCode;
import lombok.Getter;

/** 첫 글 본문의 링크 하나(글자와 주소). post.body_links jsonb에 목록으로 저장된다. */
@Getter
@EqualsAndHashCode
public class BodyLink {
  private final String text;
  private final String url;

  /** jsonb에서 다시 읽을 때 Jackson이 쓰는 생성자 */
  @JsonCreator
  public BodyLink(@JsonProperty("text") String text, @JsonProperty("url") String url) {
    this.text = text;
    this.url = url;
  }
}

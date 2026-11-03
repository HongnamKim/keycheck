package com.keycheck.post.dto;

import java.time.Instant;

/** 작성일 추정의 기준점: 작성일이 있는 글의 글 번호와 작성일 */
public interface PostedAtAnchor {
  Long getTopicId();

  Instant getPostedAt();
}

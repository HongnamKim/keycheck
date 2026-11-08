package com.keycheck.notice.dto;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 요약기에 넘기는 공지 첨부 사진 하나 */
@Getter
@RequiredArgsConstructor
public class NoticeImage {
  /** 디스코드 첨부 id. 요약기는 판매 건의 사진을 이 id로 고른다. */
  private final long attachmentId;

  private final String filename;

  /** 폭 640 사본의 공개 주소. 공개 주소 설정이 없으면 null(사진은 보내지 않고 파일 이름만 넘긴다) */
  private final String thumbUrl;
}

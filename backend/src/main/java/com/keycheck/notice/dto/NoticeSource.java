package com.keycheck.notice.dto;

import com.keycheck.notice.domain.SaleLink;
import com.keycheck.post.domain.KoreanVendor;
import java.time.Instant;
import java.util.List;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 요약기에 넘기는 글 공지 하나(딸린 메시지의 사진 포함) */
@Getter
@RequiredArgsConstructor
public class NoticeSource {
  private final KoreanVendor vendor;

  /** 공지 시각. 요약기가 "금일", "차주 화요일" 같은 말을 날짜로 바꿀 때 기준으로 쓴다. */
  private final Instant postedAt;

  /** 디스코드 마크다운 본문 */
  private final String content;

  /** 본문 링크, 본문 순서. 판매 건의 링크는 이 중에서만 고른다. */
  private final List<SaleLink> links;

  /** 글 공지의 첨부 뒤에 딸린 메시지의 첨부를 시간 순으로 */
  private final List<NoticeImage> images;
}

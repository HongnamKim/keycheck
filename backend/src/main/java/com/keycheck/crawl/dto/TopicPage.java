package com.keycheck.crawl.dto;

import com.keycheck.post.domain.BodyLink;
import java.time.Instant;
import java.util.List;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 글 첫 페이지(topic={id}.0)에서 읽은 내용. 새 글 수집에 쓴다. */
@Getter
@RequiredArgsConstructor
public class TopicPage {
  private final String bodyText;
  private final List<RawImage> images;
  private final int views;
  private final Instant postedAt;

  /** 첫 글 본문의 http·https 링크. 첨부·geekhack 주소는 빠져 있다. */
  private final List<BodyLink> links;
}

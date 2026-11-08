package com.keycheck.notice.dto;

import com.keycheck.notice.domain.SaleLink;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 구매 링크 하나 */
@Getter
@RequiredArgsConstructor
public class SaleLinkResponse {
  private final String text;
  private final String url;

  public static SaleLinkResponse from(SaleLink link) {
    return new SaleLinkResponse(link.getText(), link.getUrl());
  }
}

package com.keycheck.summary.dto;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public class PostSource {
  private final String title;
  private final String bodyText;
}

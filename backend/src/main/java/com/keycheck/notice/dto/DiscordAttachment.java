package com.keycheck.notice.dto;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public class DiscordAttachment {
  private final String id;
  private final String filename;
  private final String url;
}

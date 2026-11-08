package com.keycheck.notice.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 채널 하나에서 마지막으로 읽은 메시지 (채널마다 한 줄) */
@Entity
@Getter
@Table(name = "notice_channel_cursor")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class NoticeChannelCursor {
  @Id private Long channelId;

  private long lastMessageId;

  public static NoticeChannelCursor create(long channelId, long lastMessageId) {
    NoticeChannelCursor cursor = new NoticeChannelCursor();
    cursor.channelId = channelId;
    cursor.lastMessageId = lastMessageId;
    return cursor;
  }

  /** 더 큰 메시지 id일 때만 앞으로 옮긴다 */
  public void advance(long messageId) {
    if (messageId > lastMessageId) {
      lastMessageId = messageId;
    }
  }
}

package com.keycheck.notice.service;

import com.keycheck.notice.domain.SaleItem;
import com.keycheck.notice.domain.SalePhase;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;

/** 판매 건의 시각 규칙(한국 시각). 판매 일정 목록과 지난 판매 목록이 같은 규칙을 쓰도록 한 곳에 둔다. */
final class SaleTimes {
  private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
  private static final Duration ON_SALE_WINDOW = Duration.ofDays(7);
  private static final Duration ANNOUNCE_WINDOW = Duration.ofDays(30);

  private SaleTimes() {}

  /**
   * 지금 기준으로 끝나지 않았는가. 마감이 있으면 마감(날짜만이면 23:59:59)이 지금 뒤. 마감이 없으면 시작이 있을 때 시작이 아직 안 왔거나 시작 뒤 7일
   * 안(예고 여부와 상관없이), 시작이 없을 때 예고가 아니면 공지 뒤 7일 안·예고면 공지 뒤 30일 안.
   */
  static boolean isActive(SaleItem item, Instant now) {
    if (item.getEndDate() != null) {
      return endAt(item).isAfter(now);
    }
    if (item.getStartDate() != null) {
      Instant start = startAt(item);
      return start.isAfter(now) || start.plus(ON_SALE_WINDOW).isAfter(now);
    }
    Duration window = item.getPhase() == SalePhase.ANNOUNCE ? ANNOUNCE_WINDOW : ON_SALE_WINDOW;
    return item.getPostedAt().plus(window).isAfter(now);
  }

  /** 지난 판매의 정렬 기준 시각: 마감(날짜만이면 23:59:59), 없으면 시작(날짜만이면 00:00), 없으면 공지 시각 */
  static Instant pastAt(SaleItem item) {
    if (item.getEndDate() != null) {
      return endAt(item);
    }
    if (item.getStartDate() != null) {
      return startAt(item);
    }
    return item.getPostedAt();
  }

  private static Instant endAt(SaleItem item) {
    LocalTime time = item.getEndTime() == null ? LocalTime.of(23, 59, 59) : item.getEndTime();
    return item.getEndDate().atTime(time).atZone(SEOUL).toInstant();
  }

  private static Instant startAt(SaleItem item) {
    LocalTime time = item.getStartTime() == null ? LocalTime.MIDNIGHT : item.getStartTime();
    return item.getStartDate().atTime(time).atZone(SEOUL).toInstant();
  }
}

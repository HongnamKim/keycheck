package com.keycheck.crawl.parser;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;

/**
 * geekhack이 보여 주는 시각("Tue, 13 October 2026, 08:29:35", 최근이면 "Today at 10:40:10"·"Yesterday at
 * 10:40:10")을 해석한다. 글 페이지 작성일과 게시판 목록의 마지막 게시물 시각이 같은 형식이다.
 */
@RequiredArgsConstructor
public class GeekhackTimeParser {
  private static final Pattern RELATIVE_DATE =
      Pattern.compile("(Today|Yesterday) at (\\d{2}:\\d{2}:\\d{2})");
  private static final DateTimeFormatter FULL_DATE =
      DateTimeFormatter.ofPattern("EEE, dd MMMM yyyy, HH:mm:ss", Locale.ENGLISH);

  private final ZoneId timeZone;
  private final Clock clock;

  /**
   * @throws java.time.format.DateTimeParseException 형식이 다를 때
   */
  public Instant parse(String text) {
    Matcher relative = RELATIVE_DATE.matcher(text);
    if (!relative.matches()) {
      return LocalDateTime.parse(text, FULL_DATE).atZone(timeZone).toInstant();
    }
    LocalDate today = clock.instant().atZone(timeZone).toLocalDate();
    LocalDate date = relative.group(1).equals("Today") ? today : today.minusDays(1);
    return date.atTime(LocalTime.parse(relative.group(2))).atZone(timeZone).toInstant();
  }
}

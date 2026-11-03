package com.keycheck.crawl.parser;

import com.keycheck.crawl.dto.BoardTopic;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Component;

/** 게시판 목록 HTML 파서. 구조는 docs/geekhack-html-notes.md 2장 참고. */
@Slf4j
@Component
@RequiredArgsConstructor
public class BoardListParser {
  private static final Pattern TOPIC_ID = Pattern.compile("topic=(\\d+)\\.");
  private static final Pattern STATS = Pattern.compile("(\\d+)\\s+Repl\\w*\\s+(\\d+)\\s+View\\w*");
  /** 마지막 게시물 칸의 "{시각}<br /> by {작성자}"에서 작성자 부분 */
  private static final Pattern BY_AUTHOR = Pattern.compile("\\s*\\bby\\s.*$");

  private final GeekhackTimeParser timeParser;

  /**
   * @throws IllegalStateException 글 목록 표가 없는 페이지(차단·오류 페이지 등)
   */
  public List<BoardTopic> parse(String html) {
    Element table = Jsoup.parse(html).selectFirst("#messageindex table.table_grid");
    if (table == null) {
      throw new IllegalStateException("게시판 목록 표를 찾지 못했다");
    }

    List<BoardTopic> topics = new ArrayList<>();
    for (Element subject : table.select("tbody td.subject")) {
      if (subject.className().contains("sticky")) {
        continue;
      }
      BoardTopic topic = parseRow(subject.parent());
      if (topic != null) {
        topics.add(topic);
      }
    }
    return topics;
  }

  /**
   * 쪽 번호 목록("Pages: 1 [<strong>2</strong>] 3 ...")에서 지금 쪽을 읽는다.
   *
   * @return 0부터 센 쪽 번호
   * @throws IllegalStateException 쪽 번호 목록이 없거나 읽을 수 없는 페이지
   */
  public int parseCurrentPage(String html) {
    Element current = Jsoup.parse(html).selectFirst(".pagelinks strong");
    if (current == null || !current.text().matches("\\d+")) {
      throw new IllegalStateException("게시판 쪽 번호를 찾지 못했다");
    }
    return Integer.parseInt(current.text()) - 1;
  }

  /** 해석할 수 없는 행이면 null */
  private BoardTopic parseRow(Element row) {
    Element link = row.selectFirst("td.subject span[id^=msg_] > a");
    Element author = row.selectFirst("td.subject p > a");
    Element statsCell = row.selectFirst("td.stats");

    if (link != null && author != null && statsCell != null) {
      Matcher topicId = TOPIC_ID.matcher(link.attr("href"));
      Matcher stats = STATS.matcher(statsCell.text());
      if (topicId.find() && stats.find()) {
        return new BoardTopic(
            Long.parseLong(topicId.group(1)),
            link.text(),
            author.text(),
            Integer.parseInt(stats.group(1)),
            Integer.parseInt(stats.group(2)),
            parseLastPostAt(row.selectFirst("td.lastpost")));
      }
    }

    log.warn("게시판 행을 해석하지 못해 건너뜀: {}", row.text());
    return null;
  }

  /** 읽지 못하면 null. 시각이 없어도 행은 버리지 않는다. */
  private Instant parseLastPostAt(Element cell) {
    String text = cell == null ? "" : BY_AUTHOR.matcher(cell.text()).replaceFirst("");
    try {
      return timeParser.parse(text);
    } catch (DateTimeParseException e) {
      log.warn("마지막 게시물 시각을 해석하지 못함: {}", text);
      return null;
    }
  }
}

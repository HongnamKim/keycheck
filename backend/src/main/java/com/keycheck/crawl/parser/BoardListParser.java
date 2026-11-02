package com.keycheck.crawl.parser;

import com.keycheck.crawl.dto.BoardTopic;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Component;

/** 게시판 목록 HTML 파서. 구조는 docs/geekhack-html-notes.md 2장 참고. */
@Slf4j
@Component
public class BoardListParser {
  private static final Pattern TOPIC_ID = Pattern.compile("topic=(\\d+)\\.");
  private static final Pattern STATS = Pattern.compile("(\\d+)\\s+Repl\\w*\\s+(\\d+)\\s+View\\w*");

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
            Integer.parseInt(stats.group(2)));
      }
    }

    log.warn("게시판 행을 해석하지 못해 건너뜀: {}", row.text());
    return null;
  }
}

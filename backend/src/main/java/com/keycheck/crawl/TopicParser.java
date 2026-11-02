package com.keycheck.crawl;

import com.keycheck.image.ImageSource;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

/** 글 페이지 HTML 파서. 구조는 docs/geekhack-html-notes.md 3장 참고. */
@RequiredArgsConstructor
public class TopicParser {
  private static final Pattern VIEWS = Pattern.compile("Read (\\d+) times");
  private static final Pattern REPLY_NUMBER = Pattern.compile("Reply #(\\d+)");
  private static final Pattern POSTED_AT = Pattern.compile("on:\\s*(.+?)\\s*»");
  private static final Pattern RELATIVE_DATE =
      Pattern.compile("(Today|Yesterday) at (\\d{2}:\\d{2}:\\d{2})");
  private static final DateTimeFormatter FULL_DATE =
      DateTimeFormatter.ofPattern("EEE, dd MMMM yyyy, HH:mm:ss", Locale.ENGLISH);
  private static final String ATTACHMENT_MARK = "action=dlattach";

  private final ZoneId timeZone;
  private final Clock clock;

  /**
   * @throws IllegalStateException 글이 없는 페이지(삭제된 글, 차단·오류 페이지 등)
   */
  public TopicPage parseFirstPage(String html) {
    Element forumPosts = forumPosts(html);
    Element firstPost = forumPosts.selectFirst("div.post_wrapper");
    Element body = firstPost == null ? null : firstPost.selectFirst("div.inner");
    if (body == null) {
      throw new IllegalStateException("첫 글을 찾지 못했다");
    }
    return new TopicPage(
        body.text(), parseImages(firstPost, body), parseViews(forumPosts), parsePostedAt(firstPost));
  }

  /**
   * 글의 마지막 페이지를 넘겨야 댓글 수가 맞다.
   *
   * @throws IllegalStateException 글이 없는 페이지(삭제된 글, 차단·오류 페이지 등)
   */
  public TopicStats parseStats(String html) {
    Element forumPosts = forumPosts(html);
    return new TopicStats(parseViews(forumPosts), parseReplies(forumPosts));
  }

  private Element forumPosts(String html) {
    Element forumPosts = Jsoup.parse(html).selectFirst("#forumposts");
    if (forumPosts == null) {
      throw new IllegalStateException("글 영역을 찾지 못했다");
    }
    return forumPosts;
  }

  private int parseViews(Element forumPosts) {
    Element header = forumPosts.selectFirst("h3.catbg");
    Matcher views = VIEWS.matcher(header == null ? "" : header.text());
    if (!views.find()) {
      throw new IllegalStateException("조회수를 찾지 못했다");
    }
    return Integer.parseInt(views.group(1));
  }

  /** 마지막 글의 "Reply #N"이 댓글 수다. 원글만 있으면 번호가 없으므로 0. */
  private int parseReplies(Element forumPosts) {
    Elements dates = forumPosts.select("div.post_wrapper div.keyinfo div.smalltext");
    Matcher reply = REPLY_NUMBER.matcher(dates.isEmpty() ? "" : dates.last().text());
    return reply.find() ? Integer.parseInt(reply.group(1)) : 0;
  }

  private Instant parsePostedAt(Element firstPost) {
    Element date = firstPost.selectFirst("div.keyinfo div.smalltext");
    Matcher postedAt = POSTED_AT.matcher(date == null ? "" : date.text());
    if (!postedAt.find()) {
      throw new IllegalStateException("작성일을 찾지 못했다");
    }
    return toInstant(postedAt.group(1));
  }

  private Instant toInstant(String text) {
    Matcher relative = RELATIVE_DATE.matcher(text);
    if (!relative.matches()) {
      return LocalDateTime.parse(text, FULL_DATE).atZone(timeZone).toInstant();
    }
    LocalDate today = clock.instant().atZone(timeZone).toLocalDate();
    LocalDate date = relative.group(1).equals("Today") ? today : today.minusDays(1);
    return date.atTime(LocalTime.parse(relative.group(2))).atZone(timeZone).toInstant();
  }

  /** 본문 이미지 다음에 첨부 영역 이미지. 서명과 아바타는 이 범위 밖이라 읽지 않는다. */
  private List<RawImage> parseImages(Element firstPost, Element body) {
    List<String> urls = new ArrayList<>();
    for (Element image : body.select("img[src]")) {
      urls.add(image.attr("src"));
    }
    // 첨부 영역의 img는 썸네일이라 링크의 원본 주소를 쓴다
    for (Element link : firstPost.select("div.attachments a.highslide[href]")) {
      urls.add(link.attr("href"));
    }

    List<RawImage> images = new ArrayList<>();
    for (String url : urls) {
      images.add(
          new RawImage(
              removeSessionId(url),
              url.contains(ATTACHMENT_MARK) ? ImageSource.ATTACHMENT : ImageSource.EXTERNAL,
              images.size()));
    }
    return images;
  }

  private String removeSessionId(String url) {
    return url.replaceFirst("PHPSESSID=[^&;]*[&;]", "");
  }
}

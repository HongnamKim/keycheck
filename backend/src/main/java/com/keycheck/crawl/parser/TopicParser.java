package com.keycheck.crawl.parser;

import com.keycheck.crawl.dto.RawImage;
import com.keycheck.crawl.dto.TopicPage;
import com.keycheck.crawl.dto.TopicStats;
import com.keycheck.image.client.ImageDownloader;
import com.keycheck.image.domain.ImageSource;
import com.keycheck.post.domain.BodyLink;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
  private static final String ATTACHMENT_MARK = "action=dlattach";
  private static final int MAX_LINKS = 200;
  private static final int MAX_LINK_TEXT_LENGTH = 200;
  private static final int MAX_LINK_URL_LENGTH = 2000;

  private final GeekhackTimeParser timeParser;

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
        body.text(),
        parseImages(firstPost, body),
        parseViews(forumPosts),
        parsePostedAt(firstPost),
        parseLinks(body));
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
    return timeParser.parse(postedAt.group(1));
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

  /**
   * 본문의 http·https 링크를 본문 순서대로, 같은 주소는 처음 것만 MAX_LINKS개까지 모은다. 첨부(action=dlattach), geekhack
   * 주소, 호스트가 없는 주소는 판매처 링크가 아니라서 뺀다. 글자가 없으면(이미지 링크 등) 주소를 글자로 쓰고, 같은 주소가 뒤에 글자와 함께
   * 다시 나오면 처음 자리에 그 글자를 쓴다(벤더 로고 링크 뒤에 벤더 이름 링크가 오는 경우).
   */
  private List<BodyLink> parseLinks(Element body) {
    // 주소 → 링크. 다시 넣어도 처음 자리(본문 순서)는 그대로다
    Map<String, BodyLink> links = new LinkedHashMap<>();
    for (Element link : body.select("a[href]")) {
      String url = link.attr("href").strip();
      if (!isSaleLink(url)) {
        continue;
      }
      String text = link.text().strip();
      // 글자 단위로 잘라 이모지(서로게이트 쌍)를 반으로 가르지 않는다. 반쪽 문자는 jsonb에 저장되지 않는다.
      if (text.codePointCount(0, text.length()) > MAX_LINK_TEXT_LENGTH) {
        text = text.substring(0, text.offsetByCodePoints(0, MAX_LINK_TEXT_LENGTH));
      }
      BodyLink seen = links.get(url);
      if (seen == null) {
        if (links.size() < MAX_LINKS) {
          links.put(url, new BodyLink(text.isEmpty() ? url : text, url));
        }
      } else if (seen.getText().equals(url) && !text.isEmpty()) {
        links.put(url, new BodyLink(text, url));
      }
    }
    return new ArrayList<>(links.values());
  }

  private boolean isSaleLink(String url) {
    if (url.length() > MAX_LINK_URL_LENGTH || url.contains(ATTACHMENT_MARK)) {
      return false;
    }
    URI uri;
    try {
      uri = new URI(url);
    } catch (URISyntaxException e) {
      return false;
    }
    String scheme = uri.getScheme();
    boolean http = "http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme);
    // 호스트가 없는 주소(https:foo, http:///x, 밑줄이 든 호스트 등)는 판매 페이지가 아니고 geekhack 검사도 빠져나간다
    return http && uri.getHost() != null && !ImageDownloader.isGeekhack(uri);
  }

  private String removeSessionId(String url) {
    return url.replaceFirst("PHPSESSID=[^&;]*[&;]", "");
  }
}

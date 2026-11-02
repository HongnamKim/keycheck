package com.keycheck.crawl.service;

import com.keycheck.classify.service.Classifier;
import com.keycheck.crawl.client.GeekhackClient;
import com.keycheck.crawl.client.RequestGate;
import com.keycheck.crawl.config.GeekhackProperties;
import com.keycheck.crawl.dto.BoardTopic;
import com.keycheck.crawl.dto.RawImage;
import com.keycheck.crawl.dto.TopicPage;
import com.keycheck.crawl.parser.BoardListParser;
import com.keycheck.crawl.parser.TopicParser;
import com.keycheck.image.dto.Selection;
import com.keycheck.image.service.RepresentativeImageSelector;
import com.keycheck.post.domain.Post;
import com.keycheck.post.domain.SummaryStatus;
import com.keycheck.summary.dto.PostSource;
import com.keycheck.summary.dto.SummaryResult;
import com.keycheck.summary.service.Summarizer;
import java.time.Clock;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 게시판 목록을 읽어 새 글은 저장하고 이미 있는 글은 조회수·댓글 수를 갱신한다 (설계 4.3).
 *
 * <p>요청 간격을 기다리는 동안 DB 트랜잭션을 잡지 않도록 저장은 CrawlStore에 맡긴다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CrawlService {
  private static final int BOARD_PAGES = 2;

  private final GeekhackClient client;
  private final RequestGate gate;
  private final BoardListParser boardListParser;
  private final TopicParser topicParser;
  private final Classifier classifier;
  private final RepresentativeImageSelector imageSelector;
  private final Summarizer summarizer;
  private final CrawlStore store;
  private final GeekhackProperties properties;
  private final Clock clock;

  public void crawl() {
    for (int page = 0; page < BOARD_PAGES; page++) {
      for (BoardTopic topic : readBoardPage(page)) {
        try {
          process(topic);
        } catch (RuntimeException e) {
          log.error("글 수집 실패: topic={}", topic.getTopicId(), e);
        }
      }
    }
  }

  private List<BoardTopic> readBoardPage(int page) {
    try {
      gate.acquire();
      return boardListParser.parse(client.fetchBoardPage(page));
    } catch (RuntimeException e) {
      log.error("게시판 목록 수집 실패: page={}", page, e);
      return List.of();
    }
  }

  private void process(BoardTopic topic) {
    if (store.updateIfExists(topic, clock.instant())) {
      return;
    }
    if (CrawlStore.isDeleted(topic)) {
      return;
    }

    gate.acquire();
    TopicPage page = topicParser.parseFirstPage(client.fetchTopic(topic.getTopicId()));
    Post post = newPost(topic, page);
    Selection selection = imageSelector.select(page.getImages());
    for (int i = 0; i < page.getImages().size(); i++) {
      RawImage image = page.getImages().get(i);
      post.addImage(
          image.getUrl(),
          image.getSource(),
          image.getPosition(),
          selection.getExcludedReasons().get(i));
    }
    summarize(post);
    store.saveNew(post, selection.getRepresentativeIndex());
  }

  private Post newPost(BoardTopic topic, TopicPage page) {
    return Post.create(
        topic.getTopicId(),
        topic.getTitle(),
        topic.getAuthor(),
        classifier.classify(topic.getTitle(), page.getBodyText()),
        page.getPostedAt(),
        topic.getViews(),
        topic.getReplies(),
        clock.instant(),
        properties.getBaseUrl() + "/index.php?topic=" + topic.getTopicId() + ".0",
        page.getBodyText());
  }

  private void summarize(Post post) {
    try {
      SummaryResult result =
          summarizer.summarize(new PostSource(post.getTitle(), post.getBodyText()));
      post.setSummaryLines(result.getLines());
      post.setSpecs(result.getSpecs());
      post.setSummaryStatus(SummaryStatus.DONE);
    } catch (RuntimeException e) {
      log.warn("요약 실패: topic={}", post.getTopicId(), e);
      post.setSummaryStatus(SummaryStatus.FAILED);
    }
  }
}

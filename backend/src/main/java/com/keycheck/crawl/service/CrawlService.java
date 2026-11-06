package com.keycheck.crawl.service;

import com.keycheck.classify.service.Classifier;
import com.keycheck.crawl.client.GeekhackClient;
import com.keycheck.crawl.client.RequestGate;
import com.keycheck.crawl.config.CollectProperties;
import com.keycheck.crawl.config.GeekhackProperties;
import com.keycheck.crawl.domain.BoardBackfill;
import com.keycheck.crawl.dto.BoardTopic;
import com.keycheck.crawl.dto.TopicContent;
import com.keycheck.crawl.dto.TopicPage;
import com.keycheck.crawl.parser.BoardListParser;
import com.keycheck.crawl.parser.TopicParser;
import com.keycheck.crawl.repository.BoardBackfillRepository;
import com.keycheck.image.service.RepresentativeImageSelector;
import com.keycheck.post.domain.Board;
import com.keycheck.post.domain.Post;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.locks.ReentrantLock;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * geekhack 게시판(IC, GB) 목록을 읽어 새 글은 저장하고 이미 있는 글은 조회수·댓글 수를 갱신한다 (설계 4.3). 글에는 읽은
 * 목록의 게시판을 저장하고, 이미 있는 글이 다른 게시판 목록에 보이면(운영자가 옮긴 경우) 그 게시판으로 바꾼다.
 *
 * <ul>
 *   <li>정기 수집(crawl): 게시판마다 기본 정렬(마지막 댓글순) 0~1쪽. 새 글과 틀 글은 글 첫 페이지까지 읽는다. 글 첫
 *       페이지를 읽지 못한 새 글은 틀 글로 저장한다.
 *   <li>과거 글 확보(backfillNextPage): 목록 API가 모자랄 때 한 게시판 작성순 목록의 다음 쪽 하나. 새 글은 틀 글로만
 *       저장한다.
 *   <li>틀 글 채우기(fillSkeleton): 상세 API가 틀 글을 열 때.
 * </ul>
 *
 * <p>요청 간격을 기다리거나 geekhack 응답을 기다리는 동안 DB 트랜잭션을 잡지 않도록 저장은 CrawlStore에 맡긴다.
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
  private final CrawlStore store;
  private final BoardBackfillRepository backfillRepository;
  private final GeekhackProperties properties;
  private final CollectProperties collectProperties;
  private final Clock clock;

  /** 과거 글 확보는 게시판과 상관없이 한 번에 한 요청만 한다 */
  private final ReentrantLock backfillLock = new ReentrantLock();

  public void crawl() {
    for (Board board : Board.values()) {
      for (int page = 0; page < BOARD_PAGES; page++) {
        gate.acquire();
        List<BoardTopic> topics;
        try {
          topics = boardListParser.parse(client.fetchBoardPage(board, page));
        } catch (RuntimeException e) {
          log.error("게시판 목록 수집 실패: board={}, page={}", board, page, e);
          continue;
        }
        applyBoardPage(board, topics, true);
      }
    }
  }

  /**
   * board 작성순 목록의 다음 쪽을 받아 새 글을 틀 글로 저장하고 그 게시판의 커버리지를 넓힌다. 다른 요청이 (어느 게시판이든)
   * 확보 중이면 기다리지 않고, 끝까지 받았거나 maxWait 안에
   * 요청 간격을 얻지 못하거나 목록을 받지 못하면 하지 않는다. 글을 한 건도 읽지 못한 쪽은 받지 못한 것으로 본다. 이미 수집 기간 경계
   * 아래까지 받아 둔 게시판이면 요청하지 않고 끝까지 받은 것으로 둔다.
   *
   * @return 확보 상태를 바꿨으면(목록 한 쪽을 받아 반영했거나 경계에 닿아 끝까지 받은 것으로 뒀으면) true. 호출한 쪽은 확보 상태를 다시
   *     읽는다.
   */
  public boolean backfillNextPage(Board board, Duration maxWait) {
    if (!backfillLock.tryLock()) {
      return false;
    }
    try {
      BoardBackfill backfill = backfillRepository.current(board);
      if (backfill.isExhausted()) {
        return false;
      }
      long since = collectProperties.getSinceTopicId();
      if (backfill.getFloorTopicId() != null && backfill.getFloorTopicId() < since) {
        store.markBackfillExhausted(board);
        return true;
      }
      if (!gate.tryAcquire(maxWait)) {
        return false;
      }
      int page = backfill.getNextPage();
      List<BoardTopic> topics;
      int shownPage;
      try {
        String html = client.fetchBoardPageByFirstPost(board, page);
        topics = boardListParser.parse(html);
        shownPage = boardListParser.parseCurrentPage(html);
      } catch (RuntimeException e) {
        log.error("작성순 게시판 목록 수집 실패: board={}, page={}", board, page, e);
        return false;
      }
      if (topics.isEmpty()) {
        log.warn("작성순 게시판 목록에서 글을 읽지 못함: board={}, page={}", board, page);
        return false;
      }
      applyBoardPage(board, topics, false);
      store.recordBoardPage(board, page, shownPage, topics, since);
      return true;
    } finally {
      backfillLock.unlock();
    }
  }

  /**
   * 틀 글을 글 첫 페이지로 채운다. maxWait 안에 요청 간격을 얻지 못하거나 읽기에 실패하면 그대로 둔다.
   *
   * @param post 틀 글 (isDetailLoaded() == false)
   */
  public void fillSkeleton(Post post, Duration maxWait) {
    if (gate.tryAcquire(maxWait)) {
      readAndFill(post);
    }
  }

  /**
   * @param board topics를 읽은 목록의 게시판
   * @param readTopics true면 새 글과 틀 글의 글 첫 페이지까지 읽는다(정기 수집), false면 새 글을 틀로만 저장한다(과거 글 확보)
   */
  private void applyBoardPage(Board board, List<BoardTopic> topics, boolean readTopics) {
    for (BoardTopic topic : topics) {
      try {
        process(board, topic, readTopics);
      } catch (RuntimeException e) {
        log.error("글 수집 실패: topic={}", topic.getTopicId(), e);
      }
    }
  }

  /**
   * 수집 기간 경계보다 작은 글은 새로 저장하지 않는다. 이미 저장된 그런 글은 목록 값만 갱신하고 글 첫 페이지로 채우지 않는다
   * (오래된 글에 새 댓글이 달려 마지막 댓글순 목록에 올라와도).
   */
  private void process(Board board, BoardTopic topic, boolean readTopics) {
    boolean beforeSince = topic.getTopicId() < collectProperties.getSinceTopicId();
    Optional<Post> existing = store.updateIfExists(board, topic, clock.instant());
    if (existing.isPresent()) {
      Post post = existing.get();
      if (readTopics && !post.isDetailLoaded() && !post.isHidden() && !beforeSince) {
        gate.acquire();
        readAndFill(post);
      }
      return;
    }
    if (CrawlStore.isDeleted(topic) || beforeSince) {
      return;
    }

    if (!readTopics) {
      store.saveSkeleton(skeleton(board, topic));
      return;
    }
    gate.acquire();
    TopicContent content;
    try {
      content = readTopic(topic.getTopicId(), topic.getTitle());
    } catch (RuntimeException e) {
      // 목록에는 보이게 틀로 남긴다. 다음 정기 수집이나 상세 열기가 다시 채운다.
      log.warn("글 첫 페이지를 읽지 못해 틀 글로 저장: topic={}", topic.getTopicId(), e);
      store.saveSkeleton(skeleton(board, topic));
      return;
    }
    store.saveNew(newPost(board, topic, content), content);
  }

  private void readAndFill(Post post) {
    try {
      TopicContent content = readTopic(post.getTopicId(), post.getTitle());
      store.fillSkeleton(post.getId(), content, clock.instant());
    } catch (RuntimeException e) {
      log.warn("틀 글 채우기 실패: topic={}", post.getTopicId(), e);
    }
  }

  /**
   * 글 첫 페이지를 읽고 분류·대표 이미지를 정한다. 새 글 저장과 틀 글 채우기가 같이 쓴다. 분류는 규칙 분류기의 임시값이고, 요약은
   * SummaryWorker가 나중에 하며 요약기가 정한 분류가 있으면 그것으로 바꾼다.
   */
  private TopicContent readTopic(long topicId, String title) {
    TopicPage page = topicParser.parseFirstPage(client.fetchTopic(topicId));
    return new TopicContent(
        page,
        classifier.classify(title, page.getBodyText()),
        imageSelector.select(page.getImages()));
  }

  private Post newPost(Board board, BoardTopic topic, TopicContent content) {
    TopicPage page = content.getPage();
    Post post =
        Post.create(
            board,
            topic.getTopicId(),
            topic.getTitle(),
            topic.getAuthor(),
            content.getCategory(),
            page.getPostedAt(),
            topic.getViews(),
            topic.getReplies(),
            clock.instant(),
            originalUrl(topic),
            page.getBodyText());
    post.updateLastPostAt(topic.getLastPostAt());
    return post;
  }

  /** 본문이 없으므로 미분류로 둔다. 채울 때 제목과 본문으로 분류한다. */
  private Post skeleton(Board board, BoardTopic topic) {
    Post post =
        Post.createSkeleton(
            board,
            topic.getTopicId(),
            topic.getTitle(),
            topic.getAuthor(),
            topic.getViews(),
            topic.getReplies(),
            clock.instant(),
            originalUrl(topic));
    post.updateLastPostAt(topic.getLastPostAt());
    return post;
  }

  private String originalUrl(BoardTopic topic) {
    return properties.getBaseUrl() + "/index.php?topic=" + topic.getTopicId() + ".0";
  }
}

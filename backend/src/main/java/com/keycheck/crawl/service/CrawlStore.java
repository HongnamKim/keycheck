package com.keycheck.crawl.service;

import com.keycheck.crawl.dto.BoardTopic;
import com.keycheck.crawl.dto.RawImage;
import com.keycheck.crawl.dto.TopicContent;
import com.keycheck.crawl.dto.TopicPage;
import com.keycheck.crawl.repository.BoardBackfillRepository;
import com.keycheck.image.dto.Selection;
import com.keycheck.post.domain.Board;
import com.keycheck.post.domain.Post;
import com.keycheck.post.domain.PostImage;
import com.keycheck.post.repository.PostRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** 수집한 글을 글 하나 단위의 트랜잭션으로 저장한다. */
@Component
@RequiredArgsConstructor
public class CrawlStore {
  /** 작성자가 글을 지우면 제목만 이렇게 남는다 */
  private static final String DELETED_TITLE = "deleted";

  private final PostRepository postRepository;
  private final BoardBackfillRepository backfillRepository;

  static boolean isDeleted(BoardTopic topic) {
    return DELETED_TITLE.equals(topic.getTitle());
  }

  /**
   * @param board topic을 읽은 목록의 게시판. 저장된 글의 게시판과 다르면(운영자가 옮긴 경우) 바꾼다.
   * @return 이미 저장된 글이면 갱신한 글, 없으면 empty
   */
  @Transactional
  public Optional<Post> updateIfExists(Board board, BoardTopic topic, Instant now) {
    Optional<Post> found = postRepository.findByTopicId(topic.getTopicId());
    found.ifPresent(
        post -> {
          post.setBoard(board);
          post.setTitle(topic.getTitle());
          post.setViews(topic.getViews());
          post.setReplies(topic.getReplies());
          post.setStatsCheckedAt(now);
          post.updateLastPostAt(topic.getLastPostAt());
          if (isDeleted(topic)) {
            post.setHidden(true);
          }
        });
    return found;
  }

  @Transactional
  public void saveNew(Post post, TopicContent content) {
    postRepository.save(post);
    writeContent(post, content);
  }

  @Transactional
  public void saveSkeleton(Post post) {
    postRepository.save(post);
  }

  /**
   * 행을 잠그고 다시 읽어 아직 틀 글일 때만 채운다. 두 요청이 동시에 채워도 이미지가 두 번 들어가지 않는다.
   *
   * @return 채웠으면 true
   */
  @Transactional
  public boolean fillSkeleton(Long postId, TopicContent content, Instant now) {
    Post post = postRepository.findByIdForUpdate(postId).orElse(null);
    if (post == null || post.isDetailLoaded()) {
      return false;
    }
    TopicPage page = content.getPage();
    post.fillDetail(page.getBodyText(), page.getPostedAt(), page.getViews(), now);
    post.setCategory(content.getCategory());
    writeContent(post, content);
    return true;
  }

  /**
   * board 작성순 목록 page쪽의 글을 모두 반영한 뒤 부른다. 이어서 받은 쪽이면 그 게시판의 커버리지를 넓힌다
   * (BoardBackfill.recordPage).
   */
  @Transactional
  public void recordBoardPage(Board board, int page, int shownPage, List<BoardTopic> topics) {
    backfillRepository
        .lockCurrent(board)
        .recordPage(page, shownPage, topics.stream().map(BoardTopic::getTopicId).toList());
  }

  /**
   * 영속 상태인 post에 이미지(제외 사유 포함)를 넣고, 이미지를 저장한 뒤 대표 이미지를 정한다. 요약 상태는 PENDING(기본값)
   * 그대로 두어 SummaryWorker가 요약하게 한다.
   */
  private void writeContent(Post post, TopicContent content) {
    List<RawImage> images = content.getPage().getImages();
    Selection selection = content.getSelection();
    List<PostImage> added = new ArrayList<>();
    for (int i = 0; i < images.size(); i++) {
      RawImage image = images.get(i);
      added.add(
          post.addImage(
              image.getUrl(),
              image.getSource(),
              image.getPosition(),
              selection.getExcludedReasons().get(i)));
    }

    // 이미지 ID를 얻으려면 먼저 저장해야 한다. merge(saveAndFlush)는 새 이미지를 복사본으로 저장하므로 flush만 한다.
    postRepository.flush();
    if (selection.getRepresentativeIndex() != null) {
      post.setRepresentativeImageId(added.get(selection.getRepresentativeIndex()).getId());
    }
  }
}

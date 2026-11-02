package com.keycheck.crawl.service;

import com.keycheck.crawl.dto.BoardTopic;
import com.keycheck.post.domain.Post;
import com.keycheck.post.repository.PostRepository;
import java.time.Instant;
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

  static boolean isDeleted(BoardTopic topic) {
    return DELETED_TITLE.equals(topic.getTitle());
  }

  /**
   * @return 이미 저장된 글이면 갱신하고 true, 없으면 false
   */
  @Transactional
  public boolean updateIfExists(BoardTopic topic, Instant now) {
    Optional<Post> found = postRepository.findByTopicId(topic.getTopicId());
    if (found.isEmpty()) {
      return false;
    }
    Post post = found.get();
    post.setTitle(topic.getTitle());
    post.setViews(topic.getViews());
    post.setReplies(topic.getReplies());
    post.setStatsCheckedAt(now);
    if (isDeleted(topic)) {
      post.setHidden(true);
    }
    return true;
  }

  /**
   * @param representativeIndex post.getImages()에서 대표 이미지의 위치. 없으면 null.
   */
  @Transactional
  public void saveNew(Post post, Integer representativeIndex) {
    // 이미지 ID를 얻으려면 먼저 저장해야 한다
    postRepository.saveAndFlush(post);
    if (representativeIndex != null) {
      post.setRepresentativeImageId(post.getImages().get(representativeIndex).getId());
    }
  }
}

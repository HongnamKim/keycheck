package com.keycheck.post.repository;

import com.keycheck.post.domain.PostImage;
import java.util.List;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

public interface PostImageRepository extends JpaRepository<PostImage, Long> {
  /**
   * 축소본 대상. 수집 기간 안(글 번호가 sinceTopicId 이상)의 본문까지 읽은 숨기지 않은 글에서, 대표 이미지 후보에서 빠지지 않았고 아직
   * 축소본이 없으며 실패가 maxFailures번 미만인 이미지. 미리 읽기로 채우고 아직 아무도 열지 않은 글을 뒤로, 그다음 본문을 늦게 채운
   * 글부터, 같으면 글 번호가 큰 글부터, 글 안에서는 대표 이미지 먼저 그다음 본문 순서. 사용자가 연 글보다 나중에 미리 읽은 글이 그 글의
   * 축소본을 늦추지 않게 한다(요약 대상과 같다). 글을 함께 읽는다(트랜잭션 밖에서 대표 여부를 본다).
   */
  @Query(
      "select i from PostImage i join fetch i.post p"
          + " where i.thumbKey is null and i.excludedReason is null"
          + " and i.thumbFailures < :maxFailures"
          + " and p.hidden = false and p.detailFetchedAt is not null"
          + " and p.topicId >= :sinceTopicId"
          + " order by case when p.prefetched = true then 1 else 0 end,"
          + " p.detailFetchedAt desc, p.topicId desc,"
          + " case when i.id = p.representativeImageId then 0 else 1 end, i.position")
  List<PostImage> findThumbTargets(long sinceTopicId, int maxFailures, Limit limit);

  /** 같은 글의 같은 주소 행 모두에 축소본 객체 이름을 넣는다. 다른 값은 그대로 둔다. */
  @Transactional
  @Modifying
  @Query("update PostImage i set i.thumbKey = :key where i.post.id = :postId and i.url = :url")
  void markThumbs(Long postId, String url, String key);

  /** 대표 이미지의 큰 축소본 객체 이름만 넣는다. 다른 값은 그대로 둔다. */
  @Transactional
  @Modifying
  @Query("update PostImage i set i.thumbLargeKey = :key where i.id = :id")
  void setThumbLargeKey(Long id, String key);

  /** 같은 글의 같은 주소 행 모두의 축소본 실패 횟수를 하나 늘린다(같은 원본이라 결과가 같다). 다른 값은 그대로 둔다. */
  @Transactional
  @Modifying
  @Query(
      "update PostImage i set i.thumbFailures = i.thumbFailures + 1"
          + " where i.post.id = :postId and i.url = :url")
  void incrementThumbFailures(Long postId, String url);

  /** 시도 전에 incrementThumbFailures로 센 실패를 되돌린다(이미지 탓이 아닌 결과). 다른 값은 그대로 둔다. */
  @Transactional
  @Modifying
  @Query(
      "update PostImage i set i.thumbFailures = i.thumbFailures - 1"
          + " where i.post.id = :postId and i.url = :url")
  void decrementThumbFailures(Long postId, String url);
}

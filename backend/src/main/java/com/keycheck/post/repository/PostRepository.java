package com.keycheck.post.repository;

import com.keycheck.post.domain.Board;
import com.keycheck.post.domain.Category;
import com.keycheck.post.domain.Post;
import com.keycheck.post.domain.SpecItem;
import com.keycheck.post.domain.Subcategory;
import com.keycheck.post.domain.SummaryStatus;
import com.keycheck.post.dto.PostedAtAnchor;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

public interface PostRepository extends JpaRepository<Post, Long> {
  Optional<Post> findByTopicId(Long topicId);

  /**
   * 최근순(글 번호 내림차순) 목록
   *
   * @param floorTopicId 이 번호 이상만 (커버리지)
   * @param beforeTopicId 이 번호 미만만 (cursor)
   * @param board null이면 두 게시판 모두
   * @param category null이면 전체
   * @param subcategory null이면 세부 분류로 거르지 않는다
   */
  @Query(
      "select p from Post p where p.hidden = false"
          + " and p.topicId >= :floorTopicId and p.topicId < :beforeTopicId"
          + " and (:board is null or p.board = :board)"
          + " and (:category is null or p.category = :category)"
          + " and (:subcategory is null or p.subcategory = :subcategory)"
          + " order by p.topicId desc")
  List<Post> findRecent(
      long floorTopicId,
      long beforeTopicId,
      Board board,
      Category category,
      Subcategory subcategory,
      Limit limit);

  /**
   * 최근순 목록에서 afterTopicId 글보다 위(글 번호가 큰) 글을 가까운 것부터(글 번호 오름차순). 매개변수는 findRecent와 같다.
   *
   * @param afterTopicId 이 번호 초과만
   */
  @Query(
      "select p from Post p where p.hidden = false"
          + " and p.topicId >= :floorTopicId and p.topicId > :afterTopicId"
          + " and (:board is null or p.board = :board)"
          + " and (:category is null or p.category = :category)"
          + " and (:subcategory is null or p.subcategory = :subcategory)"
          + " order by p.topicId asc")
  List<Post> findRecentAbove(
      long floorTopicId,
      long afterTopicId,
      Board board,
      Category category,
      Subcategory subcategory,
      Limit limit);

  /**
   * 조회수순(조회수 내림차순, 같으면 글 번호 내림차순) 목록
   *
   * @param views cursor의 조회수
   * @param topicId cursor의 글 번호. (views, topicId)보다 뒤에 오는 글만 준다.
   * @param sinceTopicId 이 번호 이상만 (수집 기간)
   * @param board null이면 두 게시판 모두
   * @param category null이면 전체
   * @param subcategory null이면 세부 분류로 거르지 않는다
   */
  @Query(
      "select p from Post p where p.hidden = false"
          + " and (p.views < :views or (p.views = :views and p.topicId < :topicId))"
          + " and p.topicId >= :sinceTopicId"
          + " and (:board is null or p.board = :board)"
          + " and (:category is null or p.category = :category)"
          + " and (:subcategory is null or p.subcategory = :subcategory)"
          + " order by p.views desc, p.topicId desc")
  List<Post> findByViews(
      int views,
      long topicId,
      long sinceTopicId,
      Board board,
      Category category,
      Subcategory subcategory,
      Limit limit);

  /**
   * 조회수순 목록에서 (views, topicId)보다 앞에 오는 글을 가까운 것부터(조회수 오름차순, 같으면 글 번호 오름차순). 매개변수는
   * findByViews와 같다.
   */
  @Query(
      "select p from Post p where p.hidden = false"
          + " and (p.views > :views or (p.views = :views and p.topicId > :topicId))"
          + " and p.topicId >= :sinceTopicId"
          + " and (:board is null or p.board = :board)"
          + " and (:category is null or p.category = :category)"
          + " and (:subcategory is null or p.subcategory = :subcategory)"
          + " order by p.views asc, p.topicId asc")
  List<Post> findByViewsAbove(
      int views,
      long topicId,
      long sinceTopicId,
      Board board,
      Category category,
      Subcategory subcategory,
      Limit limit);

  /**
   * 작성일 추정의 기준점(작성일이 있는 글, 숨김 포함). 주어진 글 번호마다 앞뒤로 가장 가까운 글 하나씩과, 글 번호가 가장 작은·가장 큰 글을
   * 모은다. 보간에는 앞뒤 하나씩, 외삽에는 가장 가까운 글과 그쪽 끝 글이 필요하므로 이것으로 충분하다. 글 수와 상관없이 쿼리 한 번이다.
   */
  @Query(
      nativeQuery = true,
      value =
          "select anchor.topic_id as topicId, anchor.posted_at as postedAt"
              + " from post target cross join lateral ("
              + "   (select p.topic_id, p.posted_at from post p"
              + "    where p.posted_at is not null and p.topic_id < target.topic_id"
              + "    order by p.topic_id desc limit 1)"
              + "   union all"
              + "   (select p.topic_id, p.posted_at from post p"
              + "    where p.posted_at is not null and p.topic_id > target.topic_id"
              + "    order by p.topic_id limit 1)"
              + " ) anchor"
              + " where target.topic_id in (:topicIds)"
              + " union"
              + " (select p.topic_id, p.posted_at from post p"
              + "  where p.posted_at is not null order by p.topic_id limit 1)"
              + " union"
              + " (select p.topic_id, p.posted_at from post p"
              + "  where p.posted_at is not null order by p.topic_id desc limit 1)")
  List<PostedAtAnchor> findPostedAtAnchors(Collection<Long> topicIds);

  /** 상세용. 이미지까지 함께 읽어 트랜잭션 밖에서도 쓸 수 있다. */
  @EntityGraph(attributePaths = "images")
  Optional<Post> findWithImagesById(Long id);

  /** 틀 글 채우기용. 동시에 채우지 않도록 행을 잠그고 읽는다. */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select p from Post p where p.id = :id")
  Optional<Post> findByIdForUpdate(Long id);

  /** 조회수·댓글 수·확인 시각만 바꾼다. 그사이 다른 쓰기가 바꾼 값(숨김·제목 등)은 그대로 둔다. */
  @Transactional
  @Modifying
  @Query(
      "update Post p set p.views = :views, p.replies = :replies, p.statsCheckedAt = :checkedAt"
          + " where p.id = :id")
  void updateStats(Long id, int views, int replies, Instant checkedAt);

  /**
   * 요약 대상. 본문까지 읽은 숨기지 않은 글 중 요약 대기(PENDING)인 글, redoFake면 가짜 요약기('fake', FakeSummarizer.ID)로
   * 요약한 글, redoMissingSubcategory면 세부 분류가 없는 키보드·키캡 요약까지. 요약 대기 글을 먼저, 그 안에서는 본문을 늦게 채운
   * 글(방금 상세를 연 틀 글)부터, 같으면 글 번호가 큰 글부터.
   */
  @Query(
      "select p from Post p where p.hidden = false and p.detailFetchedAt is not null"
          + " and (p.summaryStatus = com.keycheck.post.domain.SummaryStatus.PENDING"
          + " or (:redoFake = true and p.summaryStatus = com.keycheck.post.domain.SummaryStatus.DONE"
          + " and p.summarizedBy = 'fake')"
          + " or (:redoMissingSubcategory = true"
          + " and p.summaryStatus = com.keycheck.post.domain.SummaryStatus.DONE"
          + " and p.subcategory is null"
          + " and p.category in (com.keycheck.post.domain.Category.KEYBOARD,"
          + " com.keycheck.post.domain.Category.KEYCAP)))"
          + " order by case when p.summaryStatus = com.keycheck.post.domain.SummaryStatus.PENDING"
          + " then 0 else 1 end, p.detailFetchedAt desc, p.topicId desc")
  List<Post> findSummaryTargets(boolean redoFake, boolean redoMissingSubcategory, Limit limit);

  /**
   * 요약을 넣고 DONE으로 바꾼다. category가 null이면(가짜 요약기) 분류와 세부 분류를 그대로 두고, 아니면 둘 다 결과로 바꾼다(세부 분류가
   * 없으면 null). 다른 컬럼은 건드리지 않는다.
   */
  @Transactional
  @Modifying
  @Query(
      "update Post p set p.summaryStatus = com.keycheck.post.domain.SummaryStatus.DONE,"
          + " p.summaryLines = :lines, p.specs = :specs, p.summarizedBy = :summarizedBy,"
          + " p.subcategory = case when :category is null then p.subcategory else :subcategory end,"
          + " p.category = coalesce(:category, p.category)"
          + " where p.id = :id")
  void saveSummary(
      Long id,
      List<String> lines,
      List<SpecItem> specs,
      Category category,
      Subcategory subcategory,
      String summarizedBy);

  @Transactional
  @Modifying
  @Query("update Post p set p.summaryStatus = :status where p.id = :id")
  void updateSummaryStatus(Long id, SummaryStatus status);
}

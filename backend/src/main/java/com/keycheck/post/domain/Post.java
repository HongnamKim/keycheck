package com.keycheck.post.domain;

import com.keycheck.image.domain.ImageSource;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.DynamicUpdate;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

@Entity
@Getter
@Table(name = "post")
// 수집과 요약 작업이 동시에 같은 글을 저장해도 서로 바꾼 컬럼을 덮지 않게, 바뀐 컬럼만 UPDATE한다
@DynamicUpdate
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Post {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(unique = true)
  private Long topicId;

  /** 글을 읽어 온 게시판. 운영자가 글을 다른 게시판으로 옮기면 그 게시판 목록에서 읽을 때 바뀐다. */
  @Setter
  @Enumerated(EnumType.STRING)
  @Column(length = 10)
  private Board board;

  @Setter
  @Column(length = 500)
  private String title;

  @Setter
  @Column(length = 200)
  private String author;

  @Setter
  @Enumerated(EnumType.STRING)
  @Column(length = 20)
  private Category category;

  /** 세부 분류(키보드 배열, 키캡 제조사). 요약기가 정한다. 기타·미분류 글이나 아직 정하지 않았으면 null. */
  @Setter
  @Enumerated(EnumType.STRING)
  @Column(length = 20)
  private Subcategory subcategory;

  @Setter private Instant postedAt;

  @Setter private int views;

  @Setter private int replies;

  @Setter private Instant statsCheckedAt;

  @Column(length = 500)
  private String originalUrl;

  /** 요약용 원문 텍스트. API로 노출하지 않는다. 틀 글이면 null. */
  @Column(columnDefinition = "text")
  private String bodyText;

  /** 글 첫 페이지를 읽어 본문·이미지를 채운 시각. null이면 틀 글(게시판 목록에서만 읽은 글). */
  private Instant detailFetchedAt;

  /** 백그라운드 미리 읽기가 이 틀 글을 읽지 못한 횟수. PrefetchWorker.MAX_FAILURES에 닿으면 미리 읽기에서 뺀다. */
  private int prefetchFailures;

  /** 백그라운드 미리 읽기가 채웠고 아직 아무도 상세를 열지 않은 글. 요약·축소본 순서에서 뒤로 간다(사용자가 연 글이 먼저). */
  private boolean prefetched;

  /** 게시판 목록에서 읽은 마지막 게시물(댓글, 없으면 원글) 시각. 아직 읽지 못했으면 null. */
  private Instant lastPostAt;

  @Setter
  @Enumerated(EnumType.STRING)
  @Column(length = 20)
  private SummaryStatus summaryStatus = SummaryStatus.PENDING;

  @Setter
  @JdbcTypeCode(SqlTypes.JSON)
  private List<String> summaryLines;

  @Setter
  @JdbcTypeCode(SqlTypes.JSON)
  private List<SpecItem> specs;

  /** 요약을 만든 요약기 ('fake' 또는 Claude 모델 ID). 요약이 없으면 null. */
  @Column(length = 100)
  private String summarizedBy;

  /** 대표 이미지(post_image.id). 순환 참조를 피하려고 연관관계 대신 ID만 둔다. */
  @Setter private Long representativeImageId;

  @Setter private boolean hidden;

  @OneToMany(mappedBy = "post", cascade = CascadeType.ALL, orphanRemoval = true)
  @OrderBy("position")
  private List<PostImage> images = new ArrayList<>();

  @CreationTimestamp private Instant createdAt;

  @UpdateTimestamp private Instant updatedAt;

  /** 글 첫 페이지까지 읽은 글 */
  public static Post create(
      Board board,
      Long topicId,
      String title,
      String author,
      Category category,
      Instant postedAt,
      int views,
      int replies,
      Instant statsCheckedAt,
      String originalUrl,
      String bodyText) {
    Post post = new Post();
    post.board = board;
    post.topicId = topicId;
    post.title = title;
    post.author = author;
    post.category = category;
    post.postedAt = postedAt;
    post.views = views;
    post.replies = replies;
    post.statsCheckedAt = statsCheckedAt;
    post.originalUrl = originalUrl;
    post.bodyText = bodyText;
    post.detailFetchedAt = statsCheckedAt;
    return post;
  }

  /** 게시판 목록에서만 읽은 틀 글. 본문·작성일·이미지는 상세를 처음 열 때 채운다(fillDetail). */
  public static Post createSkeleton(
      Board board,
      Long topicId,
      String title,
      String author,
      int views,
      int replies,
      Instant statsCheckedAt,
      String originalUrl) {
    Post post = new Post();
    post.board = board;
    post.topicId = topicId;
    post.title = title;
    post.author = author;
    post.category = Category.UNCLASSIFIED;
    post.views = views;
    post.replies = replies;
    post.statsCheckedAt = statsCheckedAt;
    post.originalUrl = originalUrl;
    return post;
  }

  /** 게시판 목록에서 읽은 마지막 게시물 시각으로 바꾼다. 읽지 못해 null이면 이전 값을 둔다. */
  public void updateLastPostAt(Instant lastPostAt) {
    if (lastPostAt != null) {
      this.lastPostAt = lastPostAt;
    }
  }

  public boolean isDetailLoaded() {
    return detailFetchedAt != null;
  }

  /**
   * 틀 글을 글 첫 페이지에서 읽은 값으로 채운다. 이미지·분류·요약은 따로 넣는다. 글 첫 페이지에는 댓글 수가 없으므로 statsCheckedAt은 그대로
   * 두어, 통계 API가 조회수·댓글 수를 함께 다시 확인하게 한다.
   *
   * @param prefetched 백그라운드 미리 읽기가 채우면 true
   */
  public void fillDetail(
      String bodyText, Instant postedAt, int views, Instant fetchedAt, boolean prefetched) {
    this.bodyText = bodyText;
    this.postedAt = postedAt;
    this.views = views;
    this.detailFetchedAt = fetchedAt;
    this.prefetched = prefetched;
  }

  /**
   * @param excludedReason 대표 이미지 후보에서 빠진 이유. 후보면 null.
   */
  public PostImage addImage(String url, ImageSource source, int position, String excludedReason) {
    PostImage image = PostImage.create(this, url, source, position, excludedReason);
    images.add(image);
    return image;
  }
}

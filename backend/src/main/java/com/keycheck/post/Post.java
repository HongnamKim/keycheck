package com.keycheck.post;

import com.keycheck.image.ImageSource;
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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

@Entity
@Getter
@Table(name = "post")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Post {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(unique = true)
  private Long topicId;

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

  @Setter private Instant postedAt;

  @Setter private int views;

  @Setter private int replies;

  @Setter private Instant statsCheckedAt;

  @Column(length = 500)
  private String originalUrl;

  /** 요약용 원문 텍스트. API로 노출하지 않는다. */
  @Column(columnDefinition = "text")
  private String bodyText;

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

  /** 대표 이미지(post_image.id). 순환 참조를 피하려고 연관관계 대신 ID만 둔다. */
  @Setter private Long representativeImageId;

  @Setter private boolean hidden;

  @OneToMany(mappedBy = "post", cascade = CascadeType.ALL, orphanRemoval = true)
  @OrderBy("position")
  private List<PostImage> images = new ArrayList<>();

  @CreationTimestamp private Instant createdAt;

  @UpdateTimestamp private Instant updatedAt;

  public static Post create(
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
    return post;
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

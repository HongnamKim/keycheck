package com.keycheck.post.domain;

import com.keycheck.image.domain.ImageSource;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Getter
@Table(name = "post_image")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PostImage {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "post_id")
  private Post post;

  @Column(columnDefinition = "text")
  private String url;

  @Enumerated(EnumType.STRING)
  @Column(length = 20)
  private ImageSource source;

  private int position;

  /** 대표 이미지 후보에서 빠진 이유. 후보면 null. */
  @Setter
  @Column(length = 100)
  private String excludedReason;

  /** 축소본(가로 최대 640px) 객체 이름. 아직 없으면 null. 같은 글의 같은 주소 행은 같은 객체를 가리킨다. */
  @Column(columnDefinition = "text")
  private String thumbKey;

  /** 대표 이미지의 큰 축소본(가로 최대 1280px) 객체 이름. 대표 이미지가 아니거나 아직 없으면 null. */
  @Column(columnDefinition = "text")
  private String thumbLargeKey;

  /** 축소본을 만들지 못한 횟수. ThumbnailWorker.MAX_FAILURES에 닿으면 더 시도하지 않고 화면은 원본을 쓴다. */
  private int thumbFailures;

  static PostImage create(
      Post post, String url, ImageSource source, int position, String excludedReason) {
    PostImage image = new PostImage();
    image.post = post;
    image.url = url;
    image.source = source;
    image.position = position;
    image.excludedReason = excludedReason;
    return image;
  }
}

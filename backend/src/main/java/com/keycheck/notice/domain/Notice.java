package com.keycheck.notice.domain;

import com.keycheck.post.domain.KoreanVendor;
import com.keycheck.post.domain.SummaryStatus;
import jakarta.persistence.CascadeType;
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

/** 디스코드 판매 공지 한 건 (판매처 공지 서버의 메시지 하나) */
@Entity
@Getter
@Table(name = "notice")
// 수집과 요약 작업이 같은 공지를 동시에 저장해도 서로 바꾼 컬럼을 덮지 않게, 바뀐 컬럼만 UPDATE한다
@DynamicUpdate
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Notice {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  /** 원래 서버 메시지 id */
  @Column(unique = true)
  private long sourceMessageId;

  /** 팔로우 채널에 복사된 메시지 id. 봇이 받은 공지만 있다. */
  private Long channelMessageId;

  private long channelId;

  @Enumerated(EnumType.STRING)
  @Column(length = 20)
  private KoreanVendor vendor;

  @Enumerated(EnumType.STRING)
  @Column(length = 20)
  private NoticeOrigin origin;

  private Instant postedAt;

  @Column(columnDefinition = "text")
  private String content;

  /** 본문에 글이 있는지. 이미지만 올라온 딸린 메시지는 false. */
  private boolean hasText;

  /** 디스코드가 준 메시지 원문 JSON */
  @JdbcTypeCode(SqlTypes.JSON)
  @Column(columnDefinition = "jsonb")
  private String raw;

  /** 이 메시지가 딸린 글 공지. 글 공지 자신이거나 짝을 못 찾았으면 null. */
  @Setter
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "parent_id")
  private Notice parent;

  @Setter
  @Enumerated(EnumType.STRING)
  @Column(length = 20)
  private SummaryStatus summaryStatus = SummaryStatus.PENDING;

  @Column(length = 100)
  private String summarizedBy;

  private Instant summarizedAt;

  @OneToMany(mappedBy = "notice", cascade = CascadeType.ALL, orphanRemoval = true)
  @OrderBy("position")
  private List<NoticeAttachment> attachments = new ArrayList<>();

  @CreationTimestamp private Instant createdAt;

  @UpdateTimestamp private Instant updatedAt;

  public static Notice create(
      long sourceMessageId,
      Long channelMessageId,
      long channelId,
      KoreanVendor vendor,
      NoticeOrigin origin,
      Instant postedAt,
      String content,
      boolean hasText,
      String rawJson) {
    Notice notice = new Notice();
    notice.sourceMessageId = sourceMessageId;
    notice.channelMessageId = channelMessageId;
    notice.channelId = channelId;
    notice.vendor = vendor;
    notice.origin = origin;
    notice.postedAt = postedAt;
    notice.content = content;
    notice.hasText = hasText;
    notice.raw = rawJson;
    return notice;
  }

  public NoticeAttachment addAttachment(
      long attachmentId, int position, String filename, String sourceUrl) {
    NoticeAttachment attachment =
        NoticeAttachment.create(this, attachmentId, position, filename, sourceUrl);
    attachments.add(attachment);
    return attachment;
  }
}

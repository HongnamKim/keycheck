package com.keycheck.notice.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@Table(name = "notice_attachment")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class NoticeAttachment {
  /** 디스코드 첨부 id */
  @Id private Long id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "notice_id")
  private Notice notice;

  private int position;

  @Column(columnDefinition = "text")
  private String filename;

  /** 디스코드 CDN 주소. 만료되면 새로 받아 갱신한다. */
  @Column(columnDefinition = "text")
  private String sourceUrl;

  @Enumerated(EnumType.STRING)
  @Column(length = 20)
  private ArchiveStatus archiveStatus = ArchiveStatus.PENDING;

  /** 원본 사진의 보이는 폭(px). 모르면 null */
  private Integer width;

  /** 원본 사진의 보이는 높이(px). 모르면 null */
  private Integer height;

  /** 보관에 실패한 횟수 */
  private int failures;

  static NoticeAttachment create(
      Notice notice, long id, int position, String filename, String sourceUrl) {
    NoticeAttachment attachment = new NoticeAttachment();
    attachment.id = id;
    attachment.notice = notice;
    attachment.position = position;
    attachment.filename = filename;
    attachment.sourceUrl = sourceUrl;
    return attachment;
  }
}

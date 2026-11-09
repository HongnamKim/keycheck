package com.keycheck.notice.domain;

import com.keycheck.notice.dto.SaleItemDraft;
import com.keycheck.post.domain.KoreanVendor;
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
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** 공지에서 뽑은 판매 건. 판매처와 공지 시각은 공지에서 복사한다. */
@Entity
@Getter
@Table(name = "sale_item")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SaleItem {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "notice_id")
  private Notice notice;

  private int position;

  @Enumerated(EnumType.STRING)
  @Column(length = 20)
  private KoreanVendor vendor;

  private Instant postedAt;

  @Column(length = 200)
  private String productName;

  @Enumerated(EnumType.STRING)
  @Column(length = 20)
  private SaleCategory category;

  @Enumerated(EnumType.STRING)
  @Column(length = 20)
  private SaleType saleType;

  @Enumerated(EnumType.STRING)
  @Column(length = 20)
  private SalePhase phase;

  private LocalDate startDate;

  private LocalTime startTime;

  private LocalDate endDate;

  private LocalTime endTime;

  @Column(columnDefinition = "text")
  private String shippingNote;

  @JdbcTypeCode(SqlTypes.JSON)
  private List<String> summaryLines;

  @JdbcTypeCode(SqlTypes.JSON)
  private List<SaleLink> links;

  /** 이 건에 쓸 첨부 id 목록. 첫째가 대표 이미지. */
  @JdbcTypeCode(SqlTypes.JSON)
  private List<Long> imageIds;

  @Setter private boolean hidden;

  @CreationTimestamp private Instant createdAt;

  public static SaleItem create(Notice notice, int position, SaleItemDraft draft) {
    SaleItem item = new SaleItem();
    item.notice = notice;
    item.vendor = notice.getVendor();
    item.postedAt = notice.getPostedAt();
    item.update(position, draft);
    return item;
  }

  /** 다시 요약한 내용으로 바꾼다. id·숨김·저장 시각과 공지에서 복사한 값은 그대로 둔다. */
  public void update(int position, SaleItemDraft draft) {
    this.position = position;
    this.productName = draft.getProductName();
    this.category = draft.getCategory();
    this.saleType = draft.getSaleType();
    this.phase = draft.getPhase();
    this.startDate = draft.getStartDate();
    this.startTime = draft.getStartTime();
    this.endDate = draft.getEndDate();
    this.endTime = draft.getEndTime();
    this.shippingNote = draft.getShippingNote();
    this.summaryLines = draft.getSummaryLines();
    this.links = draft.getLinks();
    this.imageIds = draft.getImageIds();
  }
}

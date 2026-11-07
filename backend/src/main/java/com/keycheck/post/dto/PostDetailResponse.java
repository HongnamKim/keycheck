package com.keycheck.post.dto;

import com.keycheck.image.service.ThumbUrls;
import com.keycheck.post.domain.Board;
import com.keycheck.post.domain.BodyLink;
import com.keycheck.post.domain.Category;
import com.keycheck.post.domain.Post;
import com.keycheck.post.domain.PostImage;
import com.keycheck.post.domain.SpecItem;
import com.keycheck.post.domain.Subcategory;
import com.keycheck.post.domain.SummaryStatus;
import com.keycheck.post.domain.VendorItem;
import java.time.Instant;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public class PostDetailResponse {
  /** 한국 벤더의 지역 코드 */
  private static final String KOREA = "KR";

  private final Long id;
  private final String title;

  /** 상품·프로젝트 이름(예: "SWG Nightshift"). 요약기가 뽑는다. 모르거나 아직 뽑지 않았으면 null. */
  private final String productName;

  private final String author;

  /** 글을 읽어 온 geekhack 게시판 (IC, GB) */
  private final Board board;

  private final Category category;

  /** 세부 분류 코드(KB_65, KC_GMK 등). 기타·미분류 글이나 아직 정하지 않았으면 null. */
  private final Subcategory subcategory;

  private final String representativeImageUrl;

  /** 대표 이미지 축소본(가로 최대 640px) 공개 주소. 대표 이미지나 축소본이 없으면 null. */
  private final String representativeThumbUrl;

  /** 대표 이미지 큰 축소본(가로 최대 1280px) 공개 주소. 상세 맨 위에 쓴다. 대표 이미지나 큰 축소본이 없으면 null. */
  private final String representativeLargeUrl;

  /** 규칙에서 빠지지 않은 후보 이미지 전체, 본문 순서, 주소 중복 제외(첫 행) (대표 이미지 포함) */
  private final List<PostImageResponse> images;

  private final SummaryStatus summaryStatus;

  /** summaryStatus가 DONE이 아니면 빈 목록 */
  private final List<String> summaryLines;

  private final List<SpecItem> specs;

  /** 요약기가 뽑은 벤더. 한국 벤더(region "KR")를 앞에 두고, 한국 벤더끼리와 나머지끼리는 본문 순서. 없거나 아직 뽑지 않았으면 빈 목록. */
  private final List<VendorResponse> vendors;

  private final String originalUrl;
  private final int views;
  private final int replies;
  private final Instant statsCheckedAt;
  private final Instant postedAt;

  /** postedAt이 없는 글(틀 글)의 글 번호로 추정한 작성일. postedAt이 있거나 추정할 수 없으면 null. */
  private final Instant estimatedPostedAt;

  /** 게시판 목록에서 읽은 마지막 게시물(댓글) 시각. 아직 읽지 못했으면 null. */
  private final Instant lastPostAt;

  /** false면 틀 글: geekhack에서 글을 읽지 못해 본문·이미지·요약이 없다. */
  private final boolean detailLoaded;

  /** representative는 대표 이미지. 없으면 null. */
  public static PostDetailResponse from(
      Post post, PostImage representative, Instant estimatedPostedAt, ThumbUrls urls) {
    boolean done = post.getSummaryStatus() == SummaryStatus.DONE;
    Set<String> seen = new HashSet<>();
    return new PostDetailResponse(
        post.getId(),
        post.getTitle(),
        post.getProductName(),
        post.getAuthor(),
        post.getBoard(),
        post.getCategory(),
        post.getSubcategory(),
        representative != null ? representative.getUrl() : null,
        representative != null ? urls.url(representative.getThumbKey()) : null,
        representative != null ? urls.url(representative.getThumbLargeKey()) : null,
        post.getImages().stream()
            .filter(image -> image.getExcludedReason() == null)
            .filter(image -> seen.add(image.getUrl()))
            .map(image -> new PostImageResponse(image.getUrl(), urls.url(image.getThumbKey())))
            .toList(),
        post.getSummaryStatus(),
        done && post.getSummaryLines() != null ? post.getSummaryLines() : List.of(),
        post.getSpecs() != null ? post.getSpecs() : List.of(),
        koreanFirst(post.getVendors(), post.getBodyLinks()),
        post.getOriginalUrl(),
        post.getViews(),
        post.getReplies(),
        post.getStatsCheckedAt(),
        post.getPostedAt(),
        estimatedPostedAt,
        post.getLastPostAt(),
        post.isDetailLoaded());
  }

  /**
   * 한국 벤더를 앞으로. 정렬이 안정적이라 각 무리 안은 저장 순서 그대로다. 한국 벤더는 원문 지역 표기와 상관없이 지역을 KR로 내보내고,
   * 판매 링크가 없는 알려진 한국 벤더는 본문 링크 중 그 벤더 사이트의 첫 주소를 쓴다. null이면 빈 목록.
   */
  private static List<VendorResponse> koreanFirst(List<VendorItem> vendors, List<BodyLink> links) {
    if (vendors == null) {
      return List.of();
    }
    return vendors.stream()
        .sorted(Comparator.comparing(VendorItem::isKorean).reversed())
        .map(
            vendor ->
                vendor.isKorean()
                    ? new VendorResponse(
                        vendor.getName(),
                        KOREA,
                        vendor.getUrl() != null ? vendor.getUrl() : siteLink(vendor, links))
                    : new VendorResponse(vendor.getName(), vendor.getRegion(), vendor.getUrl()))
        .toList();
  }

  /** 알려진 한국 벤더면 본문 링크 중 그 벤더 사이트의 첫 주소. 아니거나 없으면 null */
  private static String siteLink(VendorItem vendor, List<BodyLink> links) {
    if (links == null) {
      return null;
    }
    return vendor
        .knownKorean()
        .flatMap(known -> links.stream().map(BodyLink::getUrl).filter(known::owns).findFirst())
        .orElse(null);
  }
}

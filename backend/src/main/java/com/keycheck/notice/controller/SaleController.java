package com.keycheck.notice.controller;

import com.keycheck.notice.dto.SaleDetailResponse;
import com.keycheck.notice.dto.SaleHighlightResponse;
import com.keycheck.notice.dto.SaleListResponse;
import com.keycheck.notice.dto.SaleScheduleResponse;
import com.keycheck.notice.dto.SaleSitemapItemResponse;
import com.keycheck.notice.service.SaleHighlightService;
import com.keycheck.notice.service.SaleQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "국내 판매", description = "판매처 디스코드 공지에서 뽑은 국내 판매 소식 조회")
@RestController
@RequestMapping("/api/sales")
@RequiredArgsConstructor
public class SaleController {
  private static final String TIMES_DESCRIPTION =
      " 시작·마감 날짜는 YYYY-MM-DD, 시각은 HH:mm(모두 한국 시각)이고 모르면 null이다. 날짜만 알면 시각은 null이다."
          + " postedAt: 공지 시각(ISO-8601, UTC). thumbUrl(640)·largeUrl(1280)·imageUrl(2048): 대표 사진, 없거나 아직 보관 전이면 null.";

  private final SaleQueryService saleQueryService;
  private final SaleHighlightService saleHighlightService;
  private final Clock clock;

  @Operation(
      summary = "국내 판매 목록",
      description =
          "숨김·분류 OTHER·요약 전 공지의 건을 뺀 판매 건을 공지 시각 최근 순(같으면 공지 안 순서)으로 20건씩 이어서 돌려준다."
              + " hasMore가 true면 nextCursor를 cursor로 넘겨 다음을 요청한다."
              + " 끝난 판매도 남긴다."
              + TIMES_DESCRIPTION)
  @ApiResponse(responseCode = "200", description = "성공")
  @ApiResponse(responseCode = "400", description = "허용되지 않는 vendor·category·cursor 값")
  @GetMapping
  public SaleListResponse getSales(
      @Parameter(
              description = "판매처 필터",
              schema = @Schema(allowableValues = {"all", "swagkeys", "geonworks"}))
          @RequestParam(defaultValue = "all")
          String vendor,
      @Parameter(
              description = "분류 필터",
              schema = @Schema(allowableValues = {"all", "keyboard", "keycap", "switch", "parts"}))
          @RequestParam(defaultValue = "all")
          String category,
      @Parameter(description = "이전 응답의 nextCursor. 첫 요청은 생략") @RequestParam(required = false)
          String cursor) {
    return saleQueryService.getSales(vendor, category, cursor);
  }

  @Operation(
      summary = "국내 판매 일정",
      description =
          "지금(한국 시각) 기준으로 끝나지 않은 판매 건을 쪽 나눔 없이 최대 300건, 공지 시각 최근 순(같으면 공지 안 순서)으로 돌려준다."
              + " 마감이 있으면 마감이 지나지 않은 건이다. 마감이 없으면 시작이 있을 때 시작이 아직 안 왔거나 시작 뒤 7일 안인 건(예고 포함),"
              + " 시작이 없을 때 예고가 아니면 공지 뒤 7일 안, 예고면 공지 뒤 30일 안인 건이다. 숨김·분류 OTHER·요약 전 공지의 건은 뺀다."
              + TIMES_DESCRIPTION)
  @ApiResponse(responseCode = "200", description = "성공")
  @ApiResponse(responseCode = "400", description = "허용되지 않는 vendor·category 값")
  @GetMapping("/schedule")
  public SaleScheduleResponse getSchedule(
      @Parameter(
              description = "판매처 필터",
              schema = @Schema(allowableValues = {"all", "swagkeys", "geonworks"}))
          @RequestParam(defaultValue = "all")
          String vendor,
      @Parameter(
              description = "분류 필터",
              schema = @Schema(allowableValues = {"all", "keyboard", "keycap", "switch", "parts"}))
          @RequestParam(defaultValue = "all")
          String category) {
    return saleQueryService.getSchedule(vendor, category);
  }

  @Operation(
      summary = "국내 지난 판매",
      description =
          "국내 판매 일정에 들지 않는(지금 기준 끝난) 판매 건 전부를 지난 시각 최근 순(같으면 id 큰 순)으로 30건씩 이어서 돌려준다."
              + " 지난 시각은 마감(날짜만이면 23:59:59), 없으면 시작(날짜만이면 00:00), 없으면 공지 시각이다(한국 시각)."
              + " hasMore가 true면 nextCursor를 cursor로 넘겨 다음을 요청한다."
              + " 숨김·분류 OTHER·요약 전 공지의 건은 뺀다."
              + TIMES_DESCRIPTION)
  @ApiResponse(responseCode = "200", description = "성공")
  @ApiResponse(responseCode = "400", description = "허용되지 않는 vendor·category·cursor 값")
  @GetMapping("/past")
  public SaleListResponse getPast(
      @Parameter(
              description = "판매처 필터",
              schema = @Schema(allowableValues = {"all", "swagkeys", "geonworks"}))
          @RequestParam(defaultValue = "all")
          String vendor,
      @Parameter(
              description = "분류 필터",
              schema = @Schema(allowableValues = {"all", "keyboard", "keycap", "switch", "parts"}))
          @RequestParam(defaultValue = "all")
          String category,
      @Parameter(description = "이전 응답의 nextCursor. 첫 요청은 생략") @RequestParam(required = false)
          String cursor) {
    return saleQueryService.getPast(vendor, category, cursor);
  }

  @Operation(
      summary = "국내 판매 하이라이트",
      description =
          "메인에 올릴 국내 판매 건을 최대 2개 돌려준다. 대표 사진이 보관된 건만 고르고,"
              + " reason 순서로 채운다. UPCOMING: 7일 안에 시작. CLOSING: 판매 중이고 7일 안에 마감."
              + " RECENT: 공지가 14일 안. 같은 건은 한 번만 나온다. 하나도 없으면 picks가 빈 배열이다."
              + " newSaleCount: 메인 날짜 줄 숫자. 공지 시각(postedAt)이 지금에서 7일 전 이후인 판매 건 중"
              + " 숨김·분류 OTHER를 뺀 수(사진은 묻지 않는다)."
              + TIMES_DESCRIPTION)
  @ApiResponse(responseCode = "200", description = "성공")
  @GetMapping("/highlights")
  public SaleHighlightResponse getHighlights() {
    Instant now = clock.instant();
    return new SaleHighlightResponse(saleHighlightService.pick(now), saleHighlightService.countNew(now));
  }

  @Operation(
      summary = "국내 판매 사이트맵 목록",
      description =
          "검색 엔진 사이트맵에 넣을 판매 건 ID와 lastModified(공지 요약 시각, ISO-8601 UTC)를 목록과 같은 순서로 모두 돌려준다.")
  @ApiResponse(responseCode = "200", description = "성공")
  @GetMapping("/sitemap")
  public List<SaleSitemapItemResponse> getSitemap() {
    return saleQueryService.getSitemap();
  }

  @Operation(
      summary = "국내 판매 상세",
      description =
          "카드 필드에 요약 줄 전부(summaryLines), 배송 안내(shippingNote), 구매 링크(links), 사진들(images)을 더해 돌려준다."
              + " images는 보관이 끝난 사진만 공지에 나온 순서로, url(2048)·largeUrl(1280)·thumbUrl(640)이다."
              + TIMES_DESCRIPTION)
  @ApiResponse(responseCode = "200", description = "성공")
  @ApiResponse(responseCode = "404", description = "없거나 숨김 처리되었거나 분류가 OTHER이거나 요약 전인 판매 건")
  @GetMapping("/{id}")
  public SaleDetailResponse getSale(
      @Parameter(description = "판매 건 ID (키체크 내부 ID)") @PathVariable Long id) {
    return saleQueryService.getSale(id);
  }
}

package com.keycheck.post.controller;

import com.keycheck.post.dto.BoardFilter;
import com.keycheck.post.dto.CategoryFilter;
import com.keycheck.post.dto.HighlightResponse;
import com.keycheck.post.dto.PostDetailResponse;
import com.keycheck.post.dto.PostFeedItemResponse;
import com.keycheck.post.dto.PostListResponse;
import com.keycheck.post.dto.PostNeighborsResponse;
import com.keycheck.post.dto.PostSitemapItemResponse;
import com.keycheck.post.dto.PostSort;
import com.keycheck.post.dto.PostStatsResponse;
import com.keycheck.post.service.PostHighlightService;
import com.keycheck.post.service.PostQueryService;
import com.keycheck.post.service.PostStatsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "글", description = "수집한 geekhack IC·GB 글 조회")
@RestController
@RequestMapping("/api/posts")
@RequiredArgsConstructor
public class PostController {
  private static final String DATES_DESCRIPTION =
      " 시각은 모두 ISO-8601(UTC). postedAt: 글 페이지에서 읽은 작성일, 틀 글이면 null."
          + " estimatedPostedAt: postedAt이 null인 글만, 작성일을 아는 앞뒤 글의 글 번호로 추정한 작성일"
          + "(lastPostAt·지금보다 늦지 않음). postedAt이 있거나 추정할 수 없으면 null."
          + " lastPostAt: geekhack 게시판 목록에서 읽은 마지막 게시물(댓글) 시각, 아직 읽지 못했으면 null.";

  private final PostQueryService postQueryService;
  private final PostStatsService postStatsService;
  private final PostHighlightService postHighlightService;

  @Operation(
      summary = "글 목록",
      description =
          "숨김 글과 수집 기간(2020년) 이전 글을 뺀 목록을 최대 20건씩 이어서 돌려준다(무한 스크롤). hasMore가 true면 nextCursor를 cursor로 넘겨"
              + " 다음을 요청한다(nextCursor가 null이면 cursor 없이). items가 비었는데 hasMore가 true면 geekhack에서"
              + " 더 가져오지 못한 순간이므로 잠시 뒤 같은 cursor로 다시 요청한다."
              + " board: geekhack 게시판. ic(Interest Checks), gb(Group Buys and Preorders), all(둘 다, 기본)."
              + " 카드·상세의 board는 글을 읽어 온 게시판 코드(IC, GB)다."
              + " recent: 확보한 범위의 글을 글 번호(작성순) 내림차순으로 준다. 20건이 안 되면 geekhack 작성순 목록의"
              + " 다음 쪽을 한 번 읽어 본문·이미지 없는 틀 글(detailLoaded=false)로 저장한 뒤 다시 조회한다."
              + " all이면 두 게시판 모두 빠짐없이 받아 둔 범위만 주고, 모자라면 덜 받은 게시판의 다음 쪽을 한 번 읽는다."
              + " 그래서 두 게시판을 모두 조금이라도 받기 전에는 items가 비고 hasMore=true일 수 있다."
              + " all은 두 게시판 모두 끝까지 받았을 때만 모자라면 hasMore=false다."
              + " views: 저장된 글 전체(board가 있으면 그 게시판 글)를 조회수순으로 주고 geekhack을 호출하지 않는다."
              + " 분류·세부 분류 필터는 recent여도 과거 글을 확보하지 않고, 지금 분류된 글이 다하면 hasMore=false다."
              + " 이때 all은 게시판별 필터 목록을 합친 것이다(게시판마다 그 게시판에서 확보한 범위로 거른다)."
              + " subcategory: 세부 분류 코드. KEYBOARD는 배열(KB_100, KB_96, KB_80, KB_75, KB_65, KB_60,"
              + " KB_40, KB_ERGO, KB_ETC), KEYCAP은 제조사(KC_GMK, KC_SP, KC_MW, KC_KKB, KC_PBTFANS, KC_ETC)."
              + " 기타·미분류 글이나 아직 정하지 않았으면 null."
              + " 공동구매 글은 요약기가 뽑은 기간(gbStartDate·gbEndDate, YYYY-MM-DD, 모르면 null)을 준다."
              + " productName: 요약기가 뽑은 상품·프로젝트 이름(예: SWG Nightshift). 모르거나 아직 뽑지 않았으면 null."
              + " hasKoreanVendor: 요약기가 뽑은 벤더 중 한국 벤더(지역 KR, 또는 이름·판매 사이트로 알아본 알려진 한국 벤더)가 있으면"
              + " true, 없거나 아직 뽑지 않았으면 false."
              + DATES_DESCRIPTION)
  @ApiResponse(responseCode = "200", description = "성공")
  @ApiResponse(
      responseCode = "400",
      description =
          "허용되지 않는 board·category·sub·sort·cursor 값, category가 all이거나 분류와 맞지 않는 sub")
  @GetMapping
  public PostListResponse getPosts(
      @Parameter(
              description = "분류 필터",
              schema = @Schema(allowableValues = {"all", "keyboard", "keycap"}))
          @RequestParam(defaultValue = "all")
          String category,
      @Parameter(
              description =
                  "세부 분류 필터. category가 keyboard면 배열(100, 96, 80, 75, 65, 60, 40, ergo, etc),"
                      + " keycap이면 제조사(gmk, sp, mw, kkb, pbtfans, etc). category가 all이면 쓸 수 없다."
                      + " 생략하면 거르지 않는다",
              schema =
                  @Schema(
                      allowableValues = {
                        "100", "96", "80", "75", "65", "60", "40", "ergo", "etc", "gmk", "sp", "mw",
                        "kkb", "pbtfans"
                      }))
          @RequestParam(required = false)
          String sub,
      @Parameter(
              description = "정렬 (recent: 최근 작성순, views: 조회수순)",
              schema = @Schema(allowableValues = {"recent", "views"}))
          @RequestParam(defaultValue = "recent")
          String sort,
      @Parameter(description = "이전 응답의 nextCursor. 첫 요청은 생략") @RequestParam(required = false)
          String cursor,
      @Parameter(
              description =
                  "게시판 필터 (ic: Interest Checks, gb: Group Buys and Preorders, all: 둘 다)",
              schema = @Schema(allowableValues = {"all", "ic", "gb"}))
          @RequestParam(defaultValue = "all")
          String board) {
    CategoryFilter filter = CategoryFilter.from(category);
    return postQueryService.getPosts(
        BoardFilter.from(board), filter, filter.subcategory(sub), PostSort.from(sort), cursor);
  }

  @Operation(
      summary = "메인 하이라이트",
      description =
          "메인 하이라이트 칸과 날짜 줄 숫자를 한 번에 돌려준다. 요청마다 계산하고 geekhack을 호출하지 않는다."
              + " 후보는 숨기지 않았고 수집 기간(2020년) 이후이며 본문을 읽었고 대표 이미지가 있는 글이다."
              + " 최근 N일은 작성일(postedAt)이 지금에서 N일 전 이후인 글이다."
              + " slots는 이 순서로 고르고, 앞 칸에 들어간 글은 뒤 칸에서 건너뛴다."
              + " FEATURE: 최근 7일 공동구매 조회수 1위. TALL: 최근 14일 IC 조회수 1위."
              + " DEADLINE: today부터 30일 뒤까지(양끝 포함) 마감하는 공동구매 중 마감이 가장 가까운 글(deadline=true)."
              + " 없으면 최근 14일 공동구매 조회수 1위를 사진 칸으로 준다(deadline=false)."
              + " IC_REPLIES: 최근 14일 IC 댓글 1위. IC_VIEWS: 최근 14일 IC 조회수 1위."
              + " GB_VIEWS: 최근 30일 공동구매 조회수 1위."
              + " 창 안에 후보가 없으면 30일 → 90일 → 전체로 넓히고, 그래도 없으면 그 칸을 slots에서 뺀다."
              + " windowDays: 고른 창(일). 전체로 넓혔거나 마감 띠면 null."
              + " rank: 그 창의 정렬 순위(1부터, 앞 칸에 들어간 글도 센다). 마감 띠면 null."
              + " today: 한국 날짜(YYYY-MM-DD). newIcCount·newGbCount: 최근 7일에 작성된 IC·공동구매 글 수."
              + " endingSoonGbCount: today부터 14일 뒤까지(양끝 포함) 마감하는 공동구매 글 수."
              + " 세 숫자 모두 숨김 글과 수집 기간 이전 글을 빼고 센다(본문·대표 이미지는 묻지 않는다)."
              + " 카드 형식은 목록과 같다."
              + DATES_DESCRIPTION)
  @ApiResponse(responseCode = "200", description = "성공")
  @GetMapping("/highlights")
  public HighlightResponse getHighlights() {
    return postHighlightService.getHighlights();
  }

  @Operation(
      summary = "사이트맵 글 목록",
      description =
          "검색 엔진 사이트맵에 넣을 글 ID와 lastModified(요약 완료 시각)를 글 번호 내림차순으로 모두 돌려준다. geekhack을 호출하지 않는다."
              + " 숨기지 않았고 수집 기간(2020년) 이후이며 본문을 읽었고 요약이 끝났고(summaryStatus=DONE)"
              + " 요약 줄이 하나 이상인 글만 넣는다."
              + " lastModified: 요약을 마지막으로 저장한 시각(ISO-8601, UTC, 다시 요약하면 바뀐다)."
              + " 이 값을 기록하기 전에 요약한 글이면 null.")
  @ApiResponse(responseCode = "200", description = "성공")
  @GetMapping("/sitemap")
  public List<PostSitemapItemResponse> getSitemap() {
    return postQueryService.getSitemap();
  }

  @Operation(
      summary = "RSS 피드 글 목록",
      description =
          "RSS 피드에 넣을 글을 최대 "
              + PostQueryService.FEED_SIZE
              + "건 돌려준다. geekhack을 호출하지 않는다."
              + " 사이트맵과 같은 글(숨기지 않았고 수집 기간(2020년) 이후이며 본문을 읽었고 요약이 끝났고 요약 줄이 하나 이상인 글)을"
              + " 요약을 마지막으로 저장한 시각(summarizedAt) 내림차순으로 주고, 그 시각을 모르는 글은 뒤에 둔다."
              + " 같으면 글 번호 내림차순이다."
              + " board: 글을 읽어 온 게시판 코드(IC, GB). productName: 요약기가 뽑은 상품·프로젝트 이름, 모르면 null."
              + " summaryLines: 요약 줄 전체. 시각은 모두 ISO-8601(UTC)."
              + " postedAt: 글 페이지에서 읽은 작성일, 모르면 null. summarizedAt: 요약을 마지막으로 저장한 시각,"
              + " 이 값을 기록하기 전에 요약한 글이면 null.")
  @ApiResponse(responseCode = "200", description = "성공")
  @GetMapping("/feed")
  public List<PostFeedItemResponse> getFeed() {
    return postQueryService.getFeed();
  }

  @Operation(
      summary = "글 상세",
      description =
          "요약, 스펙표, 대표 이미지, 원문 링크를 돌려준다. 틀 글이면 geekhack 글 첫 페이지를 읽어 본문·이미지를 채운 뒤"
              + " 돌려준다. 읽지 못하면(요청 간격 대기 초과, 요청·해석 실패) 틀 그대로 detailLoaded=false로 돌려준다."
              + " fill=false면 채우지 않고 틀 그대로 돌려준다."
              + " 요약은 백그라운드에서 만들므로 막 채운 글은 summaryStatus=PENDING이다."
              + " summarizingForMs: 지금 요약 중이면 서버가 요약을 시작한 뒤 흐른 밀리초, 요약 차례를 기다리거나 요약 중이 아니면 null."
              + " vendors: 요약기가 뽑은 벤더(name, region: 대문자 지역 코드 또는 null, url: 첫 글 본문에 있던 판매 페이지 주소 또는"
              + " null). 한국 벤더(KR)를 앞에 두고 각 무리 안은 본문 순서, 없거나 아직 뽑지 않았으면 빈 배열."
              + " 알려진 한국 벤더(GeonWorks·Swagkeys·KLC Playground)는 원문 지역 표기와 상관없이 region KR이고, 판매 링크가 없으면"
              + " 본문 링크 중 그 벤더 사이트 주소를 쓴다."
              + DATES_DESCRIPTION)
  @ApiResponse(responseCode = "200", description = "성공")
  @ApiResponse(responseCode = "404", description = "없거나 숨김 처리되었거나 수집 기간(2020년) 이전의 글")
  @GetMapping("/{id}")
  public PostDetailResponse getPost(
      @Parameter(description = "글 ID (키체크 내부 ID)") @PathVariable Long id,
      @Parameter(
              description =
                  "false면 틀 글을 채우지 않고(geekhack 요청 없음) 미리 읽은 글의 요약 순서도 앞당기지 않는다."
                      + " 검색·미리보기 봇의 방문과 요약을 기다리는 화면의 다시 읽기에 쓴다")
          @RequestParam(defaultValue = "true")
          boolean fill) {
    return postQueryService.getPost(id, fill);
  }

  @Operation(
      summary = "글 앞뒤 목록",
      description =
          "상세 아래에 보여 줄 목록. 같은 board·category·sub·sort의 글 목록(GET /api/posts와 같은 필터·정렬·커버리지 규칙)에서"
              + " 이 글 바로 위 최대 size개(before)와 바로 아래 최대 size개(after)를 준다. 둘 다 목록 순서대로라"
              + " before의 마지막 원소가 바로 위 글, after의 첫 원소가 바로 아래 글이다. 이 글은 들어 있지 않다."
              + " 이 글이 그 목록 조건(게시판·분류·커버리지)에 맞지 않아도 그 정렬 기준의 위치로 계산한다."
              + " 과거 글을 확보하지 않으므로(geekhack 요청 없음) 확보한 범위의 끝에서는 after가 size보다 적을 수 있다."
              + " 카드 형식은 목록과 같다."
              + DATES_DESCRIPTION)
  @ApiResponse(responseCode = "200", description = "성공")
  @ApiResponse(
      responseCode = "400",
      description =
          "허용되지 않는 board·category·sub·sort 값, category가 all이거나 분류와 맞지 않는 sub, 1~10 밖의 size")
  @ApiResponse(responseCode = "404", description = "없거나 숨김 처리되었거나 수집 기간(2020년) 이전의 글")
  @GetMapping("/{id}/neighbors")
  public PostNeighborsResponse getNeighbors(
      @Parameter(description = "글 ID (키체크 내부 ID)") @PathVariable Long id,
      @Parameter(
              description = "게시판 필터. 목록과 같다",
              schema = @Schema(allowableValues = {"all", "ic", "gb"}))
          @RequestParam(defaultValue = "all")
          String board,
      @Parameter(
              description = "분류 필터. 목록과 같다",
              schema = @Schema(allowableValues = {"all", "keyboard", "keycap"}))
          @RequestParam(defaultValue = "all")
          String category,
      @Parameter(description = "세부 분류 필터. 목록과 같다(category를 정해야 쓸 수 있다)")
          @RequestParam(required = false)
          String sub,
      @Parameter(
              description = "정렬. 목록과 같다",
              schema = @Schema(allowableValues = {"recent", "views"}))
          @RequestParam(defaultValue = "recent")
          String sort,
      @Parameter(
              description = "앞뒤 각각 최대 개수",
              schema = @Schema(minimum = "1", maximum = "10", defaultValue = "5"))
          @RequestParam(defaultValue = "5")
          int size) {
    CategoryFilter filter = CategoryFilter.from(category);
    return postQueryService.getNeighbors(
        id, BoardFilter.from(board), filter, filter.subcategory(sub), PostSort.from(sort), size);
  }

  @Operation(
      summary = "글 조회수·댓글 수",
      description = "확인한 지 1시간이 지났으면 geekhack에서 다시 읽는다. 읽지 못하면 저장값을 돌려준다.")
  @ApiResponse(responseCode = "200", description = "성공")
  @ApiResponse(responseCode = "404", description = "없거나 숨김 처리되었거나 수집 기간(2020년) 이전의 글")
  @GetMapping("/{id}/stats")
  public PostStatsResponse getStats(
      @Parameter(description = "글 ID (키체크 내부 ID)") @PathVariable Long id) {
    return postStatsService.getStats(id);
  }
}

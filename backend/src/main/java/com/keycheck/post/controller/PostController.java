package com.keycheck.post.controller;

import com.keycheck.post.dto.BoardFilter;
import com.keycheck.post.dto.CategoryFilter;
import com.keycheck.post.dto.PostDetailResponse;
import com.keycheck.post.dto.PostListResponse;
import com.keycheck.post.dto.PostSort;
import com.keycheck.post.dto.PostStatsResponse;
import com.keycheck.post.service.PostQueryService;
import com.keycheck.post.service.PostStatsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
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

  @Operation(
      summary = "글 목록",
      description =
          "숨김 글을 뺀 목록을 최대 20건씩 이어서 돌려준다(무한 스크롤). hasMore가 true면 nextCursor를 cursor로 넘겨"
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
      summary = "글 상세",
      description =
          "요약, 스펙표, 대표 이미지, 원문 링크를 돌려준다. 틀 글이면 geekhack 글 첫 페이지를 읽어 본문·이미지를 채운 뒤"
              + " 돌려준다. 읽지 못하면(요청 간격 대기 초과, 요청·해석 실패) 틀 그대로 detailLoaded=false로 돌려준다."
              + " 요약은 백그라운드에서 만들므로 막 채운 글은 summaryStatus=PENDING이다."
              + DATES_DESCRIPTION)
  @ApiResponse(responseCode = "200", description = "성공")
  @ApiResponse(responseCode = "404", description = "없거나 숨김 처리된 글")
  @GetMapping("/{id}")
  public PostDetailResponse getPost(
      @Parameter(description = "글 ID (키체크 내부 ID)") @PathVariable Long id) {
    return postQueryService.getPost(id);
  }

  @Operation(
      summary = "글 조회수·댓글 수",
      description = "확인한 지 1시간이 지났으면 geekhack에서 다시 읽는다. 읽지 못하면 저장값을 돌려준다.")
  @ApiResponse(responseCode = "200", description = "성공")
  @ApiResponse(responseCode = "404", description = "없거나 숨김 처리된 글")
  @GetMapping("/{id}/stats")
  public PostStatsResponse getStats(
      @Parameter(description = "글 ID (키체크 내부 ID)") @PathVariable Long id) {
    return postStatsService.getStats(id);
  }
}

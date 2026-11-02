package com.keycheck.post;

import com.keycheck.post.dto.PostDetailResponse;
import com.keycheck.post.dto.PostPageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@Tag(name = "글", description = "수집한 IC 글 조회")
@RestController
@RequestMapping("/api/posts")
@RequiredArgsConstructor
public class PostController {
  private final PostQueryService postQueryService;

  @Operation(summary = "글 목록", description = "숨김 글을 뺀 목록을 20건씩 돌려준다.")
  @ApiResponse(responseCode = "200", description = "성공")
  @ApiResponse(responseCode = "400", description = "허용되지 않는 category·sort 값이거나 page가 음수")
  @GetMapping
  public PostPageResponse getPosts(
      @Parameter(
              description = "분류 필터",
              schema = @Schema(allowableValues = {"all", "keyboard", "keycap"}))
          @RequestParam(defaultValue = "all")
          String category,
      @Parameter(
              description = "정렬 (recent: 최근 작성순, views: 조회수순)",
              schema = @Schema(allowableValues = {"recent", "views"}))
          @RequestParam(defaultValue = "recent")
          String sort,
      @Parameter(description = "페이지 번호 (0부터)") @RequestParam(defaultValue = "0") int page) {
    if (page < 0) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "page는 0 이상");
    }
    return postQueryService.getPosts(CategoryFilter.from(category), PostSort.from(sort), page);
  }

  @Operation(summary = "글 상세", description = "요약, 스펙표, 대표 이미지, 원문 링크를 돌려준다. geekhack을 호출하지 않는다.")
  @ApiResponse(responseCode = "200", description = "성공")
  @ApiResponse(responseCode = "404", description = "없거나 숨김 처리된 글")
  @GetMapping("/{id}")
  public PostDetailResponse getPost(
      @Parameter(description = "글 ID (키체크 내부 ID)") @PathVariable Long id) {
    return postQueryService.getPost(id);
  }
}

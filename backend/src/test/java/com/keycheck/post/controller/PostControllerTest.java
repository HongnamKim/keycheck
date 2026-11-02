package com.keycheck.post.controller;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.keycheck.TestcontainersConfiguration;
import com.keycheck.image.domain.ImageSource;
import com.keycheck.post.domain.Category;
import com.keycheck.post.domain.Post;
import com.keycheck.post.domain.SpecItem;
import com.keycheck.post.domain.SummaryStatus;
import com.keycheck.post.repository.PostRepository;
import java.time.Instant;
import java.util.List;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class PostControllerTest {
  @Autowired private MockMvc mockMvc;
  @Autowired private PostRepository postRepository;

  @BeforeEach
  void setUp() {
    postRepository.deleteAll();
  }

  /** 기본값으로 글을 만들고 customize로 바꾼 뒤 저장한다. 이미지가 있으면 첫 이미지를 대표로 정한다. */
  private Post save(long topicId, Consumer<Post> customize) {
    Post post =
        Post.create(
            topicId,
            "글 " + topicId,
            "maker",
            Category.KEYBOARD,
            Instant.parse("2026-10-01T00:00:00Z"),
            100,
            5,
            Instant.parse("2026-11-01T00:00:00Z"),
            "https://geekhack.org/index.php?topic=" + topicId + ".0",
            "비공개 본문");
    customize.accept(post);
    postRepository.saveAndFlush(post);
    if (!post.getImages().isEmpty()) {
      post.setRepresentativeImageId(post.getImages().get(0).getId());
      postRepository.saveAndFlush(post);
    }
    return post;
  }

  private void summarized(Post post) {
    post.setSummaryStatus(SummaryStatus.DONE);
    post.setSummaryLines(List.of("첫 줄", "둘째 줄"));
    post.setSpecs(List.of(new SpecItem("레이아웃", "65%")));
  }

  private void withImage(Post post) {
    post.addImage("https://i.imgur.com/" + post.getTopicId() + ".jpg", ImageSource.EXTERNAL, 0, null);
  }

  @Test
  @DisplayName("기본 목록은 숨김을 빼고 최근 작성순, 작성일 없는 글은 마지막이다")
  void defaultListIsRecentWithoutHidden() throws Exception {
    save(1, post -> post.setPostedAt(Instant.parse("2026-10-01T00:00:00Z")));
    save(2, post -> post.setPostedAt(Instant.parse("2026-10-03T00:00:00Z")));
    save(3, post -> post.setPostedAt(null));
    save(
        4,
        post -> {
          post.setPostedAt(Instant.parse("2026-10-05T00:00:00Z"));
          post.setHidden(true);
        });

    mockMvc
        .perform(get("/api/posts"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items[*].title", contains("글 2", "글 1", "글 3")))
        .andExpect(jsonPath("$.page").value(0))
        .andExpect(jsonPath("$.totalPages").value(1));
  }

  @Test
  @DisplayName("조회수순으로 정렬한다")
  void sortsByViews() throws Exception {
    save(1, post -> post.setViews(10));
    save(2, post -> post.setViews(300));
    save(3, post -> post.setViews(50));

    mockMvc
        .perform(get("/api/posts?sort=views"))
        .andExpect(jsonPath("$.items[*].title", contains("글 2", "글 3", "글 1")));
  }

  @Test
  @DisplayName("분류로 거른다")
  void filtersByCategory() throws Exception {
    save(1, post -> post.setCategory(Category.KEYBOARD));
    save(2, post -> post.setCategory(Category.KEYCAP));
    save(3, post -> post.setCategory(Category.OTHER));

    mockMvc
        .perform(get("/api/posts?category=keycap"))
        .andExpect(jsonPath("$.items[*].title", contains("글 2")));
    mockMvc.perform(get("/api/posts?category=all")).andExpect(jsonPath("$.items", hasSize(3)));
  }

  @Test
  @DisplayName("카드에 대표 이미지와 요약 첫 줄을 담는다, 요약 미완료면 첫 줄은 null")
  void cardHasImageAndFirstLine() throws Exception {
    save(
        1,
        post -> {
          summarized(post);
          withImage(post);
          post.setPostedAt(Instant.parse("2026-10-02T00:00:00Z"));
        });
    save(2, post -> post.setSummaryStatus(SummaryStatus.PENDING));

    mockMvc
        .perform(get("/api/posts"))
        .andExpect(jsonPath("$.items[0].representativeImageUrl").value("https://i.imgur.com/1.jpg"))
        .andExpect(jsonPath("$.items[0].summaryFirstLine").value("첫 줄"))
        .andExpect(jsonPath("$.items[0].statsCheckedAt").value("2026-11-01T00:00:00Z"))
        .andExpect(jsonPath("$.items[1].representativeImageUrl").value(nullValue()))
        .andExpect(jsonPath("$.items[1].summaryFirstLine").value(nullValue()))
        .andExpect(jsonPath("$.items[1].summaryStatus").value("PENDING"));
  }

  @Test
  @DisplayName("한 페이지는 20건이다")
  void pageSizeIsTwenty() throws Exception {
    for (long topicId = 1; topicId <= 21; topicId++) {
      save(topicId, post -> {});
    }

    mockMvc
        .perform(get("/api/posts?page=1"))
        .andExpect(jsonPath("$.items", hasSize(1)))
        .andExpect(jsonPath("$.page").value(1))
        .andExpect(jsonPath("$.totalPages").value(2));
  }

  @Test
  @DisplayName("잘못된 분류·정렬·페이지는 400")
  void invalidParamsAre400() throws Exception {
    mockMvc.perform(get("/api/posts?category=switch")).andExpect(status().isBadRequest());
    mockMvc.perform(get("/api/posts?sort=oldest")).andExpect(status().isBadRequest());
    mockMvc.perform(get("/api/posts?page=-1")).andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("상세는 요약·스펙·원문 링크를 주고 원문 본문은 주지 않는다")
  void detailHasSummaryButNotBody() throws Exception {
    Post post =
        save(
            1,
            p -> {
              summarized(p);
              withImage(p);
            });

    mockMvc
        .perform(get("/api/posts/" + post.getId()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.summaryLines", contains("첫 줄", "둘째 줄")))
        .andExpect(jsonPath("$.specs[0].label").value("레이아웃"))
        .andExpect(jsonPath("$.originalUrl").value("https://geekhack.org/index.php?topic=1.0"))
        .andExpect(jsonPath("$.representativeImageUrl").value("https://i.imgur.com/1.jpg"))
        .andExpect(jsonPath("$.bodyText").doesNotExist());
  }

  @Test
  @DisplayName("숨김 글과 없는 글의 상세는 404")
  void hiddenOrMissingDetailIs404() throws Exception {
    Post hidden = save(1, post -> post.setHidden(true));

    mockMvc.perform(get("/api/posts/" + hidden.getId())).andExpect(status().isNotFound());
    mockMvc.perform(get("/api/posts/999999")).andExpect(status().isNotFound());
  }

  @Test
  @DisplayName("로컬 프론트 주소의 CORS 요청을 허용한다")
  void allowsLocalFrontendCors() throws Exception {
    mockMvc
        .perform(get("/api/posts").header(HttpHeaders.ORIGIN, "http://localhost:3000"))
        .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:3000"));
  }
}

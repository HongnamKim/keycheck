package com.keycheck.post.controller;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.keycheck.TestcontainersConfiguration;
import com.keycheck.crawl.client.GeekhackClient;
import com.keycheck.crawl.client.RequestGate;
import com.keycheck.image.domain.ImageSource;
import com.keycheck.post.domain.Board;
import com.keycheck.post.domain.Category;
import com.keycheck.post.domain.Post;
import com.keycheck.post.domain.SpecItem;
import com.keycheck.post.domain.Subcategory;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class PostControllerTest {
  @Autowired private MockMvc mockMvc;
  @Autowired private PostRepository postRepository;
  @Autowired private JdbcTemplate jdbcTemplate;

  // 실제 geekhack을 호출하지 않는다. 목 RequestGate는 간격을 주지 않으므로 목록 확보도 하지 않는다.
  @MockitoBean private GeekhackClient client;
  @MockitoBean private RequestGate gate;

  /** 모든 글이 커버리지 안에 있고 끝까지 받은 상태 */
  @BeforeEach
  void setUp() {
    postRepository.deleteAll();
    jdbcTemplate.update(
        "update board_backfill set next_page = 1, floor_topic_id = 0, exhausted = true");
  }

  /** 기본값으로 글을 만들고 customize로 바꾼 뒤 저장한다. 이미지가 있으면 첫 이미지를 대표로 정한다. */
  private Post save(long topicId, Consumer<Post> customize) {
    Post post =
        Post.create(
            Board.IC,
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
  @DisplayName("기본 목록은 숨김을 빼고 글 번호 내림차순(작성순)이다")
  void defaultListIsRecentWithoutHidden() throws Exception {
    save(1, post -> post.setPostedAt(Instant.parse("2026-10-05T00:00:00Z")));
    save(2, post -> post.setPostedAt(null));
    save(3, post -> post.setPostedAt(Instant.parse("2026-10-01T00:00:00Z")));
    save(4, post -> post.setHidden(true));

    mockMvc
        .perform(get("/api/posts"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items[*].title", contains("글 3", "글 2", "글 1")))
        .andExpect(jsonPath("$.nextCursor").value("1"))
        .andExpect(jsonPath("$.hasMore").value(false));
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
  @DisplayName("세부 분류로 거른다 (최근순·조회수순)")
  void filtersBySubcategory() throws Exception {
    save(
        1,
        post -> {
          post.setSubcategory(Subcategory.KB_65);
          post.setViews(10);
        });
    save(2, post -> post.setSubcategory(Subcategory.KB_60));
    save(
        3,
        post -> {
          post.setSubcategory(Subcategory.KB_65);
          post.setViews(300);
        });
    save(4, post -> {});
    save(
        5,
        post -> {
          post.setCategory(Category.KEYCAP);
          post.setSubcategory(Subcategory.KC_GMK);
        });
    save(
        6,
        post -> {
          post.setCategory(Category.KEYCAP);
          post.setSubcategory(Subcategory.KC_ETC);
        });

    mockMvc
        .perform(get("/api/posts?category=keyboard&sub=65"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items[*].title", contains("글 3", "글 1")))
        .andExpect(jsonPath("$.hasMore").value(false));
    mockMvc
        .perform(get("/api/posts?category=keyboard&sub=65&sort=views"))
        .andExpect(jsonPath("$.items[*].title", contains("글 3", "글 1")));
    mockMvc
        .perform(get("/api/posts?category=keycap&sub=gmk"))
        .andExpect(jsonPath("$.items[*].title", contains("글 5")));
    mockMvc
        .perform(get("/api/posts?category=keycap&sub=etc"))
        .andExpect(jsonPath("$.items[*].title", contains("글 6")));
    mockMvc
        .perform(get("/api/posts?category=keyboard&sub=etc"))
        .andExpect(jsonPath("$.items", hasSize(0)));
  }

  @Test
  @DisplayName("세부 분류 필터도 cursor로 이어서 받는다 (최근순·조회수순)")
  void subcategoryFilterWithCursor() throws Exception {
    for (long topicId = 1; topicId <= 45; topicId++) {
      Subcategory sub = topicId % 3 == 0 ? Subcategory.KB_75 : Subcategory.KB_80;
      int views = (int) topicId;
      save(
          topicId,
          post -> {
            post.setSubcategory(sub);
            post.setViews(views);
          });
    }

    mockMvc
        .perform(get("/api/posts?category=keyboard&sub=75&cursor=10"))
        .andExpect(jsonPath("$.items[*].title", contains("글 9", "글 6", "글 3")))
        .andExpect(jsonPath("$.hasMore").value(false));
    mockMvc
        .perform(get("/api/posts?category=keyboard&sub=80&sort=views&cursor=5_5"))
        .andExpect(jsonPath("$.items[*].title", contains("글 4", "글 2", "글 1")))
        .andExpect(jsonPath("$.hasMore").value(false));
  }

  @Test
  @DisplayName("카드와 상세에 세부 분류 코드를 담는다, 없으면 null")
  void cardAndDetailHaveSubcategory() throws Exception {
    Post post = save(2, p -> p.setSubcategory(Subcategory.KB_65));
    save(1, p -> {});

    mockMvc
        .perform(get("/api/posts"))
        .andExpect(jsonPath("$.items[0].subcategory").value("KB_65"))
        .andExpect(jsonPath("$.items[1].subcategory").value(nullValue()));
    mockMvc
        .perform(get("/api/posts/" + post.getId()))
        .andExpect(jsonPath("$.subcategory").value("KB_65"));
  }

  @Test
  @DisplayName("카드와 상세에 글을 읽어 온 게시판(IC, GB)을 담는다")
  void cardAndDetailHaveBoard() throws Exception {
    Post gb = save(2, p -> p.setBoard(Board.GB));
    Post ic = save(1, p -> {});

    mockMvc
        .perform(get("/api/posts"))
        .andExpect(jsonPath("$.items[*].board", contains("GB", "IC")));
    mockMvc.perform(get("/api/posts/" + gb.getId())).andExpect(jsonPath("$.board").value("GB"));
    mockMvc.perform(get("/api/posts/" + ic.getId())).andExpect(jsonPath("$.board").value("IC"));
  }

  @Test
  @DisplayName("분류가 all이거나 분류와 맞지 않는 세부 분류는 400")
  void invalidSubcategoryIs400() throws Exception {
    for (String query :
        new String[] {
          "sub=65",
          "category=all&sub=65",
          "category=keycap&sub=65",
          "category=keyboard&sub=gmk",
          "category=keyboard&sub=switch",
          "category=keyboard&sub=",
          "category=keycap&sub=gmk&sort=oldest"
        }) {
      mockMvc.perform(get("/api/posts?" + query)).andExpect(status().isBadRequest());
    }
  }

  @Test
  @DisplayName("카드에 대표 이미지와 요약 첫 줄을 담는다, 요약 미완료면 첫 줄은 null")
  void cardHasImageAndFirstLine() throws Exception {
    save(
        2,
        post -> {
          summarized(post);
          withImage(post);
          post.setPostedAt(Instant.parse("2026-10-02T00:00:00Z"));
        });
    save(1, post -> post.setSummaryStatus(SummaryStatus.PENDING));

    mockMvc
        .perform(get("/api/posts"))
        .andExpect(jsonPath("$.items[0].representativeImageUrl").value("https://i.imgur.com/2.jpg"))
        .andExpect(jsonPath("$.items[0].summaryFirstLine").value("첫 줄"))
        .andExpect(jsonPath("$.items[0].statsCheckedAt").value("2026-11-01T00:00:00Z"))
        .andExpect(jsonPath("$.items[0].detailLoaded").value(true))
        .andExpect(jsonPath("$.items[1].representativeImageUrl").value(nullValue()))
        .andExpect(jsonPath("$.items[1].summaryFirstLine").value(nullValue()))
        .andExpect(jsonPath("$.items[1].summaryStatus").value("PENDING"));
  }

  @Test
  @DisplayName("한 번에 20건이고, nextCursor로 이어서 받는다")
  void pageSizeIsTwentyAndCursorContinues() throws Exception {
    for (long topicId = 1; topicId <= 21; topicId++) {
      save(topicId, post -> {});
    }

    mockMvc
        .perform(get("/api/posts"))
        .andExpect(jsonPath("$.items", hasSize(20)))
        .andExpect(jsonPath("$.items[19].title").value("글 2"))
        .andExpect(jsonPath("$.nextCursor").value("2"))
        .andExpect(jsonPath("$.hasMore").value(true));
    mockMvc
        .perform(get("/api/posts?cursor=2"))
        .andExpect(jsonPath("$.items[*].title", contains("글 1")))
        .andExpect(jsonPath("$.nextCursor").value("1"))
        .andExpect(jsonPath("$.hasMore").value(false));
  }

  @Test
  @DisplayName("분류 필터도 cursor로 이어서 받는다")
  void categoryFilterWithCursor() throws Exception {
    for (long topicId = 1; topicId <= 45; topicId++) {
      Category category = topicId % 2 == 0 ? Category.KEYCAP : Category.KEYBOARD;
      save(topicId, post -> post.setCategory(category));
    }

    mockMvc
        .perform(get("/api/posts?category=keycap&cursor=6"))
        .andExpect(jsonPath("$.items[*].title", contains("글 4", "글 2")))
        .andExpect(jsonPath("$.hasMore").value(false));
  }

  @Test
  @DisplayName("잘못된 분류·정렬·cursor는 400")
  void invalidParamsAre400() throws Exception {
    mockMvc.perform(get("/api/posts?category=switch")).andExpect(status().isBadRequest());
    mockMvc.perform(get("/api/posts?sort=oldest")).andExpect(status().isBadRequest());
    mockMvc.perform(get("/api/posts?cursor=abc")).andExpect(status().isBadRequest());
    mockMvc.perform(get("/api/posts?cursor=-5")).andExpect(status().isBadRequest());
    mockMvc.perform(get("/api/posts?sort=views&cursor=abc")).andExpect(status().isBadRequest());
    mockMvc.perform(get("/api/posts?sort=views&cursor=-20_5")).andExpect(status().isBadRequest());
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
        .andExpect(jsonPath("$.detailLoaded").value(true))
        .andExpect(jsonPath("$.bodyText").doesNotExist());
  }

  @Test
  @DisplayName("상세는 후보 이미지 전체를 본문 순서대로 주고, 규칙에서 빠진 이미지는 주지 않는다")
  void detailHasCandidateImagesInOrder() throws Exception {
    Post post =
        save(
            1,
            p -> {
              p.addImage("https://i.imgur.com/second.jpg", ImageSource.EXTERNAL, 2, null);
              p.addImage("https://geekhack.org/Smileys/smile.gif", ImageSource.EXTERNAL, 1, "site-asset");
              p.addImage("https://i.imgur.com/first.jpg", ImageSource.EXTERNAL, 0, null);
              p.addImage(
                  "https://geekhack.org/index.php?action=dlattach;topic=1.0;attach=9;image",
                  ImageSource.ATTACHMENT,
                  3,
                  null);
            });

    mockMvc
        .perform(get("/api/posts/" + post.getId()))
        .andExpect(status().isOk())
        .andExpect(
            jsonPath(
                "$.imageUrls",
                contains(
                    "https://i.imgur.com/first.jpg",
                    "https://i.imgur.com/second.jpg",
                    "https://geekhack.org/index.php?action=dlattach;topic=1.0;attach=9;image")));
  }

  @Test
  @DisplayName("본문에 같은 이미지가 여러 번 나와도 상세 이미지 목록에는 한 번만 준다")
  void detailImagesHaveNoDuplicates() throws Exception {
    Post post =
        save(
            1,
            p -> {
              p.addImage("https://i.imgur.com/a.jpg", ImageSource.EXTERNAL, 0, null);
              p.addImage("https://i.imgur.com/b.jpg", ImageSource.EXTERNAL, 1, null);
              p.addImage("https://i.imgur.com/a.jpg", ImageSource.EXTERNAL, 2, null);
            });

    mockMvc
        .perform(get("/api/posts/" + post.getId()))
        .andExpect(status().isOk())
        .andExpect(
            jsonPath("$.imageUrls", contains("https://i.imgur.com/a.jpg", "https://i.imgur.com/b.jpg")));
  }

  @Test
  @DisplayName("이미지가 없는 글의 상세는 빈 이미지 목록")
  void detailWithoutImagesHasEmptyList() throws Exception {
    Post post = save(1, p -> {});

    mockMvc
        .perform(get("/api/posts/" + post.getId()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.imageUrls", hasSize(0)));
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

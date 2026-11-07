package com.keycheck.post.controller;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.keycheck.TestcontainersConfiguration;
import com.keycheck.crawl.client.GeekhackClient;
import com.keycheck.crawl.client.RequestGate;
import com.keycheck.post.domain.Board;
import com.keycheck.post.domain.Category;
import com.keycheck.post.domain.Post;
import com.keycheck.post.domain.SummaryStatus;
import com.keycheck.post.repository.PostRepository;
import java.time.Instant;
import java.util.List;
import java.util.function.Consumer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.servlet.MockMvc;

/** 사이트맵 글 목록은 검색에 노출할 글만 글 번호 내림차순으로 준다 (수집 기간 경계 since-topic-id = 1000) */
@SpringBootTest(properties = "keycheck.collect.since-topic-id=1000")
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class PostSitemapApiTest {
  private static final Instant CHECKED_AT = Instant.parse("2026-11-01T00:00:00Z");

  @Autowired private MockMvc mockMvc;
  @Autowired private PostRepository postRepository;

  // 실제 geekhack을 호출하지 않는다
  @MockitoBean private GeekhackClient client;
  @MockitoBean private RequestGate gate;

  @BeforeEach
  void setUp() {
    postRepository.deleteAll();
  }

  @AfterEach
  void noGeekhackCalls() {
    verifyNoInteractions(client, gate);
  }

  /** 사이트맵에 들어가는 글(본문을 읽었고 요약이 끝났고 요약 줄이 있다)을 만들고 customize로 바꾼 뒤 저장한다 */
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
            CHECKED_AT,
            "https://geekhack.org/index.php?topic=" + topicId + ".0",
            "본문");
    post.setSummaryStatus(SummaryStatus.DONE);
    post.setSummaryLines(List.of("요약 줄"));
    customize.accept(post);
    return postRepository.saveAndFlush(post);
  }

  @Test
  @DisplayName("두 게시판의 요약 완료 글 ID만 글 번호 내림차순으로 준다")
  void listsIdsByTopicIdDesc() throws Exception {
    // 저장 순서(ID 순서)와 글 번호 순서를 다르게 둔다
    Post oldest = save(1001, post -> {});
    Post newest = save(1003, post -> {});
    Post middle = save(1002, post -> post.setBoard(Board.GB));

    mockMvc
        .perform(get("/api/posts/sitemap"))
        .andExpect(status().isOk())
        .andExpect(
            content()
                .json(
                    "[{\"id\": %d}, {\"id\": %d}, {\"id\": %d}]"
                        .formatted(newest.getId(), middle.getId(), oldest.getId()),
                    JsonCompareMode.STRICT));
  }

  @Test
  @DisplayName("숨긴 글, 수집 기간 이전 글, 틀 글, 요약이 끝나지 않은 글, 요약 줄이 없는 글은 빠진다")
  void excludesPostsNotToIndex() throws Exception {
    Post atSince = save(1000, post -> {});
    save(999, post -> {});
    save(1001, post -> post.setHidden(true));
    save(1002, post -> post.setSummaryStatus(SummaryStatus.PENDING));
    save(1003, post -> post.setSummaryStatus(SummaryStatus.FAILED));
    save(1004, post -> post.setSummaryLines(List.of()));
    save(1005, post -> post.setSummaryLines(null));
    // 본문을 읽지 않은 틀 글은 요약 완료로 두어도 빠진다
    Post skeleton =
        Post.createSkeleton(
            Board.IC,
            1006L,
            "글 1006",
            "maker",
            100,
            5,
            CHECKED_AT,
            "https://geekhack.org/index.php?topic=1006.0");
    skeleton.setSummaryStatus(SummaryStatus.DONE);
    skeleton.setSummaryLines(List.of("요약 줄"));
    postRepository.saveAndFlush(skeleton);

    mockMvc
        .perform(get("/api/posts/sitemap"))
        .andExpect(status().isOk())
        .andExpect(
            content().json("[{\"id\": %d}]".formatted(atSince.getId()), JsonCompareMode.STRICT));
  }
}

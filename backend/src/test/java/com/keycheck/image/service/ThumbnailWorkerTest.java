package com.keycheck.image.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.keycheck.MutableClock;
import com.keycheck.TestcontainersConfiguration;
import com.keycheck.crawl.client.RequestGate;
import com.keycheck.crawl.config.CollectProperties;
import com.keycheck.image.client.ImageDownloader;
import com.keycheck.image.client.ImageFetchException;
import com.keycheck.image.domain.ImageSource;
import com.keycheck.image.storage.FakeThumbStorage;
import com.keycheck.post.domain.Board;
import com.keycheck.post.domain.Category;
import com.keycheck.post.domain.Post;
import com.keycheck.post.domain.PostImage;
import com.keycheck.post.repository.PostImageRepository;
import com.keycheck.post.repository.PostRepository;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * 축소본 작업. 수집 기간 경계는 1000. 원본 받기는 가짜(테스트가 만든 PNG를 주소별로 돌려줌), 저장소는 메모리 가짜다. 실제 이미지 호스트·R2를
 * 부르지 않는다.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class ThumbnailWorkerTest {
  private static final Instant CHECKED_AT = Instant.parse("2026-11-01T00:00:00Z");

  @Autowired private PostRepository postRepository;
  @Autowired private PostImageRepository imageRepository;
  @Autowired private JdbcTemplate jdbcTemplate;

  @MockitoBean private ImageDownloader downloader;
  @MockitoBean private RequestGate gate;

  private final FakeThumbStorage storage = new FakeThumbStorage();
  private final MutableClock clock = new MutableClock();

  /** 주소 → 가짜 원본. 없는 주소는 받지 못한다(ImageFetchException). */
  private final Map<String, byte[]> originals = new HashMap<>();

  /** 받은 주소(순서대로) */
  private final List<String> downloaded = new ArrayList<>();

  private ThumbnailWorker worker;

  @BeforeEach
  void setUp() {
    postRepository.deleteAll();
    when(gate.tryAcquireIdle()).thenReturn(true);
    when(downloader.download(anyString()))
        .thenAnswer(
            invocation -> {
              String url = invocation.getArgument(0);
              downloaded.add(url);
              byte[] original = originals.get(url);
              if (original == null) {
                throw new ImageFetchException("없는 주소: " + url);
              }
              return original;
            });
    worker =
        new ThumbnailWorker(
            imageRepository,
            downloader,
            new ThumbnailMaker(),
            storage,
            gate,
            new CollectProperties(1000),
            clock);
  }

  private static byte[] png(int width, int height) {
    BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
    Graphics2D graphics = image.createGraphics();
    graphics.setColor(Color.ORANGE);
    graphics.fillRect(0, 0, width, height);
    graphics.dispose();
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    try {
      ImageIO.write(image, "png", out);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
    return out.toByteArray();
  }

  private static int width(byte[] webp) throws IOException {
    return ImageIO.read(new ByteArrayInputStream(webp)).getWidth();
  }

  /** 본문까지 읽은 글. customize에서 이미지를 넣는다. */
  private Post save(long topicId, Consumer<Post> customize) {
    Post post =
        Post.create(
            Board.IC,
            topicId,
            "[IC] 글 " + topicId,
            "maker",
            Category.KEYBOARD,
            Instant.parse("2026-10-13T00:00:00Z"),
            10,
            1,
            CHECKED_AT,
            "https://geekhack.test/index.php?topic=" + topicId + ".0",
            "본문 " + topicId);
    customize.accept(post);
    return postRepository.saveAndFlush(post);
  }

  /** 외부 호스트 이미지만 있는 글. 주소 순서가 본문 순서다. */
  private Post saveWithImages(long topicId, String... urls) {
    return save(
        topicId,
        post -> {
          for (int i = 0; i < urls.length; i++) {
            post.addImage(urls[i], ImageSource.EXTERNAL, i, null);
          }
        });
  }

  private void markRepresentative(Post post, PostImage image) {
    jdbcTemplate.update(
        "update post set representative_image_id = ? where id = ?", image.getId(), post.getId());
  }

  private PostImage reload(PostImage image) {
    return imageRepository.findById(image.getId()).orElseThrow();
  }

  private static String small(PostImage image) {
    return "thumbs/" + image.getId() + ".webp";
  }

  private static String large(PostImage image) {
    return "thumbs/" + image.getId() + "-1280.webp";
  }

  @Test
  @DisplayName("본문 이미지는 가로 640px 축소본 하나를 올리고 객체 이름을 저장한다")
  void makesSmallThumb() throws IOException {
    originals.put("https://i.imgur.com/a.png", png(2000, 1000));
    PostImage image = saveWithImages(2000, "https://i.imgur.com/a.png").getImages().getFirst();

    assertThat(worker.makeNext()).isTrue();

    PostImage saved = reload(image);
    assertThat(saved.getThumbKey()).isEqualTo(small(image));
    assertThat(saved.getThumbLargeKey()).isNull();
    assertThat(saved.getThumbFailures()).isZero();
    assertThat(storage.objects).containsOnlyKeys(small(image));
    assertThat(width(storage.objects.get(small(image)))).isEqualTo(640);
    assertThat(worker.makeNext()).isFalse();
    // geekhack 이미지가 아니면 요청 간격을 쓰지 않는다
    verify(gate, never()).tryAcquireIdle();
  }

  @Test
  @DisplayName("대표 이미지는 640px와 1280px 축소본을 함께 만든다")
  void representativeAlsoGetsLarge() throws IOException {
    originals.put("https://i.imgur.com/a.png", png(3000, 1500));
    Post post = saveWithImages(2000, "https://i.imgur.com/a.png");
    PostImage image = post.getImages().getFirst();
    markRepresentative(post, image);

    assertThat(worker.makeNext()).isTrue();

    PostImage saved = reload(image);
    assertThat(saved.getThumbKey()).isEqualTo(small(image));
    assertThat(saved.getThumbLargeKey()).isEqualTo(large(image));
    assertThat(storage.objects).containsOnlyKeys(small(image), large(image));
    assertThat(width(storage.objects.get(small(image)))).isEqualTo(640);
    assertThat(width(storage.objects.get(large(image)))).isEqualTo(1280);
  }

  @Test
  @DisplayName("1280px만 만들 수 없는 긴 대표 이미지(세로가 WebP 한계를 넘음)는 640px만 만들고 완료한다")
  void tooTallRepresentativeGetsSmallOnly() throws IOException {
    // 640px로 줄이면 세로 14994px라 만들 수 있지만, 1280px는 원본 폭(700) 그대로라 세로 16400px로 WebP 한계(16383)를 넘는다.
    originals.put("https://i.imgur.com/tall.png", png(700, 16400));
    Post post = saveWithImages(2000, "https://i.imgur.com/tall.png");
    PostImage image = post.getImages().getFirst();
    markRepresentative(post, image);

    assertThat(worker.makeNext()).isTrue();

    PostImage saved = reload(image);
    assertThat(saved.getThumbKey()).isEqualTo(small(image));
    assertThat(saved.getThumbLargeKey()).isNull();
    assertThat(saved.getThumbFailures()).isZero();
    assertThat(storage.objects).containsOnlyKeys(small(image));
    assertThat(width(storage.objects.get(small(image)))).isEqualTo(640);
    assertThat(worker.makeNext()).isFalse();
  }

  @Test
  @DisplayName("받기 전에 실패로 먼저 세어 두고 만들면 되돌린다(만들다 프로세스가 죽어도 같은 이미지를 MAX_FAILURES번만 고른다)")
  void countsAttemptBeforeDownload() {
    PostImage image = saveWithImages(2000, "https://i.imgur.com/a.png").getImages().getFirst();
    List<Integer> failuresWhileDownloading = new ArrayList<>();
    doAnswer(
            invocation -> {
              failuresWhileDownloading.add(
                  jdbcTemplate.queryForObject(
                      "select thumb_failures from post_image where id = ?",
                      Integer.class,
                      image.getId()));
              return png(100, 50);
            })
        .when(downloader)
        .download(anyString());

    assertThat(worker.makeNext()).isTrue();

    assertThat(failuresWhileDownloading).containsExactly(1);
    assertThat(reload(image).getThumbKey()).isEqualTo(small(image));
    assertThat(reload(image).getThumbFailures()).isZero();
  }

  @Test
  @DisplayName("예상하지 못한 오류(WebP 네이티브 라이브러리를 읽지 못함 등)는 실패로 세지 않고 잠시 쉬었다가 다시 만든다")
  void unexpectedErrorPausesWithoutCounting() {
    PostImage image = saveWithImages(2000, "https://i.imgur.com/a.png").getImages().getFirst();
    doThrow(new UnsatisfiedLinkError("WebP 네이티브 라이브러리 없음"))
        .when(downloader)
        .download(anyString());

    assertThat(worker.makeNext()).isTrue();
    assertThat(reload(image).getThumbFailures()).isZero();
    assertThat(worker.makeNext()).isFalse();

    doThrow(new IllegalStateException("예상하지 못한 오류")).when(downloader).download(anyString());
    clock.advance(ThumbnailWorker.ERROR_PAUSE);
    assertThat(worker.makeNext()).isTrue();
    assertThat(reload(image).getThumbFailures()).isZero();
    assertThat(worker.makeNext()).isFalse();
    verify(downloader, times(2)).download(anyString());

    doReturn(png(100, 50)).when(downloader).download(anyString());
    clock.advance(ThumbnailWorker.ERROR_PAUSE);
    assertThat(worker.makeNext()).isTrue();
    assertThat(reload(image).getThumbKey()).isEqualTo(small(image));
    assertThat(reload(image).getThumbFailures()).isZero();
  }

  @Test
  @DisplayName("최근 글부터, 글 안에서는 대표 이미지 먼저 그다음 본문 순서로 만든다")
  void representativeFirstThenRecentPost() {
    for (String url :
        List.of(
            "https://a.test/0.png",
            "https://a.test/1.png",
            "https://a.test/2.png",
            "https://b.test/0.png")) {
      originals.put(url, png(100, 50));
    }
    Post older =
        saveWithImages(2000, "https://a.test/0.png", "https://a.test/1.png", "https://a.test/2.png");
    // 가운데 이미지가 대표라 대표 먼저와 본문 순서(앞뒤)를 함께 본다
    markRepresentative(older, older.getImages().get(1));
    saveWithImages(3000, "https://b.test/0.png");

    while (worker.makeNext()) {
      // 대상이 없을 때까지
    }

    assertThat(downloaded)
        .containsExactly(
            "https://b.test/0.png",
            "https://a.test/1.png",
            "https://a.test/0.png",
            "https://a.test/2.png");
  }

  @Test
  @DisplayName("본문을 늦게 채운 글을 글 번호가 작아도 먼저 만든다")
  void justFilledPostFirst() {
    originals.put("https://a.test/0.png", png(100, 50));
    originals.put("https://b.test/0.png", png(100, 50));
    Post filledLater = saveWithImages(2000, "https://a.test/0.png");
    jdbcTemplate.update(
        "update post set detail_fetched_at = ? where id = ?",
        Timestamp.from(CHECKED_AT.plusSeconds(60)),
        filledLater.getId());
    saveWithImages(3000, "https://b.test/0.png");

    while (worker.makeNext()) {
      // 대상이 없을 때까지
    }

    assertThat(downloaded).containsExactly("https://a.test/0.png", "https://b.test/0.png");
  }

  @Test
  @DisplayName("미리 읽기로 채우고 아무도 열지 않은 글은 본문을 늦게 채웠어도 다른 글 뒤에 만든다")
  void prefetchedPostAfterOthers() {
    originals.put("https://a.test/0.png", png(100, 50));
    originals.put("https://b.test/0.png", png(100, 50));
    saveWithImages(2000, "https://a.test/0.png");
    Post prefetched = saveWithImages(3000, "https://b.test/0.png");
    jdbcTemplate.update(
        "update post set prefetched = true, detail_fetched_at = ? where id = ?",
        Timestamp.from(CHECKED_AT.plusSeconds(60)),
        prefetched.getId());

    while (worker.makeNext()) {
      // 대상이 없을 때까지
    }

    assertThat(downloaded).containsExactly("https://a.test/0.png", "https://b.test/0.png");
  }

  @Test
  @DisplayName("한 글에 같은 주소가 두 번 있으면 한 번만 받고, 두 행이 대표 행의 축소본을 가리키며 대표 행만 1280px를 가진다")
  void duplicateUrlInPostMadeOnce() {
    originals.put("https://i.imgur.com/same.png", png(3000, 1500));
    Post post =
        saveWithImages(2000, "https://i.imgur.com/same.png", "https://i.imgur.com/same.png");
    PostImage body = post.getImages().get(0);
    PostImage representative = post.getImages().get(1);
    markRepresentative(post, representative);

    while (worker.makeNext()) {
      // 대상이 없을 때까지
    }

    assertThat(downloaded).containsExactly("https://i.imgur.com/same.png");
    assertThat(reload(representative).getThumbKey()).isEqualTo(small(representative));
    assertThat(reload(representative).getThumbLargeKey()).isEqualTo(large(representative));
    assertThat(reload(body).getThumbKey()).isEqualTo(small(representative));
    assertThat(reload(body).getThumbLargeKey()).isNull();
    assertThat(storage.objects).containsOnlyKeys(small(representative), large(representative));
  }

  @Test
  @DisplayName("제외된 이미지, 숨긴 글, 틀 글, 수집 기간 경계 아래 글의 이미지는 만들지 않고 경계 글 번호 그 글은 만든다")
  void skipsExcludedHiddenSkeletonAndBeforeSince() {
    originals.put("https://a.test/at-since.png", png(100, 50));
    save(
        2000,
        post ->
            post.addImage(
                "https://geekhack.org/Smileys/smile.gif", ImageSource.EXTERNAL, 0, "site-asset"));
    save(
        3000,
        post -> {
          post.setHidden(true);
          post.addImage("https://a.test/hidden.png", ImageSource.EXTERNAL, 0, null);
        });
    Post skeleton =
        Post.createSkeleton(
            Board.IC,
            4000L,
            "[IC] 틀",
            "maker",
            1,
            0,
            CHECKED_AT,
            "https://geekhack.test/?topic=4000.0");
    skeleton.addImage("https://a.test/skeleton.png", ImageSource.EXTERNAL, 0, null);
    postRepository.saveAndFlush(skeleton);
    saveWithImages(999, "https://a.test/before-since.png");
    saveWithImages(1000, "https://a.test/at-since.png");

    assertThat(worker.makeNext()).isTrue();
    assertThat(worker.makeNext()).isFalse();

    assertThat(downloaded).containsExactly("https://a.test/at-since.png");
  }

  @Test
  @DisplayName("이미지로 읽을 수 없는 원본은 실패로 세고, MAX_FAILURES번 실패하면 더 시도하지 않는다")
  void unreadableCountsFailureAndStops() {
    originals.put("https://i.imgur.com/page.png", "<html>".getBytes(StandardCharsets.UTF_8));
    PostImage image = saveWithImages(2000, "https://i.imgur.com/page.png").getImages().getFirst();

    for (int i = 0; i < ThumbnailWorker.MAX_FAILURES; i++) {
      assertThat(worker.makeNext()).isTrue();
    }

    assertThat(reload(image).getThumbFailures()).isEqualTo(3);
    assertThat(reload(image).getThumbKey()).isNull();
    assertThat(worker.makeNext()).isFalse();
    assertThat(downloaded).hasSize(3);
    assertThat(storage.objects).isEmpty();
  }

  @Test
  @DisplayName("한 글에 같은 주소가 두 번 있고 받지 못하면 두 행의 실패를 함께 세어 MAX_FAILURES번만 받는다")
  void duplicateUrlFailureCountsAllRows() {
    Post post =
        saveWithImages(2000, "https://i.imgur.com/gone.png", "https://i.imgur.com/gone.png");
    PostImage body = post.getImages().get(0);
    PostImage representative = post.getImages().get(1);
    markRepresentative(post, representative);

    for (int i = 0; i < ThumbnailWorker.MAX_FAILURES; i++) {
      assertThat(worker.makeNext()).isTrue();
    }

    assertThat(reload(representative).getThumbFailures()).isEqualTo(3);
    assertThat(reload(body).getThumbFailures()).isEqualTo(3);
    assertThat(worker.makeNext()).isFalse();
    assertThat(downloaded).hasSize(3);
  }

  @Test
  @DisplayName("원본을 받지 못해도(4xx·5xx·막힌 주소 등) 실패로 센다")
  void fetchFailureCounts() {
    PostImage image = saveWithImages(2000, "https://i.imgur.com/gone.png").getImages().getFirst();

    assertThat(worker.makeNext()).isTrue();

    assertThat(reload(image).getThumbFailures()).isEqualTo(1);
    assertThat(reload(image).getThumbKey()).isNull();
  }

  @Test
  @DisplayName("저장소에 올리지 못하면 실패로 세지 않고 잠시 쉬었다가 다시 만든다")
  void storageDownPausesWithoutCounting() {
    originals.put("https://i.imgur.com/a.png", png(100, 50));
    PostImage image = saveWithImages(2000, "https://i.imgur.com/a.png").getImages().getFirst();
    storage.unavailable = true;

    assertThat(worker.makeNext()).isTrue();
    assertThat(reload(image).getThumbFailures()).isZero();
    assertThat(reload(image).getThumbKey()).isNull();

    assertThat(worker.makeNext()).isFalse();
    clock.advance(ThumbnailWorker.ERROR_PAUSE.minusSeconds(1));
    assertThat(worker.makeNext()).isFalse();
    assertThat(downloaded).hasSize(1);

    storage.unavailable = false;
    clock.advance(Duration.ofSeconds(1));
    assertThat(worker.makeNext()).isTrue();
    assertThat(reload(image).getThumbKey()).isEqualTo(small(image));
    assertThat(reload(image).getThumbFailures()).isZero();
  }

  @Test
  @DisplayName("geekhack 첨부는 빈 요청 간격이 없으면 받지 않고 그대로 두며, 간격이 비면 만든다")
  void attachmentWaitsForIdleGate() {
    String url = "https://geekhack.org/index.php?action=dlattach;topic=2000.0;attach=1;image";
    originals.put(url, png(100, 50));
    PostImage image =
        save(2000, post -> post.addImage(url, ImageSource.ATTACHMENT, 0, null))
            .getImages()
            .getFirst();
    when(gate.tryAcquireIdle()).thenReturn(false);

    assertThat(worker.makeNext()).isTrue();

    verify(downloader, never()).download(anyString());
    assertThat(reload(image).getThumbFailures()).isZero();
    assertThat(reload(image).getThumbKey()).isNull();

    when(gate.tryAcquireIdle()).thenReturn(true);
    assertThat(worker.makeNext()).isTrue();
    assertThat(reload(image).getThumbKey()).isEqualTo(small(image));
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "https://www.GeekHack.org/files/render.png",
        "https://geekhack.org./Themes/render.png"
      })
  @DisplayName("geekhack 호스트에 있는 이미지도(끝에 점이 붙은 이름 포함) 첨부처럼 빈 요청 간격을 기다린다")
  void geekhackHostedImageWaitsForIdleGate(String url) {
    saveWithImages(2000, url);
    when(gate.tryAcquireIdle()).thenReturn(false);

    assertThat(worker.makeNext()).isTrue();

    verify(gate).tryAcquireIdle();
    verify(downloader, never()).download(anyString());
  }

  @ParameterizedTest
  @ValueSource(strings = {"https://evilgeekhack.org/a.png", "https://geekhack.org.evil.test/a.png"})
  @DisplayName("geekhack을 닮은 다른 호스트의 이미지는 요청 간격을 기다리지 않고 만든다")
  void lookalikeHostDoesNotWaitForGate(String url) {
    originals.put(url, png(100, 50));
    PostImage image = saveWithImages(2000, url).getImages().getFirst();
    when(gate.tryAcquireIdle()).thenReturn(false);

    assertThat(worker.makeNext()).isTrue();

    verify(gate, never()).tryAcquireIdle();
    assertThat(downloaded).containsExactly(url);
    assertThat(reload(image).getThumbKey()).isEqualTo(small(image));
  }
}

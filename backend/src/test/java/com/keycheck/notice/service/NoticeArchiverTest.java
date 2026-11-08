package com.keycheck.notice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.keycheck.MutableClock;
import com.keycheck.TestcontainersConfiguration;
import com.keycheck.image.client.ImageDownloader;
import com.keycheck.image.client.ImageFetchException;
import com.keycheck.image.service.ThumbnailMaker;
import com.keycheck.image.storage.FakeThumbStorage;
import com.keycheck.notice.client.DiscordClient;
import com.keycheck.notice.client.DiscordUnavailableException;
import com.keycheck.notice.domain.ArchiveStatus;
import com.keycheck.notice.domain.Notice;
import com.keycheck.notice.domain.NoticeAttachment;
import com.keycheck.notice.domain.NoticeOrigin;
import com.keycheck.notice.dto.DiscordMessage;
import com.keycheck.notice.repository.NoticeAttachmentRepository;
import com.keycheck.notice.repository.NoticeRepository;
import com.keycheck.post.domain.KoreanVendor;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import tools.jackson.databind.json.JsonMapper;

/** 공지 첨부 보관. 원본 받기는 가짜(주소별로 테스트가 만든 바이트), 저장소는 메모리 가짜다. */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class NoticeArchiverTest {
  private static final long CHANNEL = 555L;

  @Autowired private NoticeRepository noticeRepository;
  @Autowired private NoticeAttachmentRepository attachmentRepository;

  @MockitoBean private ImageDownloader downloader;
  @MockitoBean private DiscordClient discordClient;

  private final FakeThumbStorage storage = new FakeThumbStorage();
  private final MutableClock clock = new MutableClock();
  private final Map<String, byte[]> originals = new HashMap<>();
  private NoticeArchiver archiver;
  private long nextMessageId = 1000;

  @BeforeEach
  void setUp() {
    noticeRepository.deleteAll();
    originals.clear();
    when(downloader.download(anyString()))
        .thenAnswer(
            invocation -> {
              String url = invocation.getArgument(0);
              byte[] original = originals.get(url);
              if (original == null) {
                throw new ImageFetchException("없는 주소: " + url);
              }
              return original;
            });
    archiver =
        new NoticeArchiver(
            attachmentRepository, downloader, new ThumbnailMaker(), storage, discordClient, clock);
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

  /** 첨부 (id, 주소)를 가진 공지를 저장한다. 봇 공지면 channel_message_id가 있다. */
  private Notice save(NoticeOrigin origin, Object... idAndUrl) {
    long messageId = nextMessageId++;
    Notice notice =
        Notice.create(
            messageId,
            origin == NoticeOrigin.BOT ? messageId + 1 : null,
            CHANNEL,
            KoreanVendor.SWAGKEYS,
            origin,
            Instant.parse("2026-11-06T10:00:00Z"),
            "공지",
            true,
            "{}");
    for (int i = 0; i < idAndUrl.length; i += 2) {
      notice.addAttachment((Long) idAndUrl[i], i / 2, "a.png", (String) idAndUrl[i + 1]);
    }
    return noticeRepository.save(notice);
  }

  private NoticeAttachment reload(long id) {
    return attachmentRepository.findById(id).orElseThrow();
  }

  @Test
  @DisplayName("세 크기를 올리고 보관 완료로 표시한다")
  void 세_크기를_올리고_보관_완료로_표시한다() throws IOException {
    save(NoticeOrigin.BOT, 7L, "https://cdn/7.png");
    originals.put("https://cdn/7.png", png(3000, 2000));

    assertThat(archiver.archiveNext()).isTrue();

    assertThat(width(storage.objects.get("notices/7.webp"))).isEqualTo(2048);
    assertThat(width(storage.objects.get("notices/7-1280.webp"))).isEqualTo(1280);
    assertThat(width(storage.objects.get("notices/7-640.webp"))).isEqualTo(640);
    assertThat(reload(7L).getArchiveStatus()).isEqualTo(ArchiveStatus.DONE);
  }

  @Test
  @DisplayName("작은 원본은 키우지 않는다")
  void 작은_원본은_키우지_않는다() throws IOException {
    save(NoticeOrigin.BOT, 8L, "https://cdn/8.png");
    originals.put("https://cdn/8.png", png(500, 400));

    archiver.archiveNext();

    assertThat(width(storage.objects.get("notices/8.webp"))).isEqualTo(500);
    assertThat(width(storage.objects.get("notices/8-1280.webp"))).isEqualTo(500);
    assertThat(width(storage.objects.get("notices/8-640.webp"))).isEqualTo(500);
  }

  @Test
  @DisplayName("이미지가 아닌 첨부는 실패로 끝내고 다른 첨부는 보관한다")
  void 이미지가_아닌_첨부는_실패로_끝내고_다른_첨부는_보관한다() {
    save(NoticeOrigin.BOT, 1L, "https://cdn/1.mp4", 2L, "https://cdn/2.png");
    originals.put("https://cdn/1.mp4", "not an image".getBytes(StandardCharsets.UTF_8));
    originals.put("https://cdn/2.png", png(100, 100));

    archiver.archiveNext();
    archiver.archiveNext();

    assertThat(reload(1L).getArchiveStatus()).isEqualTo(ArchiveStatus.FAILED);
    assertThat(reload(2L).getArchiveStatus()).isEqualTo(ArchiveStatus.DONE);
    assertThat(storage.objects).doesNotContainKey("notices/1.webp");
  }

  @Test
  @DisplayName("받기에 실패하면 봇 공지는 새 주소로 바꿔 다시 시도한다")
  void 받기에_실패하면_봇_공지는_새_주소로_바꿔_다시_시도한다() {
    Notice notice = save(NoticeOrigin.BOT, 3L, "https://cdn/old.png");
    originals.put("https://cdn/new.png", png(100, 100));
    String message =
        "{\"id\":\"%d\",\"type\":0,\"channel_id\":\"555\",\"attachments\":[{\"id\":\"3\",\"filename\":\"a.png\",\"url\":\"https://cdn/new.png\"}]}"
            .formatted(notice.getChannelMessageId());
    when(discordClient.message("555", String.valueOf(notice.getChannelMessageId())))
        .thenReturn(
            Optional.of(DiscordMessage.from(JsonMapper.builder().build().readTree(message))));

    archiver.archiveNext();

    assertThat(reload(3L).getSourceUrl()).isEqualTo("https://cdn/new.png");
    assertThat(reload(3L).getArchiveStatus()).isEqualTo(ArchiveStatus.PENDING);

    archiver.archiveNext();

    assertThat(reload(3L).getArchiveStatus()).isEqualTo(ArchiveStatus.DONE);
  }

  @Test
  @DisplayName("주소를 새로 받는 중 디스코드 오류가 나도 실패로만 센다")
  void 주소를_새로_받는_중_디스코드_오류가_나도_실패로만_센다() {
    save(NoticeOrigin.BOT, 4L, "https://cdn/gone.png");
    when(discordClient.message(anyString(), anyString()))
        .thenThrow(new DiscordUnavailableException("장애"));

    assertThat(archiver.archiveNext()).isTrue();

    assertThat(reload(4L).getFailures()).isEqualTo(1);
    assertThat(reload(4L).getArchiveStatus()).isEqualTo(ArchiveStatus.PENDING);
  }

  @Test
  @DisplayName("가져온 공지는 주소를 새로 받지 않는다")
  void 가져온_공지는_주소를_새로_받지_않는다() {
    save(NoticeOrigin.BACKFILL, 5L, "https://cdn/gone.png");

    archiver.archiveNext();

    verify(discordClient, never()).message(anyString(), anyString());
    assertThat(reload(5L).getFailures()).isEqualTo(1);
  }

  @Test
  @DisplayName("세 번 실패하면 실패로 끝낸다")
  void 세_번_실패하면_실패로_끝낸다() {
    save(NoticeOrigin.BACKFILL, 6L, "https://cdn/gone.png");

    archiver.archiveNext();
    archiver.archiveNext();
    assertThat(reload(6L).getArchiveStatus()).isEqualTo(ArchiveStatus.PENDING);
    archiver.archiveNext();

    assertThat(reload(6L).getArchiveStatus()).isEqualTo(ArchiveStatus.FAILED);
    assertThat(archiver.archiveNext()).isFalse();
  }

  @Test
  @DisplayName("받을 주소가 없는 첨부는 실패로 끝낸다")
  void 받을_주소가_없는_첨부는_실패로_끝낸다() {
    save(NoticeOrigin.BACKFILL, 9L, null);

    archiver.archiveNext();

    assertThat(reload(9L).getArchiveStatus()).isEqualTo(ArchiveStatus.FAILED);
    verify(downloader, never()).download(eq(null));
  }

  @Test
  @DisplayName("저장소 장애는 실패로 세지 않고 쉰다")
  void 저장소_장애는_실패로_세지_않고_쉰다() {
    save(NoticeOrigin.BOT, 10L, "https://cdn/10.png");
    originals.put("https://cdn/10.png", png(100, 100));
    storage.unavailable = true;

    assertThat(archiver.archiveNext()).isTrue();

    assertThat(reload(10L).getFailures()).isZero();
    assertThat(reload(10L).getArchiveStatus()).isEqualTo(ArchiveStatus.PENDING);
    storage.unavailable = false;
    assertThat(archiver.archiveNext()).isFalse();
    clock.advance(Duration.ofMinutes(1));
    assertThat(archiver.archiveNext()).isTrue();
    assertThat(reload(10L).getArchiveStatus()).isEqualTo(ArchiveStatus.DONE);
  }
}

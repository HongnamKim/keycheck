package com.keycheck.notice.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.keycheck.TestcontainersConfiguration;
import com.keycheck.image.service.ThumbnailMaker;
import com.keycheck.image.storage.FakeThumbStorage;
import com.keycheck.notice.domain.ArchiveStatus;
import com.keycheck.notice.domain.Notice;
import com.keycheck.notice.domain.NoticeOrigin;
import com.keycheck.notice.repository.NoticeAttachmentRepository;
import com.keycheck.notice.repository.NoticeRepository;
import com.keycheck.post.domain.KoreanVendor;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

/** 지난 공지 가져오기. 묶음은 손으로 쓴 JSON과 만든 PNG이고, 저장소는 메모리 가짜다. */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class NoticeImporterTest {
  // 2026-11-06T10:00:00Z 근처 snowflake
  private static final long BASE = ((1793959200000L - 1420070400000L) << 22);
  private static final long MINUTE = 60_000L << 22;

  @Autowired private NoticeIngestService ingestService;
  @Autowired private NoticeRepository noticeRepository;
  @Autowired private NoticeAttachmentRepository attachmentRepository;

  @TempDir private Path dir;

  private final FakeThumbStorage storage = new FakeThumbStorage();
  private NoticeImporter importer;

  @BeforeEach
  void setUp() throws IOException {
    noticeRepository.deleteAll();
    Files.createDirectories(dir.resolve("attachments"));
    NoticeArchiver archiver =
        new NoticeArchiver(
            attachmentRepository, null, new ThumbnailMaker(), storage, null, Clock.systemUTC());
    importer = new NoticeImporter(ingestService, archiver, attachmentRepository);
  }

  private static byte[] png() {
    BufferedImage image = new BufferedImage(300, 200, BufferedImage.TYPE_INT_RGB);
    Graphics2D graphics = image.createGraphics();
    graphics.setColor(Color.ORANGE);
    graphics.fillRect(0, 0, 300, 200);
    graphics.dispose();
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    try {
      ImageIO.write(image, "png", out);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
    return out.toByteArray();
  }

  private void writeFile(String name) throws IOException {
    Files.write(dir.resolve("attachments").resolve(name), png());
  }

  private void writeMessages(String json) throws IOException {
    Files.writeString(dir.resolve("messages.json"), json, StandardCharsets.UTF_8);
  }

  /** 메시지 하나의 JSON. localFile이 null이면 local_file 필드를 뺀다 */
  private static String message(long id, String content, long attachmentId, String localFile) {
    String attachments =
        attachmentId == 0
            ? "[]"
            : "[{\"id\":\"%d\",\"filename\":\"x.png\",\"url\":\"https://expired/x.png\"%s}]"
                .formatted(
                    attachmentId,
                    localFile == null ? "" : ",\"local_file\":\"" + localFile + "\"");
    return ("{\"id\":\"%d\",\"type\":0,\"channel_id\":\"555\",\"content\":\"%s\","
            + "\"attachments\":%s}")
        .formatted(id, content, attachments);
  }

  private ArchiveStatus status(long attachmentId) {
    return attachmentRepository.findById(attachmentId).orElseThrow().getArchiveStatus();
  }

  @Test
  @DisplayName("오래된 순으로 넣고 첨부를 보관한다")
  void 오래된_순으로_넣고_첨부를_보관한다() throws IOException {
    writeFile("7__a.png");
    writeFile("8__b.png");
    // 일부러 최신 메시지를 앞에 둔다
    writeMessages(
        "["
            + message(BASE + MINUTE, "두 번째 판매", 8, "8__b.png")
            + ","
            + message(BASE, "첫 번째 판매", 7, "7__a.png")
            + "]");

    ImportReport report = importer.importDir(dir, KoreanVendor.SWAGKEYS);

    assertThat(report.getImported()).isEqualTo(2);
    assertThat(report.getAttachmentsStored()).isEqualTo(2);
    assertThat(report.isAborted()).isFalse();
    assertThat(noticeRepository.findAll())
        .hasSize(2)
        .allSatisfy(n -> assertThat(n.getOrigin()).isEqualTo(NoticeOrigin.BACKFILL));
    assertThat(status(7)).isEqualTo(ArchiveStatus.DONE);
    assertThat(status(8)).isEqualTo(ArchiveStatus.DONE);
    assertThat(storage.objects)
        .containsKeys(
            "notices/7.webp", "notices/7-1280.webp", "notices/7-640.webp", "notices/8.webp");
    assertThat(attachmentRepository.findById(7L).orElseThrow().getSourceUrl()).isNull();
  }

  @Test
  @DisplayName("사진만 있는 메시지는 앞 공지에 묶인다")
  void 사진만_있는_메시지는_앞_공지에_묶인다() throws IOException {
    writeFile("7__a.png");
    writeFile("8__b.png");
    writeMessages(
        "["
            + message(BASE, "판매 공지", 7, "7__a.png")
            + ","
            + message(BASE + MINUTE, "", 8, "8__b.png")
            + "]");

    importer.importDir(dir, KoreanVendor.SWAGKEYS);

    Notice photoOnly = noticeRepository.findAll().stream().filter(n -> !n.isHasText()).findFirst().orElseThrow();
    assertThat(photoOnly.getParent()).isNotNull();
    assertThat(photoOnly.getParent().isHasText()).isTrue();
  }

  @Test
  @DisplayName("이미 있는 id는 건너뛴다")
  void 이미_있는_id는_건너뛴다() throws IOException {
    writeFile("7__a.png");
    writeFile("8__b.png");
    writeMessages(
        "["
            + message(BASE, "첫 번째 판매", 7, "7__a.png")
            + ","
            + message(BASE + MINUTE, "두 번째 판매", 8, "8__b.png")
            + "]");
    importer.importDir(dir, KoreanVendor.SWAGKEYS);

    ImportReport second = importer.importDir(dir, KoreanVendor.SWAGKEYS);

    assertThat(second.getImported()).isZero();
    assertThat(second.getSkipped()).isEqualTo(2);
    assertThat(second.getAttachmentsStored()).isZero();
    assertThat(noticeRepository.count()).isEqualTo(2);
  }

  @Test
  @DisplayName("파일이 없는 첨부는 실패로 둔다")
  void 파일이_없는_첨부는_실패로_둔다() throws IOException {
    writeFile("8__b.png");
    // attachments 폴더 밖에 읽을 수 있는 진짜 PNG를 둔다. 폴더 검사가 없으면 보관되어 DONE이 된다
    Files.write(dir.resolve("outside.png"), png());
    writeMessages(
        "["
            + message(BASE, "local_file 없음", 7, null)
            + ","
            + message(BASE + MINUTE, "파일 없음", 8, "missing.png")
            + ","
            + message(BASE + 2 * MINUTE, "폴더 밖", 9, "../outside.png")
            + "]");

    ImportReport report = importer.importDir(dir, KoreanVendor.SWAGKEYS);

    assertThat(report.getAttachmentsFailed()).isEqualTo(3);
    assertThat(report.getAttachmentsStored()).isZero();
    assertThat(status(7)).isEqualTo(ArchiveStatus.FAILED);
    assertThat(status(8)).isEqualTo(ArchiveStatus.FAILED);
    assertThat(status(9)).isEqualTo(ArchiveStatus.FAILED);
    assertThat(storage.objects.keySet()).noneMatch(key -> key.startsWith("notices/9"));
  }

  @Test
  @DisplayName("저장소 장애로 멈추면 중단으로 보고하고, 다시 실행하면 보관 안 된 첨부를 이어서 끝낸다")
  void 저장소_장애로_멈추면_다시_실행해_이어서_끝낸다() throws IOException {
    writeFile("7__a.png");
    writeFile("8__b.png");
    writeMessages(
        "["
            + message(BASE, "첫 번째 판매", 7, "7__a.png")
            + ","
            + message(BASE + MINUTE, "두 번째 판매", 8, "8__b.png")
            + "]");
    storage.unavailable = true;

    ImportReport first = importer.importDir(dir, KoreanVendor.SWAGKEYS);

    assertThat(first.isAborted()).isTrue();
    assertThat(first.getImported()).isEqualTo(1);
    assertThat(status(7)).isEqualTo(ArchiveStatus.PENDING);

    storage.unavailable = false;
    ImportReport second = importer.importDir(dir, KoreanVendor.SWAGKEYS);

    assertThat(second.isAborted()).isFalse();
    assertThat(second.getImported()).isEqualTo(1);
    assertThat(second.getSkipped()).isEqualTo(1);
    assertThat(second.getAttachmentsStored()).isEqualTo(2);
    assertThat(status(7)).isEqualTo(ArchiveStatus.DONE);
    assertThat(status(8)).isEqualTo(ArchiveStatus.DONE);
  }
}

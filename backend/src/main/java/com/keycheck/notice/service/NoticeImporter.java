package com.keycheck.notice.service;

import com.keycheck.image.config.StorageConfig;
import com.keycheck.image.storage.StorageUnavailableException;
import com.keycheck.notice.domain.ArchiveStatus;
import com.keycheck.notice.domain.NoticeAttachment;
import com.keycheck.notice.domain.NoticeOrigin;
import com.keycheck.notice.dto.DiscordAttachment;
import com.keycheck.notice.dto.DiscordMessage;
import com.keycheck.notice.repository.NoticeAttachmentRepository;
import com.keycheck.post.domain.KoreanVendor;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * 손으로 옮겨 변환해 둔 지난 공지 묶음(messages.json + attachments/)을 봇과 같은 길(수집 + 보관)로 넣는다. 요약은 평소 요약 작업이
 * 나중에 한다. 이미 있는 공지는 다시 넣지 않고, 보관이 덜 끝난 첨부만 이어서 보관하므로 여러 번 실행해도 안전하다. 로그에는 공지 내용을 싣지
 * 않는다.
 */
@Slf4j
@Service
@Conditional(StorageConfig.Configured.class)
@RequiredArgsConstructor
public class NoticeImporter {
  private final NoticeIngestService ingestService;
  private final NoticeArchiver archiver;
  private final NoticeAttachmentRepository attachmentRepository;

  private final JsonMapper mapper = JsonMapper.builder().build();

  public ImportReport importDir(Path dir, KoreanVendor vendor) {
    List<JsonNode> nodes = new ArrayList<>();
    mapper.readTree(dir.resolve("messages.json")).forEach(nodes::add);
    nodes.sort(Comparator.comparingLong(node -> Long.parseLong(node.path("id").asString())));

    int imported = 0;
    int skipped = 0;
    int stored = 0;
    int failed = 0;
    try {
      for (JsonNode node : nodes) {
        DiscordMessage message = DiscordMessage.from(node);
        long channelId = Long.parseLong(message.getChannelId());
        if (ingestService.ingest(vendor, channelId, message, NoticeOrigin.BACKFILL).isPresent()) {
          imported++;
        } else {
          skipped++;
        }
        for (DiscordAttachment attachment : message.getAttachments()) {
          Optional<ArchiveStatus> result = archive(dir, node, attachment);
          if (result.isEmpty()) {
            continue;
          }
          if (result.get() == ArchiveStatus.DONE) {
            stored++;
          } else {
            failed++;
          }
        }
      }
    } catch (StorageUnavailableException e) {
      log.error(
          "저장소에 올리지 못해 가져오기를 멈춘다. 저장소를 확인한 뒤 다시 실행하면 이어서 끝낸다: imported={}, skipped={}",
          imported,
          skipped);
      return new ImportReport(imported, skipped, stored, failed, true);
    }
    return new ImportReport(imported, skipped, stored, failed, false);
  }

  /** @return 이번에 보관을 시도해 끝난 상태. 이미 DONE이거나 행이 없으면 empty */
  private Optional<ArchiveStatus> archive(Path dir, JsonNode message, DiscordAttachment attachment) {
    long id = Long.parseLong(attachment.getId());
    Optional<NoticeAttachment> row = attachmentRepository.findById(id);
    if (row.isEmpty() || row.get().getArchiveStatus() == ArchiveStatus.DONE) {
      return Optional.empty();
    }
    byte[] original = read(dir, localFile(message, attachment.getId()));
    if (original == null) {
      log.warn("가져올 첨부 파일이 없어 실패로 둔다: attachment={}", id);
      attachmentRepository.markArchived(id, ArchiveStatus.FAILED);
      return Optional.of(ArchiveStatus.FAILED);
    }
    archiver.store(id, original);
    return attachmentRepository.findById(id).map(NoticeAttachment::getArchiveStatus);
  }

  private static String localFile(JsonNode message, String attachmentId) {
    for (JsonNode a : message.path("attachments")) {
      if (attachmentId.equals(a.path("id").asString())) {
        JsonNode file = a.get("local_file");
        return file == null || file.isNull() ? null : file.asString();
      }
    }
    return null;
  }

  /** 묶음의 attachments 폴더 안 파일만 읽는다. 없거나 읽지 못하면 null */
  private static byte[] read(Path dir, String localFile) {
    if (localFile == null || localFile.isBlank()) {
      return null;
    }
    Path base = dir.resolve("attachments").toAbsolutePath().normalize();
    Path file = base.resolve(localFile).normalize();
    if (!file.startsWith(base) || !Files.isRegularFile(file)) {
      return null;
    }
    try {
      return Files.readAllBytes(file);
    } catch (IOException e) {
      return null;
    }
  }
}

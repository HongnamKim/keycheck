package com.keycheck.notice.service;

import com.keycheck.image.client.ImageDownloader;
import com.keycheck.image.client.ImageFetchException;
import com.keycheck.image.config.StorageConfig;
import com.keycheck.image.service.ImageSize;
import com.keycheck.image.service.ThumbnailMaker;
import com.keycheck.image.service.UnreadableImageException;
import com.keycheck.image.storage.ThumbStorage;
import com.keycheck.notice.client.DiscordClient;
import com.keycheck.notice.domain.ArchiveStatus;
import com.keycheck.notice.domain.Notice;
import com.keycheck.notice.domain.NoticeAttachment;
import com.keycheck.notice.domain.NoticeKeys;
import com.keycheck.notice.dto.DiscordAttachment;
import com.keycheck.notice.repository.NoticeAttachmentRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Conditional;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;

/**
 * 공지 첨부를 받자마자 WebP 세 크기(2048·1280·640px)로 저장소에 올린다. 디스코드 첨부 주소는 하루쯤 지나면 만료되기 때문이다. 요약 작업은
 * 첨부가 모두 DONE이나 FAILED가 된 공지만 요약하므로, 어떤 첨부도 PENDING으로 남겨 두지 않는다. 저장소 설정이 있을 때만 만든다.
 */
@Slf4j
@Service
@Conditional(StorageConfig.Configured.class)
@RequiredArgsConstructor
public class NoticeArchiver {
  /** 이만큼 보관하지 못한 첨부는 FAILED로 끝낸다 */
  static final int MAX_FAILURES = 3;

  /** 저장소에 올릴 수 없을 때 다음 시도까지 쉬는 시간 */
  static final Duration ERROR_PAUSE = Duration.ofMinutes(1);

  private static final int[] WIDTHS = {
    ThumbnailMaker.ARCHIVE_WIDTH, ThumbnailMaker.LARGE_WIDTH, ThumbnailMaker.SMALL_WIDTH
  };

  private final NoticeAttachmentRepository attachmentRepository;
  private final ImageDownloader downloader;
  private final ThumbnailMaker maker;
  private final ThumbStorage storage;
  private final DiscordClient discordClient;
  private final Clock clock;

  private Instant pausedUntil = Instant.MIN;

  /**
   * 보관하지 못한 첨부 하나를 받아 올린다. 실패는 시도 전에 먼저 세고(ThumbnailWorker와 같은 이유), 이미지 탓이 아니면 되돌린다.
   *
   * @return 대상이 있었으면 true (쉬는 중이면 false)
   */
  public boolean archiveNext() {
    if (clock.instant().isBefore(pausedUntil)) {
      return false;
    }
    List<NoticeAttachment> targets =
        attachmentRepository.findArchiveTargets(MAX_FAILURES, Limit.of(1));
    if (targets.isEmpty()) {
      return false;
    }
    NoticeAttachment attachment = targets.getFirst();
    long id = attachment.getId();
    if (attachment.getSourceUrl() == null) {
      // 가져온 공지의 첨부는 가져오기가 store로 직접 보관한다. 받을 주소가 없으면 끝낸 것으로 본다.
      log.warn("받을 주소가 없어 보관을 실패로 끝낸다: attachment={}", id);
      attachmentRepository.markArchived(id, ArchiveStatus.FAILED);
      return true;
    }
    attachmentRepository.incrementFailures(id);
    boolean imageFault = false;
    try {
      store(id, downloader.download(attachment.getSourceUrl()));
    } catch (ImageFetchException e) {
      imageFault = true;
      int failures = attachment.getFailures() + 1;
      log.warn("첨부를 받지 못했다({}/{}): attachment={}", failures, MAX_FAILURES, id, e);
      if (failures >= MAX_FAILURES) {
        attachmentRepository.markArchived(id, ArchiveStatus.FAILED);
      } else {
        refreshSourceUrl(attachment);
      }
    } catch (RuntimeException | Error e) {
      // 저장소 장애, WebP 인코더 설정 문제 등은 이미지 탓이 아니다
      log.error("첨부 보관 중 오류로 {} 쉰다: attachment={}", ERROR_PAUSE, id, e);
      pausedUntil = clock.instant().plus(ERROR_PAUSE);
    } finally {
      if (!imageFault) {
        attachmentRepository.decrementFailures(id);
      }
    }
    return true;
  }

  /**
   * 원본을 2048 → 1280 → 640px 순서로 올리고 모두 올라가면 DONE으로 한다. 읽을 수 없는 이미지(동영상 등)는 다시 해도 같으므로
   * FAILED로 끝낸다. 저장소 장애는 그대로 던진다.
   */
  public void store(long attachmentId, byte[] original) {
    ImageSize size;
    try {
      size = maker.originalSize(original);
      String[] keys = {
        NoticeKeys.archive(attachmentId),
        NoticeKeys.large(attachmentId),
        NoticeKeys.thumb(attachmentId)
      };
      for (int i = 0; i < WIDTHS.length; i++) {
        storage.put(keys[i], maker.make(original, WIDTHS[i]));
      }
    } catch (UnreadableImageException e) {
      log.warn("읽을 수 없는 첨부라 보관을 실패로 끝낸다: attachment={}", attachmentId, e);
      attachmentRepository.markArchived(attachmentId, ArchiveStatus.FAILED);
      return;
    }
    attachmentRepository.markArchivedWithSize(attachmentId, size.getWidth(), size.getHeight());
  }

  /** 봇이 받은 공지면 디스코드에서 새 첨부 주소를 받아 둔다. 못 받으면 그대로 실패로 센다. */
  private void refreshSourceUrl(NoticeAttachment attachment) {
    Notice notice = attachment.getNotice();
    if (notice.getChannelMessageId() == null) {
      return;
    }
    try {
      discordClient
          .message(String.valueOf(notice.getChannelId()), String.valueOf(notice.getChannelMessageId()))
          .flatMap(
              message ->
                  message.getAttachments().stream()
                      .filter(a -> String.valueOf(attachment.getId()).equals(a.getId()))
                      .findFirst())
          .map(DiscordAttachment::getUrl)
          .ifPresent(url -> attachmentRepository.updateSourceUrl(attachment.getId(), url));
    } catch (RuntimeException e) {
      log.warn("새 첨부 주소를 받지 못했다: attachment={}", attachment.getId(), e);
    }
  }
}

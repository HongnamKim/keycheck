package com.keycheck.image.service;

import com.keycheck.crawl.client.RequestGate;
import com.keycheck.crawl.config.CollectProperties;
import com.keycheck.image.client.ImageDownloader;
import com.keycheck.image.client.ImageFetchException;
import com.keycheck.image.config.StorageConfig;
import com.keycheck.image.domain.ImageSource;
import com.keycheck.image.storage.StorageUnavailableException;
import com.keycheck.image.storage.ThumbStorage;
import com.keycheck.post.domain.Post;
import com.keycheck.post.domain.PostImage;
import com.keycheck.post.repository.PostImageRepository;
import java.net.URI;
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
 * 상세 갤러리에 보이는 이미지를 하나씩 WebP 축소본으로 만들어 저장소에 올린다 (설계 3.2). DB에는 객체 이름만 둔다. 저장소 설정이 있을
 * 때만 만든다.
 *
 * <p>원본 받기는 트랜잭션 밖에서 하고, 저장은 축소본 컬럼만 바꿔 그사이 다른 쓰기를 덮지 않는다.
 */
@Slf4j
@Service
@Conditional(StorageConfig.Configured.class)
@RequiredArgsConstructor
public class ThumbnailWorker {
  /** 이만큼 만들지 못한 이미지는 더 시도하지 않는다. 화면은 원본을 쓴다. */
  static final int MAX_FAILURES = 3;

  /** 저장소에 올릴 수 없을 때(접속·인증·서버 오류) 다음 시도까지 쉬는 시간 */
  static final Duration ERROR_PAUSE = Duration.ofMinutes(1);

  private final PostImageRepository imageRepository;
  private final ImageDownloader downloader;
  private final ThumbnailMaker maker;
  private final ThumbStorage storage;
  private final RequestGate gate;
  private final CollectProperties collectProperties;
  private final Clock clock;

  private Instant pausedUntil = Instant.MIN;

  /**
   * 대상 중 맨 앞 이미지(본문을 늦게 채운 글부터, 글 안에서는 대표 이미지 먼저) 하나의 축소본을 만든다. 대표 이미지는 1280px 축소본도
   * 함께 만들고 둘 다 올라가야 완료다(1280px만 만들 수 없는 긴 이미지는 640px만으로 완료). geekhack 이미지는 빈 요청 간격이 없으면
   * 그대로 둔다. 원본을 받지 못하거나 읽을 수 없으면 실패 횟수를 늘리고, 저장소에 올릴 수 없거나 예상하지 못한 오류(설정 문제 등)면 상태를
   * 그대로 두고 ERROR_PAUSE 동안 쉰다.
   *
   * <p>실패는 시도 전에 먼저 세고 이미지 탓이 아니면 되돌린다. 원본을 펼치다 프로세스가 죽으면(힙 밖 메모리 부족, 네이티브 오류) 셀
   * 기회가 없어, 다시 뜨자마자 같은 이미지를 골라 또 죽기 때문이다. 이렇게 하면 MAX_FAILURES번 죽은 뒤에는 그 이미지를 고르지 않는다.
   *
   * @return 대상이 있었으면 true (쉬는 중이면 false)
   */
  public boolean makeNext() {
    if (clock.instant().isBefore(pausedUntil)) {
      return false;
    }
    List<PostImage> targets =
        imageRepository.findThumbTargets(
            collectProperties.getSinceTopicId(), MAX_FAILURES, Limit.of(1));
    if (targets.isEmpty()) {
      return false;
    }
    PostImage image = targets.getFirst();
    Post post = image.getPost();
    if (isGeekhack(image) && !gate.tryAcquireIdle()) {
      return true;
    }
    imageRepository.incrementThumbFailures(post.getId(), image.getUrl());
    boolean imageFault = false;
    try {
      byte[] original = downloader.download(image.getUrl());
      byte[] small = maker.make(original, ThumbnailMaker.SMALL_WIDTH);
      boolean representative = image.getId().equals(post.getRepresentativeImageId());
      // 큰 축소본을 먼저 저장해 "대표 이미지에 thumb_key가 있으면 thumb_large_key도 있다"를 지킨다
      // (1280px만 만들 수 없는 긴 이미지는 빼고)
      byte[] large = representative ? large(image, original) : null;
      if (large != null) {
        String largeKey = "thumbs/" + image.getId() + "-1280.webp";
        storage.put(largeKey, large);
        imageRepository.setThumbLargeKey(image.getId(), largeKey);
      }
      String key = "thumbs/" + image.getId() + ".webp";
      storage.put(key, small);
      // 같은 글의 같은 주소 행도 이 축소본을 쓴다(대표 행이 먼저 처리되므로 1280px는 대표 행에만 생긴다)
      imageRepository.markThumbs(post.getId(), image.getUrl(), key);
    } catch (ImageFetchException | UnreadableImageException e) {
      imageFault = true;
      log.warn(
          "축소본을 만들지 못했다({}/{}): image={}, url={}",
          image.getThumbFailures() + 1,
          MAX_FAILURES,
          image.getId(),
          image.getUrl(),
          e);
    } catch (StorageUnavailableException e) {
      log.error("축소본 저장소를 쓸 수 없어 {} 쉰다: image={}", ERROR_PAUSE, image.getId(), e);
      pausedUntil = clock.instant().plus(ERROR_PAUSE);
    } catch (RuntimeException | Error e) {
      // WebP 네이티브 라이브러리를 읽지 못하는 배포 환경처럼 이미지 탓이 아닐 수 있다. 실패로 세면 모든 이미지가 빠지고,
      // 쉬지 않으면 같은 원본을 실행 간격마다 다시 받는다.
      log.error("축소본 작업 중 예상하지 못한 오류로 {} 쉰다: image={}", ERROR_PAUSE, image.getId(), e);
      pausedUntil = clock.instant().plus(ERROR_PAUSE);
    } finally {
      if (!imageFault) {
        imageRepository.decrementThumbFailures(post.getId(), image.getUrl());
      }
    }
    return true;
  }

  /**
   * 대표 이미지의 1280px 축소본. 640px는 이미 만들었으므로 원본은 읽을 수 있고, 1280px만 만들 수 없으면(WebP 세로 한계를 넘는 긴 이미지
   * 등) 크기 탓이라 null을 돌려준다. 그때 카드·하단 목록은 640px를, 상세 맨 위는 원본을 쓴다.
   */
  private byte[] large(PostImage image, byte[] original) {
    try {
      return maker.make(original, ThumbnailMaker.LARGE_WIDTH);
    } catch (UnreadableImageException e) {
      log.info("1280px 축소본은 만들 수 없어 640px만 만든다: image={}, url={}", image.getId(), image.getUrl(), e);
      return null;
    }
  }

  /** geekhack 첨부이거나 geekhack에 있는 이미지. geekhack 요청 간격을 거친다. */
  private static boolean isGeekhack(PostImage image) {
    if (image.getSource() == ImageSource.ATTACHMENT) {
      return true;
    }
    try {
      return ImageDownloader.isGeekhack(URI.create(image.getUrl()));
    } catch (IllegalArgumentException e) {
      return false; // 받기도 실패해 실패로 센다
    }
  }
}

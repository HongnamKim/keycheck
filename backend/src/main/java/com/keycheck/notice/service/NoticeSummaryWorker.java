package com.keycheck.notice.service;

import com.anthropic.errors.AnthropicException;
import com.anthropic.errors.AnthropicInvalidDataException;
import com.anthropic.errors.AnthropicServiceException;
import com.keycheck.image.service.ThumbUrls;
import com.keycheck.notice.config.NoticeProperties;
import com.keycheck.notice.domain.ArchiveStatus;
import com.keycheck.notice.domain.Notice;
import com.keycheck.notice.domain.NoticeAttachment;
import com.keycheck.notice.domain.NoticeKeys;
import com.keycheck.notice.domain.SaleItem;
import com.keycheck.notice.domain.SaleLink;
import com.keycheck.notice.dto.NoticeImage;
import com.keycheck.notice.dto.NoticeSource;
import com.keycheck.notice.dto.NoticeSummaryResult;
import com.keycheck.notice.dto.SaleItemDraft;
import com.keycheck.notice.repository.NoticeAttachmentRepository;
import com.keycheck.notice.repository.NoticeRepository;
import com.keycheck.notice.repository.SaleItemRepository;
import com.keycheck.post.domain.SummaryStatus;
import com.keycheck.summary.service.DailySummaryBudget;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 올라온 지 wait가 지나고 사진 보관이 끝난 글 공지를 하나씩 판매 건으로 요약한다. 요약기 호출은 트랜잭션 밖에서 하고, 결과는 한
 * 트랜잭션에서 저장한다. 다시 요약하면 상품 이름이 같은 기존 판매 건은 id를 두고 내용만 바꿔 공유된 주소가 이어지게 한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NoticeSummaryWorker {
  /** API를 쓸 수 없을 때(인증·설정·과부하·연결 오류) 다음 시도까지 쉬는 시간 */
  static final Duration ERROR_PAUSE = Duration.ofMinutes(1);

  /** [글자](주소) 또는 맨 주소 */
  private static final Pattern LINK =
      Pattern.compile("\\[([^\\]]+)]\\((https?://[^)\\s]+)\\)|(https?://[^\\s<>)\\]]+)");

  /** 유니코드 공백 (줄 바꿈 없는 공백·전각 공백 포함) */
  private static final Pattern WHITESPACE =
      Pattern.compile("\\s+", Pattern.UNICODE_CHARACTER_CLASS);

  private final NoticeRepository noticeRepository;
  private final NoticeAttachmentRepository attachmentRepository;
  private final SaleItemRepository saleItemRepository;
  private final NoticeSummarizer summarizer;
  private final NoticeProperties properties;
  private final DailySummaryBudget budget;
  private final ThumbUrls thumbUrls;
  private final TransactionTemplate transaction;
  private final Clock clock;

  private Instant pausedUntil = Instant.MIN;

  /**
   * 요약할 글 공지 하나를 요약한다. 글 때문에 요약할 수 없으면 FAILED로 두고 다시 시도하지 않는다. API를 쓸 수 없으면 상태를 그대로 두고
   * ERROR_PAUSE 동안 쉰다. Claude 요약기를 부른 횟수가 하루 상한에 닿았으면 다음 날까지 쉰다. 가짜 요약기는 세지 않는다. 요약하는 동안
   * 딸린 메시지의 사진이 새로 생기면 저장하지 않고 요약 대기로 두어 다음 실행에서 사진을 모두 넣어 다시 요약한다.
   *
   * @return 대상이 있었으면 true (쉬는 중이거나 하루 상한에 닿았으면 false)
   */
  public boolean summarizeNext() {
    Instant now = clock.instant();
    if (now.isBefore(pausedUntil)) {
      return false;
    }
    List<Notice> targets =
        noticeRepository.findSummaryTargets(now.minus(properties.getSummary().getWait()), Limit.of(1));
    if (targets.isEmpty()) {
      return false;
    }
    Notice notice = targets.get(0);
    boolean claude = !(summarizer instanceof FakeNoticeSummarizer);
    if (claude && !budget.tryAcquire()) {
      pausedUntil = budget.nextReset();
      return false;
    }
    List<NoticeAttachment> attachments = attachmentRepository.findWithChildren(notice.getId());
    Set<Long> attachmentIds = ids(attachments);
    NoticeSource source =
        new NoticeSource(
            notice.getVendor(),
            notice.getPostedAt(),
            notice.getContent(),
            links(notice.getContent()),
            images(attachments));
    NoticeSummaryResult result;
    try {
      result = summarizer.summarize(source);
    } catch (RuntimeException e) {
      if (isApiUnavailable(e)) {
        log.error("공지 요약 API를 쓸 수 없어 {} 쉰다: notice={}", ERROR_PAUSE, notice.getId(), e);
        pausedUntil = clock.instant().plus(ERROR_PAUSE);
        // 오류 응답은 과금되지 않으니 하루 상한에서 되돌린다. 연결 실패·시간 초과는 과금됐을 수 있어 센다
        if (claude && e instanceof AnthropicServiceException) {
          budget.release();
        }
      } else {
        log.warn("공지 요약 실패: notice={}", notice.getId(), e);
        noticeRepository.updateSummaryStatus(notice.getId(), SummaryStatus.FAILED);
      }
      return true;
    }
    try {
      Boolean saved = transaction.execute(status -> save(notice.getId(), attachmentIds, result));
      if (!Boolean.TRUE.equals(saved)) {
        log.info("요약하는 사이 사진이 더해져 저장하지 않고 다시 요약한다: notice={}", notice.getId());
      }
    } catch (RuntimeException e) {
      if (isTransient(e)) {
        // 저장소 문제는 공지 탓이 아니니 요약 대기로 두고 쉰다 (같은 공지를 매번 다시 요약해 한도를 쓰지 않게)
        log.error("공지 요약 결과를 저장하지 못해 {} 쉰다: notice={}", ERROR_PAUSE, notice.getId(), e);
        pausedUntil = clock.instant().plus(ERROR_PAUSE);
      } else {
        log.warn("공지 요약 결과를 저장하지 못해 실패로 둔다: notice={}", notice.getId(), e);
        noticeRepository.updateSummaryStatus(notice.getId(), SummaryStatus.FAILED);
      }
    }
    return true;
  }

  private boolean save(Long noticeId, Set<Long> attachmentIds, NoticeSummaryResult result) {
    if (!attachmentIds.equals(ids(attachmentRepository.findWithChildren(noticeId)))) {
      return false;
    }
    Notice notice = noticeRepository.findById(noticeId).orElseThrow();
    List<SaleItem> existing = saleItemRepository.findByNoticeId(noticeId);
    Map<String, Deque<SaleItem>> byName = new HashMap<>();
    for (SaleItem item : existing) {
      String key = nameKey(item.getProductName());
      if (key != null) {
        byName.computeIfAbsent(key, k -> new ArrayDeque<>()).add(item);
      }
    }
    Set<SaleItem> kept = new HashSet<>();
    List<SaleItemDraft> drafts = result.getItems();
    for (int i = 0; i < drafts.size(); i++) {
      Deque<SaleItem> same = byName.get(nameKey(drafts.get(i).getProductName()));
      SaleItem match = same == null ? null : same.poll();
      if (match != null) {
        match.update(i, drafts.get(i));
        kept.add(match);
      } else {
        saleItemRepository.save(SaleItem.create(notice, i, drafts.get(i)));
      }
    }
    existing.stream().filter(item -> !kept.contains(item)).forEach(saleItemRepository::delete);
    noticeRepository.markSummarized(
        noticeId, SummaryStatus.DONE, result.getSummarizedBy(), clock.instant());
    return true;
  }

  /**
   * 다시 요약할 때 기존 판매 건과 짝짓는 상품 이름 키: 공백을 모두 빼고 대소문자를 무시한다. 이름이 없거나 공백뿐이면 null(짝짓지 않음)
   */
  private static String nameKey(String productName) {
    if (productName == null) {
      return null;
    }
    String key = WHITESPACE.matcher(productName).replaceAll("").toLowerCase(Locale.ROOT);
    return key.isEmpty() ? null : key;
  }

  private static Set<Long> ids(List<NoticeAttachment> attachments) {
    return attachments.stream().map(NoticeAttachment::getId).collect(Collectors.toSet());
  }

  private List<NoticeImage> images(List<NoticeAttachment> attachments) {
    return attachments.stream()
        .filter(a -> a.getArchiveStatus() == ArchiveStatus.DONE)
        .map(
            a ->
                new NoticeImage(
                    a.getId(), a.getFilename(), thumbUrls.url(NoticeKeys.thumb(a.getId()))))
        .toList();
  }

  /** 본문 링크, 본문 순서. [글자](주소)는 {글자, 주소}, 맨 주소는 {주소, 주소}. 같은 주소는 처음 것만 */
  static List<SaleLink> links(String content) {
    Map<String, SaleLink> links = new LinkedHashMap<>();
    if (content == null) {
      return new ArrayList<>();
    }
    Matcher matcher = LINK.matcher(content);
    while (matcher.find()) {
      if (matcher.group(2) != null) {
        links.putIfAbsent(matcher.group(2), new SaleLink(matcher.group(1), matcher.group(2)));
      } else {
        String url = matcher.group(3).replaceAll("[.,;!?]+$", "");
        links.putIfAbsent(url, new SaleLink(url, url));
      }
    }
    return new ArrayList<>(links.values());
  }

  /** 저장소 연결·일시 장애. 그 밖의 저장 오류는 요약 결과(데이터) 탓으로 본다. */
  private static boolean isTransient(RuntimeException e) {
    return e instanceof TransientDataAccessException
        || e instanceof DataAccessResourceFailureException
        || e instanceof CannotCreateTransactionException;
  }

  /** 글과 상관없는 SDK 오류. 응답을 읽지 못한 경우는 글 탓으로 본다. */
  private static boolean isApiUnavailable(RuntimeException e) {
    return e instanceof AnthropicException && !(e instanceof AnthropicInvalidDataException);
  }
}

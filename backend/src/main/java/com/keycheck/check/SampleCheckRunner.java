package com.keycheck.check;

import com.keycheck.classify.service.Classifier;
import com.keycheck.crawl.client.GeekhackClient;
import com.keycheck.crawl.client.RequestGate;
import com.keycheck.crawl.dto.RawImage;
import com.keycheck.crawl.dto.TopicPage;
import com.keycheck.crawl.dto.TopicStats;
import com.keycheck.crawl.parser.TopicParser;
import com.keycheck.image.dto.Selection;
import com.keycheck.image.service.RepresentativeImageSelector;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.jsoup.Jsoup;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * 샘플 글을 실제 geekhack에서 읽어 분류와 대표 이미지 선택 결과를 출력한다 (구현 계획 Task 12). 실행기는 DB를
 * 읽거나 쓰지 않는다.
 *
 * <p>실행: {@code ./gradlew bootRun --args='--spring.profiles.active=check [글 ID ...]'}. 글 ID를 주지
 * 않으면 샘플 8건을 읽는다. check 프로필에서도 DB 연결과 Flyway 마이그레이션은 켜져 있으므로 먼저 {@code docker
 * compose up -d}로 PostgreSQL을 띄운다.
 */
@Component
@Profile("check")
@RequiredArgsConstructor
public class SampleCheckRunner implements ApplicationRunner {
  /** 키보드 4건(120481, 127115, 127131, 127200), 키캡 4건(126608, 126906, 126959, 127212) */
  private static final List<Long> SAMPLE_TOPIC_IDS =
      List.of(120481L, 127115L, 127131L, 127200L, 126608L, 126906L, 126959L, 127212L);

  private final GeekhackClient client;
  private final RequestGate gate;
  private final TopicParser topicParser;
  private final Classifier classifier;
  private final RepresentativeImageSelector imageSelector;

  @Override
  public void run(ApplicationArguments args) {
    List<Long> topicIds =
        args.getNonOptionArgs().isEmpty()
            ? SAMPLE_TOPIC_IDS
            : args.getNonOptionArgs().stream().map(Long::parseLong).toList();
    for (long topicId : topicIds) {
      try {
        check(topicId);
      } catch (RuntimeException e) {
        System.out.println("=== " + topicId + " 실패: " + e);
      }
      System.out.println();
    }
  }

  private void check(long topicId) {
    gate.acquire();
    String html = client.fetchTopic(topicId);
    TopicPage page = topicParser.parseFirstPage(html);
    TopicStats stats = topicParser.parseStats(html);
    String title = Jsoup.parse(html).title();
    Selection selection = imageSelector.select(page.getImages());
    List<String> reasons = selection.getExcludedReasons();
    long excluded = reasons.stream().filter(reason -> reason != null).count();

    System.out.println("=== " + topicId + " ===");
    System.out.println("제목: " + title);
    System.out.println("분류: " + classifier.classify(title, page.getBodyText()));
    // 첫 페이지만 읽으므로 댓글 수는 첫 페이지의 마지막 Reply 번호다 (여러 페이지 글은 실제보다 작다)
    System.out.println("조회수: " + stats.getViews() + ", 댓글 수(첫 페이지 기준): " + stats.getReplies());
    System.out.println(
        "대표 이미지: "
            + (selection.getRepresentativeIndex() == null
                ? "없음"
                : "[" + selection.getRepresentativeIndex() + "] "
                    + page.getImages().get(selection.getRepresentativeIndex()).getUrl()));
    System.out.println("이미지 " + page.getImages().size() + "개 (제외 " + excluded + "개)");
    for (int i = 0; i < page.getImages().size(); i++) {
      RawImage image = page.getImages().get(i);
      String reason = reasons.get(i) == null ? "후보" : "제외:" + reasons.get(i);
      System.out.printf(
          "  [%d] %-10s %-17s %s%n", image.getPosition(), image.getSource(), reason, image.getUrl());
    }
  }
}

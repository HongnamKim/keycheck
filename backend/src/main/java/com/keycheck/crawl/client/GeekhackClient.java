package com.keycheck.crawl.client;

import java.net.URI;
import java.nio.charset.Charset;
import lombok.RequiredArgsConstructor;
import org.springframework.web.client.RestClient;

/** geekhack HTML을 받아오는 클라이언트. 간격 제한은 직접 쓰지 않는다. 호출자가 RequestGate를 통과한 뒤 부른다. */
@RequiredArgsConstructor
public class GeekhackClient {
  private static final int BOARD_ID = 132;
  private static final int TOPICS_PER_PAGE = 50;

  /** 페이지는 ISO-8859-1로 선언하지만 실제 바이트는 Windows-1252다 (docs/geekhack-html-notes.md) */
  private static final Charset RESPONSE_CHARSET = Charset.forName("windows-1252");

  private final RestClient restClient;

  /**
   * 기본 정렬(마지막 댓글순) 목록. 새 글과 댓글이 달린 글이 앞에 온다.
   *
   * @param page 0부터
   */
  public String fetchBoardPage(int page) {
    return get("/index.php?board=" + BOARD_ID + "." + page * TOPICS_PER_PAGE);
  }

  /**
   * 작성순(글 번호 내림차순) 목록. 기본 정렬은 쪽 경계가 계속 바뀌어 과거 글을 차례로 받을 수 없다.
   *
   * @param page 0부터
   */
  public String fetchBoardPageByFirstPost(int page) {
    return get(
        "/index.php?board=" + BOARD_ID + "." + page * TOPICS_PER_PAGE + ";sort=first_post;desc");
  }

  public String fetchTopic(long topicId) {
    return get("/index.php?topic=" + topicId + ".0");
  }

  /** 오프셋을 글 수보다 크게 주면 geekhack이 마지막 페이지를 돌려준다 */
  public String fetchTopicLastPage(long topicId) {
    return get("/index.php?topic=" + topicId + ".999999");
  }

  /** URI 템플릿으로 넘기면 쿼리 값 안의 '='가 %3D로 바뀌므로, 만든 주소를 그대로 보낸다 */
  private String get(String path) {
    byte[] bytes = restClient.get().uri(URI.create(path)).retrieve().body(byte[].class);
    return bytes == null ? "" : new String(bytes, RESPONSE_CHARSET);
  }
}

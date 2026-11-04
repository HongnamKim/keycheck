package com.keycheck.crawl.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.keycheck.post.domain.Board;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class GeekhackClientTest {
  private final RestClient.Builder builder =
      RestClient.builder()
          .baseUrl("https://geekhack.test")
          .defaultHeader(HttpHeaders.USER_AGENT, "keycheck-test");
  private final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
  private final GeekhackClient client = new GeekhackClient(builder.build());

  @Test
  @DisplayName("IC 게시판(132) 페이지를 기본 정렬로 요청하고 Windows-1252로 디코딩한다")
  void decodesBoardPageAsWindows1252() {
    // 0x97은 Windows-1252의 em dash, 0xE9는 é. ISO-8859-1로 읽으면 0x97이 제어 문자가 된다.
    byte[] body = {0x43, 0x61, 0x66, (byte) 0xE9, 0x20, (byte) 0x97, 0x20, 0x41};
    server
        .expect(requestTo("https://geekhack.test/index.php?board=132.50"))
        .andExpect(header(HttpHeaders.USER_AGENT, "keycheck-test"))
        .andRespond(withSuccess(body, MediaType.TEXT_HTML));

    assertThat(client.fetchBoardPage(Board.IC, 1)).isEqualTo("Café — A");
    server.verify();
  }

  @Test
  @DisplayName("작성순 IC 게시판 페이지는 최신 글 먼저 정렬을 붙여 요청한다")
  void requestsBoardPageByFirstPost() {
    server
        .expect(requestTo("https://geekhack.test/index.php?board=132.100;sort=first_post;desc"))
        .andRespond(withSuccess("<html></html>", MediaType.TEXT_HTML));

    assertThat(client.fetchBoardPageByFirstPost(Board.IC, 2)).isEqualTo("<html></html>");
    server.verify();
  }

  @Test
  @DisplayName("GB 게시판(70)은 게시판 번호만 바꿔 같은 주소 형식으로 요청한다")
  void requestsGbBoardPages() {
    server
        .expect(requestTo("https://geekhack.test/index.php?board=70.0"))
        .andRespond(withSuccess("<html>gb</html>", MediaType.TEXT_HTML));
    server
        .expect(requestTo("https://geekhack.test/index.php?board=70.50;sort=first_post;desc"))
        .andRespond(withSuccess("<html>gb first</html>", MediaType.TEXT_HTML));

    assertThat(client.fetchBoardPage(Board.GB, 0)).isEqualTo("<html>gb</html>");
    assertThat(client.fetchBoardPageByFirstPost(Board.GB, 1)).isEqualTo("<html>gb first</html>");
    server.verify();
  }

  @Test
  @DisplayName("글 페이지 주소로 요청한다")
  void requestsTopicUrl() {
    server
        .expect(requestTo("https://geekhack.test/index.php?topic=123456.0"))
        .andRespond(withSuccess("<html></html>", MediaType.TEXT_HTML));

    assertThat(client.fetchTopic(123456L)).isEqualTo("<html></html>");
    server.verify();
  }

  @Test
  @DisplayName("마지막 페이지는 아주 큰 오프셋으로 요청한다")
  void requestsLastPageWithLargeOffset() {
    server
        .expect(requestTo("https://geekhack.test/index.php?topic=123456.999999"))
        .andRespond(withSuccess("<html>last</html>", MediaType.TEXT_HTML));

    assertThat(client.fetchTopicLastPage(123456L)).isEqualTo("<html>last</html>");
    server.verify();
  }
}

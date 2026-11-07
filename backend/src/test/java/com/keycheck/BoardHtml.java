package com.keycheck;

import java.time.Instant;
import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * 테스트용 게시판 목록 HTML을 만든다. geekhack 목록의 구조만 흉내 냈고(fixtures/board-list.html 참고) 내용은 가짜다.
 *
 * <p>각 글: 제목 "[IC] Board {글 번호}", 작성자 maker, 댓글 1, 조회수 10, 마지막 게시물 시각
 * "Sun, 01 November 2026, 10:00:00"(시카고, {@link #LAST_POST_AT}). 표 위에 쪽 번호 목록이 있다.
 */
public final class BoardHtml {
  public static final Instant LAST_POST_AT = Instant.parse("2026-11-01T16:00:00Z");

  private BoardHtml() {}

  /**
   * @param page 쪽 번호 목록에 지금 쪽으로 보일 번호 (0부터)
   */
  public static String page(int page, long... topicIds) {
    String rows =
        Arrays.stream(topicIds)
            .mapToObj(
                id ->
                    """
                    <tr>
                      <td class="subject windowbg2"><div>
                        <span id="msg_%1$d"><a href="https://geekhack.test/index.php?PHPSESSID=abc&amp;topic=%1$d.0">[IC] Board %1$d</a></span>
                        <p>Started by <a href="https://geekhack.test/index.php?action=profile;u=1">maker</a></p>
                      </div></td>
                      <td class="stats windowbg">1 Replies<br />10 Views</td>
                      <td class="lastpost windowbg2">
                        <a href="https://geekhack.test/index.php?topic=%1$d.0#new"><img src="https://cdn.geekhack.test/last_post.gif" alt="Last post" /></a>
                        Sun, 01 November 2026, 10:00:00<br />
                        by <a href="https://geekhack.test/index.php?action=profile;u=2">replier</a>
                      </td>
                    </tr>
                    """
                        .formatted(id))
            .collect(Collectors.joining());
    return "<div class=\"pagelinks floatleft\">Pages: [<strong>"
        + (page + 1)
        + "</strong>]</div>"
        + "<div id=\"messageindex\"><table class=\"table_grid\"><tbody>"
        + rows
        + "</tbody></table></div>";
  }
}

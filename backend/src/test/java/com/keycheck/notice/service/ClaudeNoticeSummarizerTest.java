package com.keycheck.notice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.models.beta.AnthropicBeta;
import com.anthropic.models.beta.messages.BetaOutputConfig;
import com.anthropic.models.beta.messages.MessageCreateParams;
import com.keycheck.notice.domain.SaleCategory;
import com.keycheck.notice.domain.SaleLink;
import com.keycheck.notice.domain.SalePhase;
import com.keycheck.notice.domain.SaleType;
import com.keycheck.notice.dto.NoticeImage;
import com.keycheck.notice.dto.NoticeSource;
import com.keycheck.notice.dto.NoticeSummaryResult;
import com.keycheck.notice.dto.SaleItemDraft;
import com.keycheck.post.domain.KoreanVendor;
import com.keycheck.summary.FakeAnthropicServer;
import com.keycheck.summary.config.ClaudeProperties;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** 실제 Anthropic API 대신 FakeAnthropicServer(127.0.0.1)에 요청한다 */
class ClaudeNoticeSummarizerTest {
  /** 2026-11-06 18:42 (금) KST */
  private static final Instant POSTED_AT = Instant.parse("2026-11-06T09:42:00Z");

  private static final SaleLink STORE = new SaleLink("판매 페이지", "https://shop.test/p/1");
  private static final SaleLink OFFICIAL = new SaleLink("공식 스토어", "https://shop.test/p/2/");

  private FakeAnthropicServer server;
  private AnthropicClient client;

  @BeforeEach
  void setUp() throws Exception {
    server = new FakeAnthropicServer();
    client = server.client(AnthropicOkHttpClient.builder().apiKey("sk-ant-test"));
  }

  @AfterEach
  void tearDown() {
    client.close();
    server.close();
  }

  private ClaudeNoticeSummarizer summarizer(String model) {
    return new ClaudeNoticeSummarizer(
        client, new ClaudeProperties(model, "low", 8192, Duration.ofMinutes(2), true));
  }

  private ClaudeNoticeSummarizer summarizer() {
    return summarizer("claude-opus-5-5");
  }

  private static NoticeSource source(
      String content, List<SaleLink> links, List<NoticeImage> images) {
    return new NoticeSource(KoreanVendor.SWAGKEYS, POSTED_AT, content, links, images);
  }

  private static NoticeSource source(List<SaleLink> links, List<NoticeImage> images) {
    return source("## Test Board 재입고\n구매는 금일 19시부터예요.", links, images);
  }

  /** text 블록 하나(앞에 생각 블록)를 담은 응답 */
  private static String response(String stopReason, String text) {
    return """
        {"id": "msg_1", "type": "message", "role": "assistant", "model": "claude-opus-5-5",
         "content": [{"type": "thinking", "thinking": "", "signature": "sig"},
                     {"type": "text", "text": %s}],
         "stop_reason": "%s", "stop_sequence": null,
         "usage": {"input_tokens": 1200, "output_tokens": 300}}
        """
        .formatted(JsonMapper.shared().writeValueAsString(text), stopReason);
  }

  /** 응답의 판매 건 하나. 기본값에 overrides(키, 값을 번갈아)를 덮어쓴다. 값 null은 JSON null이다. */
  private static Map<String, Object> item(Object... overrides) {
    Map<String, Object> item = new HashMap<>();
    item.put("productName", "Test Board");
    item.put("category", "KEYBOARD");
    item.put("saleType", "RESTOCK");
    item.put("phase", "OPEN");
    item.put("startDate", "2026-11-06");
    item.put("startTime", "19:00");
    item.put("endDate", null);
    item.put("endTime", null);
    item.put("shippingNote", null);
    item.put("summary", List.of("테스트 키보드를 다시 팔아요."));
    item.put("links", List.of());
    item.put("imageIds", List.of());
    for (int i = 0; i < overrides.length; i += 2) {
      item.put((String) overrides[i], overrides[i + 1]);
    }
    return item;
  }

  private static String items(List<Map<String, Object>> items) {
    return JsonMapper.shared().writeValueAsString(Map.of("items", items));
  }

  /** 판매 건 응답을 받아 요약한 결과 */
  private NoticeSummaryResult summarize(NoticeSource source, List<Map<String, Object>> items) {
    server.responseBody = response("end_turn", items(items));
    return summarizer().summarize(source);
  }

  private static Map<String, String> link(String text, String url) {
    return Map.of("text", text, "url", url);
  }

  /** 마지막 요청의 사용자 메시지 블록들 */
  private List<JsonNode> sentBlocks() {
    JsonNode content =
        JsonMapper.shared().readTree(server.requestBody).get("messages").get(0).get("content");
    return StreamSupport.stream(content.spliterator(), false).toList();
  }

  /** 마지막 요청의 사용자 메시지 텍스트 */
  private String sentText() {
    List<JsonNode> texts =
        sentBlocks().stream()
            .filter(block -> block.get("type").asString().equals("text"))
            .toList();
    assertThat(texts).hasSize(1);
    return texts.get(0).get("text").asString();
  }

  private List<JsonNode> sentImageBlocks() {
    return sentBlocks().stream()
        .filter(block -> block.get("type").asString().equals("image"))
        .toList();
  }

  private JsonNode sentSchema() {
    return JsonMapper.shared()
        .readTree(server.requestBody)
        .get("output_config")
        .get("format")
        .get("schema");
  }

  @Test
  @DisplayName("요청은 기존 요약기와 같은 모양이고, fallbacks는 Haiku가 아닐 때만 넣는다")
  void 요청은_기존_요약기와_같은_모양이다() {
    MessageCreateParams params = summarizer().params(source(List.of(), List.of()));

    assertThat(params.model().asString()).isEqualTo("claude-opus-5-5");
    assertThat(params.maxTokens()).isEqualTo(8192);
    BetaOutputConfig outputConfig = params.outputConfig().orElseThrow();
    assertThat(outputConfig.effort()).contains(BetaOutputConfig.Effort.LOW);
    assertThat(outputConfig.format()).contains(ClaudeNoticeSummarizer.FORMAT);
    assertThat(params.system().orElseThrow().string())
        .contains(ClaudeNoticeSummarizer.SYSTEM_PROMPT);
    assertThat(params.thinking()).isEmpty();
    assertThat(params._additionalBodyProperties()).isEmpty();
    assertThat(params.messages()).hasSize(1);
    assertThat(params.fallbacks().orElseThrow().isDefault()).isTrue();
    assertThat(params.betas().orElseThrow())
        .containsExactly(AnthropicBeta.SERVER_SIDE_FALLBACK_2026_07_01);

    MessageCreateParams haiku =
        summarizer("claude-haiku-5-5").params(source(List.of(), List.of()));

    assertThat(haiku.fallbacks()).isEmpty();
    assertThat(haiku.betas()).isEmpty();
  }

  @Test
  @DisplayName("시간 표기를 한국 시각 글자로 바꿔 보낸다")
  void 시간_표기를_한국_시각_글자로_바꿔_보낸다() {
    summarize(
        source(
            "구매는 <t:1793961000:F> 부터\n마감은 <t:1794045600:R>\n시작 <t:1793961000>",
            List.of(),
            List.of()),
        List.of());

    assertThat(sentText())
        .contains("<posted_at>\n2026-11-06 18:42 (금) KST\n</posted_at>")
        .contains("구매는 2026-11-06 19:30 (금) KST 부터")
        .contains("마감은 2026-11-07 19:00 (토) KST")
        .contains("시작 2026-11-06 19:30 (금) KST")
        .doesNotContain("<t:");
  }

  @Test
  @DisplayName("판매처·본문·링크 목록·첨부 목록을 보내고, 첨부 순번은 붙인 사진의 차례다")
  void 판매처와_본문과_링크와_첨부_목록을_보낸다() {
    summarize(
        source(
            List.of(STORE, OFFICIAL),
            List.of(
                new NoticeImage(7L, "a.png", null),
                new NoticeImage(8L, "Test Board.jpg", "https://img.test/notices/8-640.webp"))),
        List.of());

    assertThat(sentText())
        .startsWith("<vendor>\nSWAGKEYS\n</vendor>\n<posted_at>\n")
        .contains("<body>\n## Test Board 재입고\n구매는 금일 19시부터예요.\n</body>")
        .contains(
            "<links>\n판매 페이지 | https://shop.test/p/1\n공식 스토어 | https://shop.test/p/2/\n</links>")
        .contains("<images>\n- | 7 | a.png\n1 | 8 | Test Board.jpg\n</images>");
  }

  @Test
  @DisplayName("사진을 주소 블록으로 보낸다")
  void 사진을_주소_블록으로_보낸다() {
    summarize(
        source(
            List.of(),
            List.of(new NoticeImage(7L, "a.png", "https://img.test/notices/7-640.webp"))),
        List.of());

    assertThat(sentImageBlocks())
        .containsExactly(
            JsonMapper.shared()
                .readTree(
                    "{\"type\":\"image\",\"source\":{\"type\":\"url\","
                        + "\"url\":\"https://img.test/notices/7-640.webp\"}}"));
  }

  @Test
  @DisplayName("첨부는 앞의 20개만 보낸다")
  void 첨부는_앞의_20개만_보낸다() {
    List<NoticeImage> images =
        IntStream.rangeClosed(1, 21)
            .mapToObj(
                i ->
                    new NoticeImage(
                        i, i + ".png", "https://img.test/notices/" + i + "-640.webp"))
            .toList();

    summarize(source(List.of(), images), List.of());

    assertThat(sentImageBlocks()).hasSize(20);
    assertThat(sentText()).contains("20 | 20 | 20.png\n</images>").doesNotContain("21.png");
  }

  @Test
  @DisplayName("공개 주소가 없으면 이미지 블록을 보내지 않는다")
  void 공개_주소가_없으면_이미지_블록을_보내지_않는다() {
    summarize(source(List.of(), List.of(new NoticeImage(7L, "a.png", null))), List.of());

    assertThat(sentImageBlocks()).isEmpty();
    assertThat(sentText()).contains("<images>\n- | 7 | a.png\n</images>");
  }

  @Test
  @DisplayName("응답을 판매 건으로 바꾸고, 키보드 밖 제품(OTHER)도 그대로 받는다")
  void 응답을_판매_건으로_바꾼다() {
    NoticeSummaryResult result =
        summarize(
            source(List.of(STORE), List.of(new NoticeImage(7L, "a.png", null))),
            List.of(
                item(
                    "productName", " Test Board ",
                    "saleType", "PREORDER",
                    "phase", "ANNOUNCE",
                    "startDate", "2026-11-09",
                    "startTime", "09:00",
                    "endDate", "2026-11-20",
                    "endTime", "23:59",
                    "shippingNote", "27년 3분기 발송 예정",
                    "summary", List.of("테스트 키보드 예약판매예요.", "알루미늄 케이스예요."),
                    "links", List.of(link("판매 페이지", "https://shop.test/p/1")),
                    "imageIds", List.of("7")),
                item("productName", "Test Pad", "category", "OTHER", "saleType", "SALE")));

    assertThat(result.getSummarizedBy()).isEqualTo("claude-opus-5-5");
    assertThat(result.getItems()).hasSize(2);
    SaleItemDraft board = result.getItems().get(0);
    assertThat(board.getProductName()).isEqualTo("Test Board");
    assertThat(board.getCategory()).isEqualTo(SaleCategory.KEYBOARD);
    assertThat(board.getSaleType()).isEqualTo(SaleType.PREORDER);
    assertThat(board.getPhase()).isEqualTo(SalePhase.ANNOUNCE);
    assertThat(board.getStartDate()).isEqualTo(LocalDate.of(2026, 11, 9));
    assertThat(board.getStartTime()).isEqualTo(LocalTime.of(9, 0));
    assertThat(board.getEndDate()).isEqualTo(LocalDate.of(2026, 11, 20));
    assertThat(board.getEndTime()).isEqualTo(LocalTime.of(23, 59));
    assertThat(board.getShippingNote()).isEqualTo("27년 3분기 발송 예정");
    assertThat(board.getSummaryLines()).containsExactly("테스트 키보드 예약판매예요.", "알루미늄 케이스예요.");
    assertThat(board.getLinks()).containsExactly(STORE);
    assertThat(board.getImageIds()).containsExactly(7L);
    SaleItemDraft pad = result.getItems().get(1);
    assertThat(pad.getCategory()).isEqualTo(SaleCategory.OTHER);
    assertThat(pad.getSaleType()).isEqualTo(SaleType.SALE);
    assertThat(pad.getLinks()).isEmpty();
    assertThat(pad.getImageIds()).isEmpty();
  }

  @Test
  @DisplayName("보내지 않은 링크는 버린다")
  void 보내지_않은_링크는_버린다() {
    NoticeSummaryResult result =
        summarize(
            source(List.of(STORE), List.of()),
            List.of(
                item(
                    "links",
                    List.of(
                        link("판매 페이지", "https://evil.test/p/1"),
                        link("판매 페이지", "https://shop.test/p/1")))));

    assertThat(result.getItems().get(0).getLinks()).containsExactly(STORE);
  }

  @Test
  @DisplayName("끝 슬래시나 앞뒤 공백만 다른 링크는 보낸 링크(글자·주소)로 맞춘다")
  void 끝_슬래시만_다른_링크는_보낸_주소로_맞춘다() {
    NoticeSummaryResult result =
        summarize(
            source(List.of(STORE, OFFICIAL), List.of()),
            List.of(
                item(
                    "links",
                    List.of(
                        link("공식", "https://shop.test/p/2"),
                        link("판매", " https://shop.test/p/1/ "),
                        link("판매 페이지", "https://shop.test/p/1")))));

    assertThat(result.getItems().get(0).getLinks()).containsExactly(OFFICIAL, STORE);
  }

  @Test
  @DisplayName("보내지 않은 사진 id는 버린다")
  void 보내지_않은_사진_id는_버린다() {
    NoticeSummaryResult result =
        summarize(
            source(
                List.of(),
                List.of(new NoticeImage(7L, "a.png", null), new NoticeImage(8L, "b.png", null))),
            List.of(item("imageIds", List.of("8", "99", "1", "abc", "7", "8"))));

    assertThat(result.getItems().get(0).getImageIds()).containsExactly(8L, 7L);
  }

  @Test
  @DisplayName("읽을 수 없는 시각은 null로 두고 판매 건은 살리며, 날짜가 없으면 시각도 null")
  void 읽을_수_없는_시각은_null로_둔다() {
    NoticeSummaryResult result =
        summarize(
            source(List.of(), List.of()),
            List.of(
                item("startDate", "2026-11-06", "startTime", "24:00"),
                item("startDate", null, "startTime", "19:30"),
                item("startTime", "25:10", "endDate", "2026-11-07", "endTime", "19:60"),
                item("startTime", "7:30", "endDate", "2026-13-01", "endTime", "19:00")));

    assertThat(result.getItems()).hasSize(4);
    SaleItemDraft hour24 = result.getItems().get(0);
    assertThat(hour24.getStartDate()).isEqualTo(LocalDate.of(2026, 11, 6));
    assertThat(hour24.getStartTime()).isNull();
    SaleItemDraft noDate = result.getItems().get(1);
    assertThat(noDate.getStartDate()).isNull();
    assertThat(noDate.getStartTime()).isNull();
    SaleItemDraft badTimes = result.getItems().get(2);
    assertThat(badTimes.getStartTime()).isNull();
    assertThat(badTimes.getEndDate()).isEqualTo(LocalDate.of(2026, 11, 7));
    assertThat(badTimes.getEndTime()).isNull();
    SaleItemDraft badDate = result.getItems().get(3);
    assertThat(badDate.getStartDate()).isEqualTo(LocalDate.of(2026, 11, 6));
    assertThat(badDate.getStartTime()).isNull();
    assertThat(badDate.getEndDate()).isNull();
    assertThat(badDate.getEndTime()).isNull();
  }

  @Test
  @DisplayName("이름이 비었거나 공백뿐이거나 80자를 넘는 판매 건은 버리고, 80자면 받는다")
  void 이름이_빈_판매_건은_버린다() {
    NoticeSummaryResult result =
        summarize(
            source(List.of(), List.of()),
            List.of(
                item("productName", ""),
                item("productName", " \n "),
                item("productName", null),
                item("productName", "A".repeat(81)),
                item("productName", " " + "B".repeat(80) + " "),
                item("productName", "Test Board")));

    assertThat(result.getItems())
        .extracting(SaleItemDraft::getProductName)
        .containsExactly("B".repeat(80), "Test Board");
  }

  @Test
  @DisplayName("요약은 빈 줄을 빼고 앞뒤 공백을 지워 세 줄까지 쓴다")
  void 요약은_세_줄까지() {
    NoticeSummaryResult result =
        summarize(
            source(List.of(), List.of()),
            List.of(
                item(
                    "summary",
                    List.of(" 첫 줄이에요. ", "", "  ", "둘째 줄이에요.", "셋째 줄이에요.", "넷째 줄이에요."))));

    assertThat(result.getItems().get(0).getSummaryLines())
        .containsExactly("첫 줄이에요.", "둘째 줄이에요.", "셋째 줄이에요.");
  }

  @Test
  @DisplayName("판매 건은 공지당 20개까지 받는다")
  void 판매_건은_20개까지() {
    List<Map<String, Object>> many = new ArrayList<>();
    IntStream.range(0, 21).forEach(i -> many.add(item("productName", "Board " + i)));

    NoticeSummaryResult result = summarize(source(List.of(), List.of()), many);

    assertThat(result.getItems()).hasSize(20);
    assertThat(result.getItems().get(19).getProductName()).isEqualTo("Board 19");
  }

  @Test
  @DisplayName("판매가 아닌 공지는 0건")
  void 판매가_아닌_공지는_0건() {
    NoticeSummaryResult result = summarize(source(List.of(), List.of()), List.of());

    assertThat(result.getItems()).isEmpty();
    assertThat(result.getSummarizedBy()).isEqualTo("claude-opus-5-5");
  }

  @Test
  @DisplayName("거절하거나 끝까지 답하지 않으면 예외")
  void 거절하면_예외() {
    server.responseBody = response("refusal", "");

    assertThatThrownBy(() -> summarizer().summarize(source(List.of(), List.of())))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("거절");

    server.responseBody = response("max_tokens", "{\"items\": [");

    assertThatThrownBy(() -> summarizer().summarize(source(List.of(), List.of())))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("max_tokens");
  }

  @Test
  @DisplayName("스키마는 분류·판매 방식·시점을 열거값으로 받고 모든 속성을 필수로 둔다")
  void 스키마의_열거값() {
    summarize(source(List.of(), List.of()), List.of());

    JsonNode schema = sentSchema();
    assertThat(schema.get("required")).extracting(JsonNode::asString).containsExactly("items");
    assertThat(schema.get("additionalProperties").asBoolean()).isFalse();
    JsonNode item = schema.get("properties").get("items").get("items");
    JsonNode properties = item.get("properties");
    assertThat(properties.get("category").get("enum"))
        .extracting(JsonNode::asString)
        .containsExactly("KEYBOARD", "KEYCAP", "SWITCH", "PARTS", "OTHER");
    assertThat(properties.get("saleType").get("enum"))
        .extracting(JsonNode::asString)
        .containsExactly("SALE", "PREORDER", "RESTOCK", "DROP", "RAFFLE");
    assertThat(properties.get("phase").get("enum"))
        .extracting(JsonNode::asString)
        .containsExactly("ANNOUNCE", "OPEN", "CHANGE");
    assertThat(properties.get("imageIds").get("items").get("type").asString())
        .isEqualTo("string");
    assertThat(properties.get("startDate").get("anyOf").get(0).get("format").asString())
        .isEqualTo("date");
    assertThat(item.get("required"))
        .extracting(JsonNode::asString)
        .containsExactlyInAnyOrder(
            "productName",
            "category",
            "saleType",
            "phase",
            "startDate",
            "startTime",
            "endDate",
            "endTime",
            "shippingNote",
            "summary",
            "links",
            "imageIds");
    assertThat(item.get("additionalProperties").asBoolean()).isFalse();
    assertThat(properties.get("links").get("items").get("additionalProperties").asBoolean())
        .isFalse();
  }

  @Test
  @DisplayName("지시문은 판매 소식만 고르기·제품 범위·나누기·링크·사진 id·날짜·요약 규칙을 설명한다")
  void 지시문은_판매_건_규칙을_설명한다() {
    assertThat(ClaudeNoticeSummarizer.SYSTEM_PROMPT)
        .contains("예약판매", "드랍", "래플", "재입고", "연장", "마감")
        .contains("배송", "가격 인상", "타건샵", "팝업", "아르바이트", "A/S", "라이브")
        .contains("KEYBOARD", "KEYCAP", "SWITCH", "PARTS", "OTHER", "마우스", "윤활제")
        .contains("<links>", "<images>", "첨부 id", "순번이 아니다")
        .contains("<posted_at>", "금일", "차주", "시각만 null")
        .contains("해요체", "해외");
  }
}

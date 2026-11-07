package com.keycheck.summary.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.models.beta.AnthropicBeta;
import com.anthropic.models.beta.messages.BetaOutputConfig;
import com.anthropic.models.beta.messages.MessageCreateParams;
import com.keycheck.post.domain.Board;
import com.keycheck.post.domain.BodyLink;
import com.keycheck.post.domain.Category;
import com.keycheck.post.domain.SpecItem;
import com.keycheck.post.domain.Subcategory;
import com.keycheck.post.domain.VendorItem;
import com.keycheck.summary.FakeAnthropicServer;
import com.keycheck.summary.config.ClaudeProperties;
import com.keycheck.summary.dto.PostSource;
import com.keycheck.summary.dto.SummaryResult;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.IntStream;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** 실제 Anthropic API 대신 FakeAnthropicServer(127.0.0.1)에 요청한다 */
class ClaudeSummarizerTest {
  private static final Instant POSTED_AT = Instant.parse("2026-10-13T00:00:00Z");

  private static final PostSource SOURCE =
      new PostSource(
          Board.IC,
          "[IC] Test Board",
          "A 65% gasket mount board. Aluminum case.",
          POSTED_AT,
          List.of());

  private static final PostSource GB_SOURCE =
      new PostSource(
          Board.GB,
          "[GB] Test Keycaps",
          "Group buy runs Oct 30 - Nov 13. MOQ 100.",
          POSTED_AT,
          List.of());

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

  private ClaudeSummarizer summarizer(boolean fallbacks) {
    return new ClaudeSummarizer(
        client,
        new ClaudeProperties("claude-opus-5-5", "low", 8192, Duration.ofMinutes(2), fallbacks));
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

  private static final String SUMMARY_JSON =
      """
      {"category": "KEYBOARD", "subcategory": "KB_65",
       "summaryLines": ["65% 가스켓 마운트 키보드다.", "알루미늄 케이스다.", "IC 단계다."],
       "specs": [{"label": "배열", "value": "65%"}, {"label": "마운트", "value": "가스켓"}]}
      """;

  /** 공동구매 기간 두 키를 더한 응답 본문. null이면 JSON null로 넣는다. */
  private static String gbJson(String start, String end) {
    return "{\"category\": \"KEYCAP\", \"subcategory\": \"KC_GMK\","
        + " \"summaryLines\": [\"GMK 키캡 공동구매예요.\"], \"specs\": [],"
        + " \"gbStartDate\": "
        + (start == null ? "null" : "\"" + start + "\"")
        + ", \"gbEndDate\": "
        + (end == null ? "null" : "\"" + end + "\"")
        + "}";
  }

  /** 상품 이름 키를 더한 응답 본문. null이면 JSON null로 넣는다. */
  private static String productJson(String productName) {
    return "{\"category\": \"KEYCAP\", \"subcategory\": \"KC_GMK\","
        + " \"summaryLines\": [\"GMK 키캡이에요.\"], \"specs\": [],"
        + " \"gbStartDate\": null, \"gbEndDate\": null,"
        + " \"productName\": "
        + JsonMapper.shared().writeValueAsString(productName)
        + "}";
  }

  private static final List<BodyLink> LINKS =
      List.of(new BodyLink("Swagkeys", "https://swagkeys.com/p"));

  /** 링크를 함께 넘기는 글 */
  private static PostSource linked(Board board, List<BodyLink> links) {
    return new PostSource(
        board, "[GB] Test Keycaps", "Vendors: Swagkeys (KR), NovelKeys (US)", POSTED_AT, links);
  }

  /** 벤더 키를 더한 응답 본문. vendors는 JSON 그대로 넣는다. */
  private static String vendorsJson(String vendors) {
    return "{\"category\": \"KEYCAP\", \"subcategory\": \"KC_GMK\","
        + " \"summaryLines\": [\"GMK 키캡 공동구매예요.\"], \"specs\": [],"
        + " \"gbStartDate\": null, \"gbEndDate\": null, \"productName\": null,"
        + " \"vendors\": "
        + vendors
        + "}";
  }

  /** 마지막 요청의 사용자 메시지(JSON 문자열 표기, 줄바꿈은 \n) */
  private String sentMessage() {
    return JsonMapper.shared()
        .readTree(server.requestBody)
        .get("messages")
        .get(0)
        .get("content")
        .toString();
  }

  @Test
  @DisplayName("요청에 모델·max_tokens·effort·JSON 스키마를 넣고 thinking·샘플링 파라미터·prefill은 넣지 않는다")
  void buildsParams() {
    MessageCreateParams params = summarizer(true).params(SOURCE);

    assertThat(params.model().asString()).isEqualTo("claude-opus-5-5");
    assertThat(params.maxTokens()).isEqualTo(8192);
    BetaOutputConfig outputConfig = params.outputConfig().orElseThrow();
    assertThat(outputConfig.effort()).contains(BetaOutputConfig.Effort.LOW);
    assertThat(outputConfig.format()).contains(ClaudeSummarizer.FORMAT);
    assertThat(params.thinking()).isEmpty();
    assertThat(params._additionalBodyProperties()).isEmpty();
    assertThat(params.system().orElseThrow().string()).contains(ClaudeSummarizer.SYSTEM_PROMPT);
    // 마지막 메시지가 사용자 메시지 하나뿐이다 (assistant prefill 없음)
    assertThat(params.messages()).hasSize(1);
  }

  @Test
  @DisplayName("fallbacks가 켜져 있으면 server-side fallbacks \"default\"와 beta 헤더를 넣는다")
  void fallbacksOn() {
    MessageCreateParams params = summarizer(true).params(SOURCE);

    assertThat(params.fallbacks().orElseThrow().isDefault()).isTrue();
    assertThat(params.betas().orElseThrow())
        .containsExactly(AnthropicBeta.SERVER_SIDE_FALLBACK_2026_07_01);
  }

  @Test
  @DisplayName("Haiku는 server-side fallbacks를 지원하지 않으므로 설정이 켜져 있어도 넣지 않는다")
  void noFallbacksForHaiku() {
    MessageCreateParams params =
        new ClaudeSummarizer(
                client,
                new ClaudeProperties("claude-haiku-5-5", "low", 8192, Duration.ofMinutes(2), true))
            .params(SOURCE);

    assertThat(params.model().asString()).isEqualTo("claude-haiku-5-5");
    assertThat(params.fallbacks()).isEmpty();
    assertThat(params.betas()).isEmpty();
  }

  @Test
  @DisplayName("fallbacks가 꺼져 있으면 fallbacks와 beta 헤더를 넣지 않는다")
  void fallbacksOff() {
    MessageCreateParams params = summarizer(false).params(SOURCE);

    assertThat(params.fallbacks()).isEmpty();
    assertThat(params.betas()).isEmpty();
  }

  @Test
  @DisplayName("보내는 요청 본문은 json_schema 형식과 분류 enum을 담고 temperature·top_p·top_k·thinking이 없다")
  void sendsRequestBody() {
    server.responseBody = response("end_turn", SUMMARY_JSON);

    summarizer(true).summarize(SOURCE);

    JsonNode body = JsonMapper.shared().readTree(server.requestBody);
    assertThat(body.get("model").asString()).isEqualTo("claude-opus-5-5");
    assertThat(body.get("fallbacks").asString()).isEqualTo("default");
    JsonNode outputConfig = body.get("output_config");
    assertThat(outputConfig.get("effort").asString()).isEqualTo("low");
    assertThat(outputConfig.get("format").get("type").asString()).isEqualTo("json_schema");
    assertThat(
            outputConfig.get("format").get("schema").get("properties").get("category").get("enum"))
        .extracting(JsonNode::asString)
        .containsExactly("KEYBOARD", "KEYCAP", "OTHER");
    JsonNode subcategory =
        outputConfig.get("format").get("schema").get("properties").get("subcategory");
    assertThat(subcategory.get("anyOf").get(0).get("enum"))
        .extracting(JsonNode::asString)
        .containsExactly(
            "KB_100", "KB_96", "KB_80", "KB_75", "KB_65", "KB_60", "KB_40", "KB_ERGO", "KB_ETC",
            "KC_GMK", "KC_SP", "KC_MW", "KC_KKB", "KC_PBTFANS", "KC_ETC");
    assertThat(subcategory.get("anyOf").get(1).get("type").asString()).isEqualTo("null");
    assertThat(outputConfig.get("format").get("schema").get("required"))
        .extracting(JsonNode::asString)
        .contains("subcategory");
    assertThat(body.has("temperature")).isFalse();
    assertThat(body.has("top_p")).isFalse();
    assertThat(body.has("top_k")).isFalse();
    assertThat(body.has("thinking")).isFalse();
    assertThat(body.get("messages")).hasSize(1);
    JsonNode message = body.get("messages").get(0);
    assertThat(message.get("role").asString()).isEqualTo("user");
    assertThat(message.get("content").toString())
        .contains("<board>\\nIC\\n</board>", "[IC] Test Board", "A 65% gasket mount board.");
    assertThat(server.requestHeaders.getFirst("anthropic-beta"))
        .contains("server-side-fallback-2026-07-01");
  }

  @Test
  @DisplayName("응답 JSON을 분류·요약·스펙과 응답한 모델 ID로 바꾼다")
  void mapsResponse() {
    server.responseBody = response("end_turn", SUMMARY_JSON);

    SummaryResult result = summarizer(true).summarize(SOURCE);

    assertThat(result.getCategory()).isEqualTo(Category.KEYBOARD);
    assertThat(result.getSubcategory()).isEqualTo(Subcategory.KB_65);
    assertThat(result.getLines())
        .containsExactly("65% 가스켓 마운트 키보드다.", "알루미늄 케이스다.", "IC 단계다.");
    assertThat(result.getSpecs())
        .containsExactly(new SpecItem("배열", "65%"), new SpecItem("마운트", "가스켓"));
    assertThat(result.getSummarizedBy()).isEqualTo("claude-opus-5-5");
  }

  @Test
  @DisplayName("지시문에 세부 분류(키보드 배열, 키캡 제조사) 정의가 있다")
  void systemPromptDefinesSubcategories() {
    assertThat(ClaudeSummarizer.SYSTEM_PROMPT)
        .contains("subcategory", "KB_96", "TKL", "HHKB", "KB_ERGO", "KC_SP", "KC_ETC")
        .contains("Signature Plastics");
  }

  @Test
  @DisplayName("GB 글이면 사용자 메시지에 GB 게시판을 표시한다")
  void sendsGbBoard() {
    server.responseBody = response("end_turn", SUMMARY_JSON);

    summarizer(true)
        .summarize(
            new PostSource(
                Board.GB,
                "[GB] Test Board",
                "Group buy runs Nov 1 - Nov 30. MOQ 100.",
                POSTED_AT,
                List.of()));

    JsonNode message = JsonMapper.shared().readTree(server.requestBody).get("messages").get(0);
    assertThat(message.get("content").toString())
        .contains("<board>\\nGB\\n</board>", "[GB] Test Board")
        .doesNotContain("<board>\\nIC");
  }

  @Test
  @DisplayName("지시문은 게시판 표시를 설명하고, GB 글이면 공동구매 정보를 스펙에 먼저 뽑으라고 한다")
  void systemPromptDescribesBoards() {
    assertThat(ClaudeSummarizer.SYSTEM_PROMPT)
        .contains("<board>", "Interest Checks", "Group Buys and Preorders")
        .contains("공동구매 기간", "벤더", "가격", "MOQ")
        .contains("해요체");
  }

  @Test
  @DisplayName("GB 글이면 응답의 공동구매 시작일·마감일을 날짜로 바꾼다")
  void extractsGbPeriod() {
    server.responseBody = response("end_turn", gbJson("2026-10-30", "2026-11-13"));

    SummaryResult result = summarizer(true).summarize(GB_SOURCE);

    assertThat(result.getGbStartDate()).isEqualTo(LocalDate.of(2026, 10, 30));
    assertThat(result.getGbEndDate()).isEqualTo(LocalDate.of(2026, 11, 13));

    server.responseBody = response("end_turn", gbJson(null, "2026-11-13"));

    SummaryResult endOnly = summarizer(true).summarize(GB_SOURCE);

    assertThat(endOnly.getGbStartDate()).isNull();
    assertThat(endOnly.getGbEndDate()).isEqualTo(LocalDate.of(2026, 11, 13));
  }

  @Test
  @DisplayName("공동구매 날짜 형식이 틀리면 시작일·마감일을 둘 다 버리고 요약은 그대로 쓴다")
  void invalidGbDateDropsBoth() {
    server.responseBody = response("end_turn", gbJson("2026-13-01", "2026-11-13"));

    SummaryResult invalidMonth = summarizer(true).summarize(GB_SOURCE);

    assertThat(invalidMonth.getGbStartDate()).isNull();
    assertThat(invalidMonth.getGbEndDate()).isNull();
    assertThat(invalidMonth.getLines()).containsExactly("GMK 키캡 공동구매예요.");
    assertThat(invalidMonth.getCategory()).isEqualTo(Category.KEYCAP);

    server.responseBody = response("end_turn", gbJson("2026-10-30", "Nov 10"));

    SummaryResult notIso = summarizer(true).summarize(GB_SOURCE);

    assertThat(notIso.getGbStartDate()).isNull();
    assertThat(notIso.getGbEndDate()).isNull();
    assertThat(notIso.getLines()).containsExactly("GMK 키캡 공동구매예요.");
  }

  @Test
  @DisplayName("마감일이 시작일보다 앞이면 둘 다 버리고, 같은 날이면 그대로 쓴다")
  void endBeforeStartDropsBoth() {
    server.responseBody = response("end_turn", gbJson("2026-11-13", "2026-10-30"));

    SummaryResult reversed = summarizer(true).summarize(GB_SOURCE);

    assertThat(reversed.getGbStartDate()).isNull();
    assertThat(reversed.getGbEndDate()).isNull();

    server.responseBody = response("end_turn", gbJson("2026-11-13", "2026-11-13"));

    SummaryResult sameDay = summarizer(true).summarize(GB_SOURCE);

    assertThat(sameDay.getGbStartDate()).isEqualTo(LocalDate.of(2026, 11, 13));
    assertThat(sameDay.getGbEndDate()).isEqualTo(LocalDate.of(2026, 11, 13));
  }

  @Test
  @DisplayName("IC 글이면 응답에 날짜가 있어도 공동구매 기간은 null")
  void icNeverHasGbPeriod() {
    server.responseBody = response("end_turn", gbJson("2026-10-30", "2026-11-13"));

    SummaryResult result = summarizer(true).summarize(SOURCE);

    assertThat(result.getGbStartDate()).isNull();
    assertThat(result.getGbEndDate()).isNull();
  }

  @Test
  @DisplayName("응답에 공동구매 기간 키가 없으면 둘 다 null")
  void missingGbKeysAreNull() {
    server.responseBody = response("end_turn", SUMMARY_JSON);

    SummaryResult result = summarizer(true).summarize(GB_SOURCE);

    assertThat(result.getGbStartDate()).isNull();
    assertThat(result.getGbEndDate()).isNull();
  }

  @Test
  @DisplayName("응답 형식은 공동구매 시작일·마감일을 날짜 문자열 또는 null로 받고 두 키를 필수로 둔다")
  void schemaRequiresGbPeriod() {
    server.responseBody = response("end_turn", SUMMARY_JSON);

    summarizer(true).summarize(GB_SOURCE);

    JsonNode schema =
        JsonMapper.shared()
            .readTree(server.requestBody)
            .get("output_config")
            .get("format")
            .get("schema");
    for (String key : new String[] {"gbStartDate", "gbEndDate"}) {
      JsonNode anyOf = schema.get("properties").get(key).get("anyOf");
      assertThat(anyOf.get(0).get("type").asString()).isEqualTo("string");
      assertThat(anyOf.get(0).get("format").asString()).isEqualTo("date");
      assertThat(anyOf.get(1).get("type").asString()).isEqualTo("null");
    }
    assertThat(schema.get("required"))
        .extracting(JsonNode::asString)
        .contains("gbStartDate", "gbEndDate");
  }

  @Test
  @DisplayName("사용자 메시지에 글 작성 시각을 넣는다")
  void sendsPostedAt() {
    server.responseBody = response("end_turn", SUMMARY_JSON);

    summarizer(true).summarize(GB_SOURCE);

    JsonNode message = JsonMapper.shared().readTree(server.requestBody).get("messages").get(0);
    assertThat(message.get("content").toString())
        .contains("</board>\\n<posted_at>\\n2026-10-13T00:00:00Z\\n</posted_at>\\n<title>");
  }

  @Test
  @DisplayName("지시문은 공동구매 기간을 뽑는 규칙(작성 시각 기준·해 넘김, 제목 우선, 취소·연기·연장, 끝난 공동구매, 확신이 없으면 null)을 설명한다")
  void systemPromptDescribesGbPeriod() {
    assertThat(ClaudeSummarizer.SYSTEM_PROMPT)
        .contains("gbStartDate", "gbEndDate", "posted_at")
        .contains("취소", "제목", "끝난", "확신")
        .contains("제목을 따른다", "연도")
        // 연도 없는 마감일은 작성일 뒤로(해 넘김), 새 마감일 없는 연장은 옛 마감일 대신 null
        .contains("그 뒤인 가장 가까운 날짜", "연장", "옛 마감일을 쓰지 않는다");
  }

  @Test
  @DisplayName("응답의 상품 이름을 앞뒤 공백을 지워 쓰고, IC·GB 글 모두 쓴다")
  void mapsProductName() {
    server.responseBody = response("end_turn", productJson("SWG Nightshift"));

    assertThat(summarizer(true).summarize(GB_SOURCE).getProductName())
        .isEqualTo("SWG Nightshift");

    server.responseBody = response("end_turn", productJson("  GMK CYL nuSignal \n"));

    assertThat(summarizer(true).summarize(SOURCE).getProductName())
        .isEqualTo("GMK CYL nuSignal");
  }

  @Test
  @DisplayName("상품 이름이 null이거나 비었거나 공백뿐이거나 키가 없으면 null")
  void blankProductNameIsNull() {
    for (String blank : new String[] {null, "", "   ", "\n\t"}) {
      server.responseBody = response("end_turn", productJson(blank));

      assertThat(summarizer(true).summarize(SOURCE).getProductName()).isNull();
    }

    server.responseBody = response("end_turn", SUMMARY_JSON);

    assertThat(summarizer(true).summarize(SOURCE).getProductName()).isNull();
  }

  @Test
  @DisplayName("상품 이름이 앞뒤 공백을 지우고 80자를 넘으면 버리고(null) 요약은 그대로 쓰며, 80자면 그대로 쓴다")
  void tooLongProductNameIsNull() {
    server.responseBody = response("end_turn", productJson("A".repeat(81)));

    SummaryResult tooLong = summarizer(true).summarize(SOURCE);

    assertThat(tooLong.getProductName()).isNull();
    assertThat(tooLong.getLines()).containsExactly("GMK 키캡이에요.");
    assertThat(tooLong.getCategory()).isEqualTo(Category.KEYCAP);

    server.responseBody = response("end_turn", productJson(" " + "A".repeat(80) + " "));

    assertThat(summarizer(true).summarize(SOURCE).getProductName()).isEqualTo("A".repeat(80));
  }

  @Test
  @DisplayName("응답 형식은 상품 이름을 문자열 또는 null로 받고 필수로 둔다")
  void schemaRequiresProductName() {
    server.responseBody = response("end_turn", SUMMARY_JSON);

    summarizer(true).summarize(SOURCE);

    JsonNode schema =
        JsonMapper.shared()
            .readTree(server.requestBody)
            .get("output_config")
            .get("format")
            .get("schema");
    JsonNode anyOf = schema.get("properties").get("productName").get("anyOf");
    assertThat(anyOf).hasSize(2);
    assertThat(anyOf.get(0).get("type").asString()).isEqualTo("string");
    assertThat(anyOf.get(1).get("type").asString()).isEqualTo("null");
    assertThat(schema.get("required")).extracting(JsonNode::asString).contains("productName");
  }

  @Test
  @DisplayName("지시문은 상품 이름을 뽑는 규칙(원문 표기, 게시판 표시·날짜·진행 상태·홍보 문구 빼기, 60자, 대표 이름 하나, 모르면 null)을 설명한다")
  void systemPromptDescribesProductName() {
    assertThat(ClaudeSummarizer.SYSTEM_PROMPT)
        .contains("productName", "SWG Nightshift", "Class 60 V2", "GMK CYL nuSignal")
        .contains("원문 표기", "[IC]", "[GB]", "날짜", "Live", "Extended", "Cancelled", "Round 3")
        .contains("홍보 문구", "60자", "대표 이름 하나")
        .contains("이름을 알 수 없으면 null", "번역하지 않는다");
  }

  @Test
  @DisplayName("응답의 벤더를 이름·지역·판매 링크로 바꾸고 지역은 대문자로 쓰며, IC·GB 글 모두 쓴다")
  void mapsVendors() {
    server.responseBody =
        response(
            "end_turn",
            vendorsJson(
                "[{\"name\": \"Swagkeys\", \"region\": \"kr\", \"url\": \"https://swagkeys.com/p\"},"
                    + " {\"name\": \"NovelKeys\", \"region\": \"US\", \"url\": null}]"));

    for (Board board : new Board[] {Board.GB, Board.IC}) {
      SummaryResult result = summarizer(true).summarize(linked(board, LINKS));

      assertThat(result.getVendors())
          .containsExactly(
              new VendorItem("Swagkeys", "KR", "https://swagkeys.com/p"),
              new VendorItem("NovelKeys", "US", null));
      assertThat(result.getVendors()).extracting(VendorItem::isKorean).containsExactly(true, false);
    }
  }

  @Test
  @DisplayName("벤더 주소가 넘긴 링크(앞의 150개)에 없으면 주소만 버리고(null) 이름·지역은 그대로 쓴다")
  void dropsUnknownVendorUrl() {
    server.responseBody =
        response(
            "end_turn",
            vendorsJson(
                "[{\"name\": \"Swagkeys\", \"region\": \"KR\", \"url\": \"https://evil.example\"}]"));

    assertThat(summarizer(true).summarize(linked(Board.GB, LINKS)).getVendors())
        .containsExactly(new VendorItem("Swagkeys", "KR", null));

    // 링크가 없는 글이면 어떤 주소도 쓰지 않는다
    server.responseBody =
        response(
            "end_turn",
            vendorsJson(
                "[{\"name\": \"Swagkeys\", \"region\": \"KR\", \"url\": \"https://swagkeys.com/p\"}]"));

    assertThat(summarizer(true).summarize(linked(Board.GB, List.of())).getVendors())
        .containsExactly(new VendorItem("Swagkeys", "KR", null));

    // 151번째 링크는 넘기지 않았으므로 쓰지 않는다
    List<BodyLink> many =
        IntStream.range(0, 151)
            .mapToObj(i -> new BodyLink("Shop" + i, "https://shop.example/" + i))
            .toList();
    server.responseBody =
        response(
            "end_turn",
            vendorsJson(
                "[{\"name\": \"Shop149\", \"region\": \"US\", \"url\": \"https://shop.example/149\"},"
                    + " {\"name\": \"Shop150\", \"region\": \"US\", \"url\": \"https://shop.example/150\"}]"));

    assertThat(summarizer(true).summarize(linked(Board.GB, many)).getVendors())
        .containsExactly(
            new VendorItem("Shop149", "US", "https://shop.example/149"),
            new VendorItem("Shop150", "US", null));
  }

  @Test
  @DisplayName("이름이 없거나 비었거나 공백뿐인 벤더는 버리고, 남은 벤더가 30개보다 많으면 앞의 30개만 쓴다")
  void dropsBlankVendorAndCaps() {
    server.responseBody =
        response(
            "end_turn",
            vendorsJson(
                "[{\"name\": \"\", \"region\": \"KR\", \"url\": null},"
                    + " {\"name\": \" \\n \", \"region\": \"KR\", \"url\": null},"
                    + " {\"name\": null, \"region\": \"KR\", \"url\": null},"
                    + " {\"name\": \"NovelKeys\", \"region\": \"US\", \"url\": null}]"));

    assertThat(summarizer(true).summarize(linked(Board.GB, LINKS)).getVendors())
        .containsExactly(new VendorItem("NovelKeys", "US", null));

    String vendors =
        IntStream.range(0, 31)
            .mapToObj(i -> "{\"name\": \"Vendor" + i + "\", \"region\": null, \"url\": null}")
            .collect(Collectors.joining(",", "[{\"name\": \"\", \"region\": null, \"url\": null},", "]"));
    server.responseBody = response("end_turn", vendorsJson(vendors));

    List<VendorItem> capped = summarizer(true).summarize(linked(Board.GB, LINKS)).getVendors();

    assertThat(capped).hasSize(30);
    assertThat(capped.get(0)).isEqualTo(new VendorItem("Vendor0", null, null));
    assertThat(capped.get(29)).isEqualTo(new VendorItem("Vendor29", null, null));
  }

  @Test
  @DisplayName("벤더 이름·지역의 앞뒤 공백을 지우고, 지역이 비었거나 공백뿐이면 null")
  void normalizesVendorNameAndRegion() {
    server.responseBody =
        response(
            "end_turn",
            vendorsJson(
                "[{\"name\": \" Swagkeys \\n\", \"region\": \" kr \", \"url\": null},"
                    + " {\"name\": \"NovelKeys\", \"region\": \"\", \"url\": null},"
                    + " {\"name\": \"Cannonkeys\", \"region\": \"  \", \"url\": null}]"));

    assertThat(summarizer(true).summarize(linked(Board.GB, LINKS)).getVendors())
        .containsExactly(
            new VendorItem("Swagkeys", "KR", null),
            new VendorItem("NovelKeys", null, null),
            new VendorItem("Cannonkeys", null, null));
  }

  @Test
  @DisplayName("지역이 여럿이거나 한국을 다르게 적어도(KR/CN, Korea, South Korea, 한국, 🇰🇷) 한국이 있으면 KR로 쓰고, 한국이 없으면 대문자로만 바꾼다")
  void koreanRegionVariantsAreKr() {
    String vendors =
        List.of(
                "KR/CN", "cn, kr", "Korea", "South Korea", "한국", "🇰🇷", "(KR)", "US KR", "CN/SG",
                "eu")
            .stream()
            .map(
                region ->
                    "{\"name\": \"V\", \"region\": "
                        + JsonMapper.shared().writeValueAsString(region)
                        + ", \"url\": null}")
            .collect(Collectors.joining(",", "[", "]"));
    server.responseBody = response("end_turn", vendorsJson(vendors));

    assertThat(summarizer(true).summarize(linked(Board.GB, LINKS)).getVendors())
        .extracting(VendorItem::getRegion)
        .containsExactly("KR", "KR", "KR", "KR", "KR", "KR", "KR", "KR", "CN/SG", "EU");
  }

  @Test
  @DisplayName("벤더 주소가 넘긴 링크와 앞뒤 공백·끝 슬래시만 다르면 넘긴 링크의 주소를 쓴다")
  void matchesVendorUrlIgnoringTrailingSlash() {
    List<BodyLink> links =
        List.of(
            new BodyLink("Swagkeys", "https://swagkeys.com/p"),
            new BodyLink("NovelKeys", "https://novelkeys.com/x/"));
    server.responseBody =
        response(
            "end_turn",
            vendorsJson(
                "[{\"name\": \"Swagkeys\", \"region\": \"KR\", \"url\": \"https://swagkeys.com/p/\"},"
                    + " {\"name\": \"NovelKeys\", \"region\": \"US\", \"url\": \" https://novelkeys.com/x \"},"
                    + " {\"name\": \"Other\", \"region\": \"US\", \"url\": \"https://swagkeys.com/p/q\"}]"));

    assertThat(summarizer(true).summarize(linked(Board.GB, links)).getVendors())
        .containsExactly(
            new VendorItem("Swagkeys", "KR", "https://swagkeys.com/p"),
            new VendorItem("NovelKeys", "US", "https://novelkeys.com/x/"),
            new VendorItem("Other", "US", null));
  }

  @Test
  @DisplayName("응답에 벤더 키가 없거나 null이면 빈 목록")
  void missingVendorsIsEmpty() {
    server.responseBody = response("end_turn", SUMMARY_JSON);

    assertThat(summarizer(true).summarize(linked(Board.GB, LINKS)).getVendors())
        .isNotNull()
        .isEmpty();

    server.responseBody = response("end_turn", vendorsJson("null"));

    assertThat(summarizer(true).summarize(linked(Board.GB, LINKS)).getVendors())
        .isNotNull()
        .isEmpty();
  }

  @Test
  @DisplayName("링크가 있으면 사용자 메시지의 본문 다음에 <links> 구역을 넣어 한 줄에 '글자 | 주소'로 앞의 150개만 쓰고, 없으면 넣지 않는다")
  void sendsLinks() {
    server.responseBody = response("end_turn", SUMMARY_JSON);

    summarizer(true).summarize(linked(Board.GB, LINKS));

    assertThat(sentMessage())
        .contains("\\n</body>\\n<links>\\nSwagkeys | https://swagkeys.com/p\\n</links>");

    List<BodyLink> many =
        IntStream.range(0, 151)
            .mapToObj(i -> new BodyLink("Shop" + i, "https://shop.example/" + i))
            .toList();
    summarizer(true).summarize(linked(Board.GB, many));

    assertThat(sentMessage())
        .contains("<links>\\nShop0 | https://shop.example/0\\nShop1 | https://shop.example/1\\n")
        .contains("\\nShop149 | https://shop.example/149\\n</links>")
        .doesNotContain("Shop150");

    summarizer(true).summarize(linked(Board.GB, List.of()));

    assertThat(sentMessage()).doesNotContain("<links>").endsWith("\\n</body>\"");

    // 링크를 저장하기 전에 읽은 글(null)도 같다
    summarizer(true).summarize(linked(Board.GB, null));

    assertThat(sentMessage()).doesNotContain("<links>").endsWith("\\n</body>\"");
  }

  @Test
  @DisplayName("응답 형식은 벤더를 이름(문자열)·지역·주소(문자열 또는 null) 객체 배열로 받고 필수로 둔다")
  void schemaRequiresVendors() {
    server.responseBody = response("end_turn", SUMMARY_JSON);

    summarizer(true).summarize(SOURCE);

    JsonNode schema =
        JsonMapper.shared()
            .readTree(server.requestBody)
            .get("output_config")
            .get("format")
            .get("schema");
    JsonNode vendors = schema.get("properties").get("vendors");
    assertThat(vendors.get("type").asString()).isEqualTo("array");
    JsonNode item = vendors.get("items");
    assertThat(item.get("type").asString()).isEqualTo("object");
    assertThat(item.get("properties").get("name").get("type").asString()).isEqualTo("string");
    for (String key : new String[] {"region", "url"}) {
      JsonNode anyOf = item.get("properties").get(key).get("anyOf");
      assertThat(anyOf).hasSize(2);
      assertThat(anyOf.get(0).get("type").asString()).isEqualTo("string");
      assertThat(anyOf.get(1).get("type").asString()).isEqualTo("null");
    }
    assertThat(item.get("required"))
        .extracting(JsonNode::asString)
        .containsExactly("name", "region", "url");
    assertThat(item.get("additionalProperties").asBoolean()).isFalse();
    assertThat(schema.get("required")).extracting(JsonNode::asString).contains("vendors");
  }

  @Test
  @DisplayName("지시문은 벤더를 뽑는 규칙(<links>, 원문 지역 표기만 근거로 한 대문자 코드, 여러 지역이면 KR 우선, 링크 목록에 있는 주소만, 없으면 빈 배열)을 설명한다")
  void systemPromptDescribesVendors() {
    assertThat(ClaudeSummarizer.SYSTEM_PROMPT)
        .contains("vendors", "KR", "<links>", "링크 목록에 있는 주소만")
        .contains("Korea", "한국", "South Korea", "Swagkeys (KR/CN)")
        .contains("상점 이름만 보고 KR로 정하지 않는다", "지어내지 않는다", "빈 배열", "계획된 벤더");
  }

  @Test
  @DisplayName("세부 분류가 null이면 null")
  void nullSubcategory() {
    server.responseBody =
        response(
            "end_turn",
            "{\"category\": \"OTHER\", \"subcategory\": null, \"summaryLines\": [\"데스크매트예요.\"],"
                + " \"specs\": []}");

    SummaryResult result = summarizer(true).summarize(SOURCE);

    assertThat(result.getCategory()).isEqualTo(Category.OTHER);
    assertThat(result.getSubcategory()).isNull();
  }

  @Test
  @DisplayName("분류와 맞지 않는 세부 분류는 버린다")
  void dropsMismatchedSubcategory() {
    server.responseBody =
        response(
            "end_turn",
            "{\"category\": \"KEYBOARD\", \"subcategory\": \"KC_GMK\", \"summaryLines\": [\"키보드예요.\"],"
                + " \"specs\": []}");
    SummaryResult keyboard = summarizer(true).summarize(SOURCE);
    assertThat(keyboard.getCategory()).isEqualTo(Category.KEYBOARD);
    assertThat(keyboard.getSubcategory()).isNull();

    server.responseBody =
        response(
            "end_turn",
            "{\"category\": \"OTHER\", \"subcategory\": \"KB_65\", \"summaryLines\": [\"스위치예요.\"],"
                + " \"specs\": []}");
    SummaryResult other = summarizer(true).summarize(SOURCE);
    assertThat(other.getCategory()).isEqualTo(Category.OTHER);
    assertThat(other.getSubcategory()).isNull();
  }

  @Test
  @DisplayName("스펙이 6개보다 많으면 앞의 6개만 쓴다")
  void limitsSpecs() {
    String specs =
        IntStream.range(0, 8)
            .mapToObj(i -> "{\"label\": \"항목" + i + "\", \"value\": \"값\"}")
            .collect(Collectors.joining(","));
    server.responseBody =
        response(
            "end_turn",
            "{\"category\": \"KEYCAP\", \"summaryLines\": [\"키캡 세트다.\"], \"specs\": [" + specs + "]}");

    SummaryResult result = summarizer(true).summarize(SOURCE);

    assertThat(result.getSpecs()).hasSize(6).first().extracting(SpecItem::getLabel).isEqualTo("항목0");
  }

  @Test
  @DisplayName("거절(refusal)하면 예외를 던진다")
  void refusalThrows() {
    server.responseBody =
        """
        {"id": "msg_1", "type": "message", "role": "assistant", "model": "claude-opus-5-5",
         "content": [], "stop_reason": "refusal", "stop_sequence": null,
         "stop_details": {"type": "refusal", "category": "cyber", "explanation": null},
         "usage": {"input_tokens": 1200, "output_tokens": 0}}
        """;

    assertThatThrownBy(() -> summarizer(true).summarize(SOURCE))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("거절");
  }

  @Test
  @DisplayName("max_tokens에서 끊기면 예외를 던진다")
  void maxTokensThrows() {
    server.responseBody = response("max_tokens", "{\"category\": \"KEY");

    assertThatThrownBy(() -> summarizer(true).summarize(SOURCE))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("max_tokens");
  }

  @Test
  @DisplayName("요약 줄이 없거나 JSON이 아니면 예외를 던진다")
  void malformedThrows() {
    server.responseBody =
        response("end_turn", "{\"category\": \"OTHER\", \"summaryLines\": [], \"specs\": []}");
    assertThatThrownBy(() -> summarizer(true).summarize(SOURCE))
        .isInstanceOf(IllegalStateException.class);

    server.responseBody = response("end_turn", "요약할 수 없습니다");
    assertThatThrownBy(() -> summarizer(true).summarize(SOURCE))
        .isInstanceOf(RuntimeException.class);
  }
}

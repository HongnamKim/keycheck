package com.keycheck.summary.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.models.beta.AnthropicBeta;
import com.anthropic.models.beta.messages.BetaOutputConfig;
import com.anthropic.models.beta.messages.MessageCreateParams;
import com.keycheck.post.domain.Category;
import com.keycheck.post.domain.SpecItem;
import com.keycheck.post.domain.Subcategory;
import com.keycheck.summary.FakeAnthropicServer;
import com.keycheck.summary.config.ClaudeProperties;
import com.keycheck.summary.dto.PostSource;
import com.keycheck.summary.dto.SummaryResult;
import java.time.Duration;
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
  private static final PostSource SOURCE =
      new PostSource("[IC] Test Board", "A 65% gasket mount board. Aluminum case.");

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
        .contains("[IC] Test Board", "A 65% gasket mount board.");
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

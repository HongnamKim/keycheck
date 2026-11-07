package com.keycheck.summary;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

/** 실제 Anthropic API 대신 이 컴퓨터(127.0.0.1)에서 정해 둔 응답을 돌려주고 받은 요청을 기록한다 */
public class FakeAnthropicServer implements AutoCloseable {
  private final HttpServer server;

  /** 다음 요청에 돌려줄 Messages API 응답 JSON */
  public volatile String responseBody;

  public volatile String requestBody;
  public volatile Headers requestHeaders;

  public FakeAnthropicServer() throws IOException {
    server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
    server.createContext(
        "/",
        exchange -> {
          requestHeaders = exchange.getRequestHeaders();
          requestBody = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
          byte[] body = responseBody.getBytes(StandardCharsets.UTF_8);
          exchange.getResponseHeaders().add("Content-Type", "application/json");
          exchange.sendResponseHeaders(200, body.length);
          try (OutputStream out = exchange.getResponseBody()) {
            out.write(body);
          }
        });
    server.start();
  }

  /** 이 서버로만 요청하고 재시도하지 않는 클라이언트 */
  public AnthropicClient client(AnthropicOkHttpClient.Builder builder) {
    return builder
        .baseUrl("http://127.0.0.1:" + server.getAddress().getPort())
        .maxRetries(0)
        .build();
  }

  @Override
  public void close() {
    server.stop(0);
  }
}

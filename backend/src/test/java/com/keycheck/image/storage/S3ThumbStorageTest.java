package com.keycheck.image.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.keycheck.image.config.StorageConfig;
import com.keycheck.image.config.StorageProperties;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.services.s3.S3Client;

/** 실제 R2 대신 이 컴퓨터(127.0.0.1)의 가짜 서버로 S3 요청의 모양을 본다. 운영과 같은 설정으로 클라이언트를 만든다. */
class S3ThumbStorageTest {
  private static final String BUCKET = "keycheck-test";
  private static final String KEY = "thumbs/1.webp";
  private static final String ACCESS_KEY_ID = "test-access-key-id";
  private static final String SECRET_ACCESS_KEY = "test-secret-access-key";

  private HttpServer server;

  /** 돌려줄 응답 상태 */
  private volatile int status = 200;

  private volatile String method;
  private volatile String path;
  private volatile Headers headers;
  private volatile byte[] body;

  @BeforeEach
  void setUp() throws IOException {
    server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
    server.createContext(
        "/",
        exchange -> {
          method = exchange.getRequestMethod();
          path = exchange.getRequestURI().getRawPath();
          headers = exchange.getRequestHeaders();
          body = exchange.getRequestBody().readAllBytes();
          if (status == 200) {
            exchange.getResponseHeaders().add("ETag", "\"etag\"");
            exchange.sendResponseHeaders(200, -1);
            exchange.close();
            return;
          }
          byte[] error =
              "<Error><Code>InternalError</Code><Message>fail</Message></Error>"
                  .getBytes(StandardCharsets.UTF_8);
          exchange.getResponseHeaders().add("Content-Type", "application/xml");
          exchange.sendResponseHeaders(status, error.length);
          try (OutputStream out = exchange.getResponseBody()) {
            out.write(error);
          }
        });
    server.start();
  }

  @AfterEach
  void tearDown() {
    server.stop(0);
  }

  private void put(byte[] webp) {
    StorageProperties properties =
        new StorageProperties(
            "http://127.0.0.1:" + server.getAddress().getPort(),
            BUCKET,
            ACCESS_KEY_ID,
            SECRET_ACCESS_KEY,
            "https://img.test");
    StorageConfig config = new StorageConfig();
    try (S3Client client = config.thumbS3Client(properties)) {
      config.thumbStorage(client, properties).put(KEY, webp);
    }
  }

  @Test
  @DisplayName("버킷/객체 이름 경로로 WebP 형식과 긴 캐시 헤더를 붙여 올린다")
  void putsWithHeadersAndPath() {
    byte[] webp = "RIFF....WEBPVP8 ".getBytes(StandardCharsets.US_ASCII);

    put(webp);

    assertThat(method).isEqualTo("PUT");
    assertThat(path).isEqualTo("/" + BUCKET + "/" + KEY);
    assertThat(headers.getFirst("Content-Type")).isEqualTo("image/webp");
    assertThat(headers.getFirst("Cache-Control"))
        .isEqualTo("public, max-age=31536000, immutable");
    // 가짜 서버 주소가 http라 SDK가 본문을 조각마다 서명해(aws-chunked) 보낸다. 실제 R2(https)에는 본문 그대로 간다.
    assertThat(body).containsSequence(webp);
    assertThat(headers.getFirst("x-amz-decoded-content-length"))
        .isEqualTo(String.valueOf(webp.length));
    // R2가 잘 받지 못하는 요청 체크섬(기본이면 CRC32를 x-amz-trailer로 붙인다)을 쓰지 않는다
    assertThat(headers.getFirst("x-amz-sdk-checksum-algorithm")).isNull();
    assertThat(headers.getFirst("x-amz-trailer")).isNull();
    assertThat(headers.keySet()).noneMatch(name -> name.toLowerCase().startsWith("x-amz-checksum"));
  }

  @Test
  @DisplayName("저장소가 서버 오류를 돌려주면 StorageUnavailableException이고 메시지에 객체 이름과 키가 없다")
  void serverErrorIsStorageUnavailable() {
    status = 500;

    assertThatThrownBy(() -> put(new byte[] {1, 2, 3}))
        .isInstanceOf(StorageUnavailableException.class)
        .hasCauseInstanceOf(SdkException.class)
        .satisfies(
            e -> {
              assertThat(e.getMessage())
                  .doesNotContain(KEY)
                  .doesNotContain(ACCESS_KEY_ID)
                  .doesNotContain(SECRET_ACCESS_KEY);
              // 로그에 함께 찍히는 SDK 원인 메시지에도 접속 키가 없다
              assertThat(e.getCause().getMessage())
                  .doesNotContain(ACCESS_KEY_ID)
                  .doesNotContain(SECRET_ACCESS_KEY);
            });
  }

  @Test
  @DisplayName("인증 오류도 이미지 탓이 아니므로 StorageUnavailableException")
  void authErrorIsStorageUnavailable() {
    status = 403;

    assertThatThrownBy(() -> put(new byte[] {1, 2, 3}))
        .isInstanceOf(StorageUnavailableException.class);
  }
}

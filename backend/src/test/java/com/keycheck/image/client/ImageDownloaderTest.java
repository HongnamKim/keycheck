package com.keycheck.image.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.Timeout.ThreadMode;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * 원본 받기. 실제 DNS·네트워크를 쓰지 않는다. 호스트 이름은 가짜 resolver가 풀고, 받는 곳은 이 컴퓨터(127.0.0.1)의 가짜 서버다. 가짜
 * 서버 주소는 막힌 주소(루프백)라 테스트에서만 그 한 주소를 허용 목록으로 넣는다.
 */
class ImageDownloaderTest {
  private static final String USER_AGENT = "keycheck-test/1.0";
  private static final Duration TIMEOUT = Duration.ofSeconds(2);
  private static final Duration TOTAL_TIMEOUT = Duration.ofSeconds(10);

  /**
   * 막혀야 하는 주소로 보내는 요청의 연결 시간 제한. 검사가 깨져도 실제 내부 주소에 오래 붙지 않게 짧게 두고, 주소에는 아무것도 듣지 않는
   * 포트(9)를 붙여 내부 서비스에 요청이 닿지 않게 한다.
   */
  private static final Duration BLOCKED_TIMEOUT = Duration.ofMillis(200);
  private static final byte[] IMAGE = {(byte) 0x89, 'P', 'N', 'G', 1, 2, 3};

  private final InetAddress serverAddress = literal("127.0.0.1");

  /** 가짜 resolver가 아는 호스트. 모르는 이름은 UnknownHostException */
  private final Map<String, InetAddress[]> hosts = new HashMap<>();

  /** 가짜 resolver에 물어본 호스트 이름 */
  private final List<String> resolved = Collections.synchronizedList(new ArrayList<>());

  /** 가짜 서버가 받은 요청 경로 */
  private final List<String> requested = new CopyOnWriteArrayList<>();

  /** 가짜 서버가 받은 요청마다 User-Agent, Referer */
  private final List<String> userAgents = new CopyOnWriteArrayList<>();

  private final List<String> referers = new CopyOnWriteArrayList<>();

  /** 응답하지 않는 서버를 테스트 끝에 풀어 준다 */
  private final CountDownLatch release = new CountDownLatch(1);

  private HttpServer server;
  private ExecutorService executor;
  private String base;

  private static InetAddress literal(String address) {
    try {
      // 숫자 주소는 형식만 보고 DNS를 묻지 않는다
      return InetAddress.getByName(address);
    } catch (UnknownHostException e) {
      throw new IllegalArgumentException(e);
    }
  }

  @BeforeEach
  void setUp() throws IOException {
    server = HttpServer.create(new InetSocketAddress(serverAddress, 0), 0);
    executor = Executors.newCachedThreadPool();
    server.setExecutor(executor);
    server.start();
    hosts.put("images.test", new InetAddress[] {serverAddress});
    base = "http://images.test:" + server.getAddress().getPort();
    serve("/a.png", 200, IMAGE);
  }

  @AfterEach
  void tearDown() {
    release.countDown();
    server.stop(0);
    executor.shutdownNow();
  }

  /** 운영과 같은 검사를 하되, 호스트 이름은 가짜 resolver로 풀고 가짜 서버 주소 하나만 허용한다 */
  private ImageDownloader downloader() {
    return downloader(TIMEOUT, TOTAL_TIMEOUT, Set.of(serverAddress));
  }

  /** 허용 목록 없이 막힌 주소를 거절하는지 볼 때 쓴다 */
  private ImageDownloader blockingDownloader() {
    return downloader(BLOCKED_TIMEOUT, TOTAL_TIMEOUT, Set.of());
  }

  private ImageDownloader downloader(
      Duration timeout, Duration totalTimeout, Set<InetAddress> allowed) {
    return new ImageDownloader(
        USER_AGENT,
        timeout,
        totalTimeout,
        host -> {
          resolved.add(host);
          InetAddress[] addresses = hosts.get(host);
          if (addresses == null) {
            throw new UnknownHostException(host);
          }
          return addresses;
        },
        allowed);
  }

  private void handle(String path, HttpHandler handler) {
    server.createContext(
        path,
        exchange -> {
          requested.add(exchange.getRequestURI().getPath());
          userAgents.add(exchange.getRequestHeaders().getFirst("User-Agent"));
          referers.add(exchange.getRequestHeaders().getFirst("Referer"));
          handler.handle(exchange);
        });
  }

  private void serve(String path, int status, byte[] body) {
    handle(
        path,
        exchange -> {
          exchange.sendResponseHeaders(status, body.length == 0 ? -1 : body.length);
          try (OutputStream out = exchange.getResponseBody()) {
            out.write(body);
          }
        });
  }

  private void redirect(String path, int status, String location) {
    handle(
        path,
        exchange -> {
          exchange.getResponseHeaders().add("Location", location);
          exchange.sendResponseHeaders(status, -1);
          exchange.close();
        });
  }

  private static void assertBlocked(Throwable thrown) {
    assertThat(thrown).isInstanceOf(ImageFetchException.class);
    assertThat(thrown).rootCause().hasMessageContaining("허용하지 않는 주소");
  }

  @Test
  @DisplayName("원본 바이트를 받고 User-Agent를 붙이며 Referer는 보내지 않는다")
  void downloadsWithUserAgentWithoutReferer() throws IOException {
    try (ImageDownloader downloader = downloader()) {
      assertThat(downloader.download(base + "/a.png")).isEqualTo(IMAGE);
    }

    assertThat(requested).containsExactly("/a.png");
    assertThat(userAgents).containsExactly(USER_AGENT);
    assertThat(referers).containsOnlyNulls();
  }

  @Test
  @DisplayName("호스트는 한 번만 풀고 그때 검사한 주소로 연결한다(다시 풀 때 바뀐 주소를 쓰지 않는다)")
  void resolvesOnceAndConnectsToCheckedAddress() throws IOException {
    InetAddress[] first = {serverAddress};
    InetAddress[] rebound = {literal("169.254.169.254")};
    try (ImageDownloader downloader =
        new ImageDownloader(
            USER_AGENT,
            TIMEOUT,
            TOTAL_TIMEOUT,
            host -> {
              resolved.add(host);
              return resolved.size() == 1 ? first : rebound;
            },
            Set.of(serverAddress))) {
      assertThat(downloader.download(base + "/a.png")).isEqualTo(IMAGE);
    }

    assertThat(resolved).containsExactly("images.test");
    assertThat(requested).containsExactly("/a.png");
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "127.0.0.1",
        "127.10.0.1",
        "10.1.2.3",
        "172.16.0.1",
        "172.31.255.255",
        "192.168.0.10",
        "169.254.169.254",
        "100.64.0.1",
        "100.127.255.254",
        "0.0.0.0",
        "0.1.2.3",
        "224.0.0.1",
        "239.255.255.250",
        "::",
        "::1",
        "fe80::1",
        "febf::1",
        "fc00::1",
        "fd12:3456:789a::1",
        "ff02::1",
        "::a9fe:a9fe",
        "::808:808",
        "64:ff9b::a9fe:a9fe",
        "64:ff9b::a00:1",
        "64:ff9b::7f00:1",
        "64:ff9b:1::808:808"
      })
  @DisplayName(
      "루프백·사설·링크 로컬·CGNAT·0.0.0.0/8·ULA·멀티캐스트·IPv4-compatible·NAT64로 내부 IPv4·NAT64 지역용으로 풀리는 호스트는 연결하지 않고 거절한다")
  void rejectsInternalAddresses(String address) throws IOException {
    hosts.put("blocked.test", new InetAddress[] {literal(address)});

    try (ImageDownloader downloader = blockingDownloader()) {
      assertThatThrownBy(() -> downloader.download("http://blocked.test:9/a.png"))
          .satisfies(ImageDownloaderTest::assertBlocked);
    }
    assertThat(ImageDownloader.isBlocked(literal(address))).isTrue();
  }

  @Test
  @DisplayName("IPv4-mapped IPv6 주소(::ffff:a.b.c.d)는 공인 IPv4를 가리켜도 거절한다")
  void rejectsIpv4MappedIpv6() throws IOException {
    byte[] metadata = new byte[16];
    metadata[10] = (byte) 0xFF;
    metadata[11] = (byte) 0xFF;
    metadata[12] = (byte) 169;
    metadata[13] = (byte) 254;
    metadata[14] = (byte) 169;
    metadata[15] = (byte) 254;
    byte[] publicV4 = metadata.clone();
    publicV4[12] = 8;
    publicV4[13] = 8;
    publicV4[14] = 8;
    publicV4[15] = 8;
    InetAddress mappedMetadata = Inet6Address.getByAddress(null, metadata, -1);
    InetAddress mappedPublic = Inet6Address.getByAddress(null, publicV4, -1);
    hosts.put("mapped.test", new InetAddress[] {mappedMetadata});

    try (ImageDownloader downloader = blockingDownloader()) {
      assertThatThrownBy(() -> downloader.download("http://mapped.test:9/a.png"))
          .satisfies(ImageDownloaderTest::assertBlocked);
    }
    assertThat(mappedMetadata).isInstanceOf(Inet6Address.class);
    assertThat(ImageDownloader.isBlocked(mappedPublic)).isTrue();
  }

  @Test
  @DisplayName("풀린 주소 중 하나라도 막힌 주소면 거절한다")
  void rejectsWhenAnyAddressIsInternal() throws IOException {
    // 192.0.2.0/24는 문서용 대역이라 실제로 닿는 곳이 없다
    hosts.put("mixed.test", new InetAddress[] {literal("192.0.2.10"), literal("10.0.0.1")});

    try (ImageDownloader downloader = blockingDownloader()) {
      assertThatThrownBy(() -> downloader.download("http://mixed.test:9/a.png"))
          .satisfies(ImageDownloaderTest::assertBlocked);
    }
  }

  @Test
  @DisplayName("풀린 주소는 모두 검사하고 앞의 MAX_ADDRESSES개로만 연결한다(닿지 않는 주소가 많아도 작업을 오래 붙잡지 않는다)")
  void connectsToFirstAddressesOnly() throws UnknownHostException {
    // 192.0.2.0/24는 문서용 대역이라 실제로 닿는 곳이 없다. 연결하지 않고 resolver만 본다.
    InetAddress[] many = {
      literal("192.0.2.1"), literal("192.0.2.2"), literal("192.0.2.3"), literal("192.0.2.4")
    };
    InetAddress[] lastBlocked = {literal("192.0.2.1"), literal("192.0.2.2"), literal("10.0.0.1")};
    hosts.put("many.test", many);
    hosts.put("last-blocked.test", lastBlocked);
    ImageDownloader.CheckedDnsResolver resolver =
        new ImageDownloader.CheckedDnsResolver(hosts::get, Set.of());

    assertThat(resolver.resolve("many.test")).containsExactly(many[0], many[1]);
    assertThatThrownBy(() -> resolver.resolve("last-blocked.test"))
        .isInstanceOf(UnknownHostException.class)
        .hasMessageContaining("허용하지 않는 주소");
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "8.8.8.8",
        "1.1.1.1",
        "11.0.0.1",
        "100.63.255.255",
        "100.128.0.1",
        "172.15.255.255",
        "172.32.0.1",
        "169.253.255.255",
        "192.167.255.255",
        "192.169.0.1",
        "223.255.255.255",
        "2606:4700:4700::1111",
        "2a00:1450:4001::1",
        "64:ff9b::808:808"
      })
  @DisplayName("공인 주소는 막지 않는다(DNS64 망에서 공인 IPv4를 가리키는 NAT64 주소 포함)")
  void allowsPublicAddresses(String address) {
    assertThat(ImageDownloader.isBlocked(literal(address))).isFalse();
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "file:///etc/passwd",
        "ftp://images.test/a.png",
        "gopher://images.test/a.png",
        "jar:http://images.test/a.jar!/a.png",
        "//images.test/a.png",
        "http:///a.png",
        "http://exa mple.test/a.png",
        "http://images.test:65536/a.png",
        "http://images.test:99999/a.png",
        "not a url"
      })
  @DisplayName("http·https가 아니거나 잘못된 주소는 호스트를 풀지 않고 거절한다")
  void rejectsNonHttpUrls(String url) throws IOException {
    try (ImageDownloader downloader = downloader()) {
      assertThatThrownBy(() -> downloader.download(url)).isInstanceOf(ImageFetchException.class);
    }
    assertThat(resolved).isEmpty();
    assertThat(requested).isEmpty();
  }

  @Test
  @DisplayName("리다이렉트는 3번까지 직접 따라가고 Referer를 보내지 않는다")
  void followsUpToThreeRedirects() throws IOException {
    redirect("/r1", 302, "/r2");
    redirect("/r2", 301, base + "/r3");
    redirect("/r3", 307, "a.png");

    try (ImageDownloader downloader = downloader()) {
      assertThat(downloader.download(base + "/r1")).isEqualTo(IMAGE);
    }

    assertThat(requested).containsExactly("/r1", "/r2", "/r3", "/a.png");
    assertThat(referers).containsOnlyNulls();
  }

  @Test
  @DisplayName("리다이렉트가 3번을 넘으면 거절한다")
  void rejectsFourthRedirect() throws IOException {
    redirect("/r1", 302, "/r2");
    redirect("/r2", 302, "/r3");
    redirect("/r3", 302, "/r4");
    redirect("/r4", 308, "/a.png");

    try (ImageDownloader downloader = downloader()) {
      assertThatThrownBy(() -> downloader.download(base + "/r1"))
          .isInstanceOf(ImageFetchException.class);
    }
    assertThat(requested).containsExactly("/r1", "/r2", "/r3", "/r4");
  }

  @Test
  @DisplayName("리다이렉트로 메타데이터 주소·다른 루프백 주소·file 주소로 가면 그 요청을 하지 않고 거절한다")
  void rejectsRedirectToInternalAddress() throws IOException {
    hosts.put("metadata.test", new InetAddress[] {literal("169.254.169.254")});
    hosts.put("loopback.test", new InetAddress[] {literal("127.0.0.2")});
    // 검사가 깨져도 내부 서비스에 닿지 않게 아무것도 듣지 않는 포트(9)로 보낸다
    redirect("/to-metadata", 302, "http://metadata.test:9/latest/meta-data/");
    redirect("/to-loopback", 302, "http://loopback.test:9/a.png");
    redirect("/to-file", 302, "file:///etc/passwd");

    try (ImageDownloader downloader = downloader()) {
      assertThatThrownBy(() -> downloader.download(base + "/to-metadata"))
          .satisfies(ImageDownloaderTest::assertBlocked);
      assertThatThrownBy(() -> downloader.download(base + "/to-loopback"))
          .satisfies(ImageDownloaderTest::assertBlocked);
      assertThatThrownBy(() -> downloader.download(base + "/to-file"))
          .isInstanceOf(ImageFetchException.class);
    }
    assertThat(requested).containsExactly("/to-metadata", "/to-loopback", "/to-file");
  }

  @ParameterizedTest
  @CsvSource({
    "geekhack.org, https://geekhack.org/a.png",
    "geekhack.org, /a.png",
    "geekhack.org, http://www.GeekHack.org:{port}/a.png",
    "images.test, http://geekhack.org.:{port}/a.png",
    "images.test, http://cdn.geekhack.org:{port}/a.png"
  })
  @DisplayName("리다이렉트로 geekhack에 가면 그 요청을 하지 않고 거절한다(요청 간격은 호출자가 첫 요청에만 잡는다)")
  void rejectsRedirectToGeekhack(String from, String location) throws IOException {
    int port = server.getAddress().getPort();
    hosts.put("geekhack.org", new InetAddress[] {serverAddress});
    redirect("/r", 302, location.replace("{port}", String.valueOf(port)));

    try (ImageDownloader downloader = downloader()) {
      assertThatThrownBy(() -> downloader.download("http://" + from + ":" + port + "/r"))
          .isInstanceOf(ImageFetchException.class)
          .hasMessageContaining("geekhack으로 가는 리다이렉트");
    }
    assertThat(requested).containsExactly("/r");
    assertThat(resolved).containsExactly(from);
  }

  @ParameterizedTest
  @ValueSource(strings = {"evilgeekhack.org", "geekhack.org.evil.test"})
  @DisplayName("geekhack을 닮은 다른 호스트로 가는 리다이렉트는 따라간다")
  void followsRedirectToLookalikeHost(String host) throws IOException {
    hosts.put(host, new InetAddress[] {serverAddress});
    redirect("/r", 302, "http://" + host + ":" + server.getAddress().getPort() + "/a.png");

    try (ImageDownloader downloader = downloader()) {
      assertThat(downloader.download(base + "/r")).isEqualTo(IMAGE);
    }
    assertThat(requested).containsExactly("/r", "/a.png");
  }

  @Test
  @DisplayName("리다이렉트 주소의 포트가 범위를 넘으면 그 요청을 하지 않고 거절한다")
  void rejectsRedirectToOutOfRangePort() throws IOException {
    redirect("/to-bad-port", 302, "http://images.test:70000/a.png");

    try (ImageDownloader downloader = downloader()) {
      assertThatThrownBy(() -> downloader.download(base + "/to-bad-port"))
          .isInstanceOf(ImageFetchException.class);
    }
    assertThat(requested).containsExactly("/to-bad-port");
  }

  @Test
  @DisplayName("리다이렉트에 주소가 없으면 거절한다")
  void rejectsRedirectWithoutLocation() throws IOException {
    handle(
        "/r1",
        exchange -> {
          exchange.sendResponseHeaders(302, -1);
          exchange.close();
        });

    try (ImageDownloader downloader = downloader()) {
      assertThatThrownBy(() -> downloader.download(base + "/r1"))
          .isInstanceOf(ImageFetchException.class);
    }
  }

  @ParameterizedTest
  @ValueSource(ints = {404, 403, 500, 503})
  @DisplayName("4xx·5xx 응답은 ImageFetchException")
  void errorStatusFails(int status) throws IOException {
    serve("/error", status, "<html>오류</html>".getBytes());

    try (ImageDownloader downloader = downloader()) {
      assertThatThrownBy(() -> downloader.download(base + "/error"))
          .isInstanceOf(ImageFetchException.class);
    }
    assertThat(requested).containsExactly("/error");
  }

  @Test
  @Timeout(10)
  @DisplayName("오류 응답·리다이렉트를 여러 번 받아도 연결을 붙잡아 두지 않는다")
  void releasesConnectionsAfterRejectedResponses() throws IOException {
    serve("/error", 404, "<html>오류</html>".getBytes());
    redirect("/r1", 302, "/a.png");

    try (ImageDownloader downloader = downloader()) {
      // 한 호스트에 동시에 빌릴 수 있는 연결(기본 5개)보다 많이 부른다. 돌려주지 않으면 연결을 기다리며 멈춘다.
      for (int i = 0; i < 12; i++) {
        assertThatThrownBy(() -> downloader.download(base + "/error"))
            .isInstanceOf(ImageFetchException.class);
        assertThat(downloader.download(base + "/r1")).isEqualTo(IMAGE);
      }
    }
  }

  @Test
  @DisplayName("MAX_BYTES까지는 받는다")
  void downloadsUpToMaxBytes() throws IOException {
    serve("/max", 200, new byte[ImageDownloader.MAX_BYTES]);

    try (ImageDownloader downloader = downloader()) {
      assertThat(downloader.download(base + "/max")).hasSize(ImageDownloader.MAX_BYTES);
    }
  }

  @Test
  @Timeout(30)
  @DisplayName("MAX_BYTES를 넘으면 끝까지 받지 않고 읽기를 끊는다")
  void stopsReadingOverMaxBytes() throws IOException {
    handle(
        "/endless",
        exchange -> {
          // 길이를 알리지 않고(chunked) 끝없이 보낸다. 받는 쪽이 연결을 끊으면 쓰기가 실패해 끝난다.
          exchange.sendResponseHeaders(200, 0);
          byte[] chunk = new byte[64 * 1024];
          try (OutputStream out = exchange.getResponseBody()) {
            while (true) {
              out.write(chunk);
            }
          } catch (IOException e) {
            // 받는 쪽이 끊었다
          }
        });

    try (ImageDownloader downloader = downloader()) {
      assertThatThrownBy(() -> downloader.download(base + "/endless"))
          .isInstanceOf(ImageFetchException.class)
          .hasMessageContaining("MB");
    }
  }

  @Test
  @DisplayName("길이가 MAX_BYTES를 넘는다고 알리면 본문을 읽지 않고 거절한다")
  void rejectsDeclaredLengthOverMaxBytes() throws IOException {
    handle(
        "/huge",
        exchange -> {
          exchange.sendResponseHeaders(200, ImageDownloader.MAX_BYTES + 1L);
          try (OutputStream out = exchange.getResponseBody()) {
            out.write(new byte[1024]);
            release.await(10, TimeUnit.SECONDS);
          } catch (IOException | InterruptedException e) {
            // 받는 쪽이 끊었다
          }
        });

    try (ImageDownloader downloader = downloader()) {
      assertThatThrownBy(() -> downloader.download(base + "/huge"))
          .isInstanceOf(ImageFetchException.class)
          .hasMessageContaining("MB");
    }
  }

  @Test
  @Timeout(10)
  @DisplayName("응답이 없으면 시간 제한에 걸려 ImageFetchException")
  void readTimeoutFails() throws IOException {
    handle(
        "/silent",
        exchange -> {
          try {
            release.await(10, TimeUnit.SECONDS);
          } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
          }
          exchange.close();
        });

    try (ImageDownloader downloader =
        downloader(Duration.ofMillis(300), TOTAL_TIMEOUT, Set.of(serverAddress))) {
      assertThatThrownBy(() -> downloader.download(base + "/silent"))
          .isInstanceOf(ImageFetchException.class);
    }
  }

  @Test
  @Timeout(10)
  @DisplayName("조금씩 보내 읽기 시간 제한에 걸리지 않아도 전체 시간 제한을 넘으면 끊는다")
  void totalTimeoutFails() throws IOException {
    handle(
        "/drip",
        exchange -> {
          exchange.sendResponseHeaders(200, 0);
          try (OutputStream out = exchange.getResponseBody()) {
            for (int i = 0; i < 200; i++) {
              out.write(1);
              out.flush();
              Thread.sleep(50);
            }
          } catch (IOException | InterruptedException e) {
            // 받는 쪽이 끊었다
          }
        });

    try (ImageDownloader downloader =
        downloader(Duration.ofSeconds(2), Duration.ofMillis(500), Set.of(serverAddress))) {
      assertThatThrownBy(() -> downloader.download(base + "/drip"))
          .isInstanceOf(ImageFetchException.class);
    }
  }

  /**
   * HttpServer로는 보낼 수 없는 응답을 보내는 가짜 서버. 요청을 받으면 head를 보낸 뒤 연결이 끊길 때까지 drip을 100ms마다 보낸다.
   * 계속 무언가 오므로 읽기 시간 제한에 걸리지 않는다.
   */
  private ServerSocket dripServer(String head, String drip) throws IOException {
    ServerSocket listener = new ServerSocket(0, 1, serverAddress);
    executor.submit(
        () -> {
          try (Socket socket = listener.accept();
              OutputStream out = socket.getOutputStream()) {
            out.write(head.getBytes(StandardCharsets.US_ASCII));
            while (!release.await(100, TimeUnit.MILLISECONDS)) {
              out.write(drip.getBytes(StandardCharsets.US_ASCII));
              out.flush();
            }
          } catch (IOException | InterruptedException e) {
            // 받는 쪽이 끊었다
          }
          return null;
        });
    return listener;
  }

  @Test
  // 끊지 못하면 소켓 읽기에 막혀 같은 스레드의 시간 제한으로는 테스트가 끝나지 않는다
  @Timeout(value = 10, threadMode = ThreadMode.SEPARATE_THREAD)
  @DisplayName("응답 머리 전에 1xx만 계속 보내 응답을 기다리게 해도 전체 시간 제한에 끊는다")
  void totalTimeoutWhileWaitingForResponse() throws IOException {
    try (ServerSocket listener = dripServer("", "HTTP/1.1 102 Processing\r\n\r\n");
        ImageDownloader downloader =
            downloader(Duration.ofSeconds(2), Duration.ofMillis(500), Set.of(serverAddress))) {
      String url = "http://images.test:" + listener.getLocalPort() + "/processing";
      assertThatThrownBy(() -> downloader.download(url))
          .isInstanceOf(ImageFetchException.class)
          .hasMessageContaining("시간");
    }
  }

  @Test
  @Timeout(value = 10, threadMode = ThreadMode.SEPARATE_THREAD)
  @DisplayName("한 번 읽기 안에서 막혀도(끝나지 않는 chunk 길이 줄) 전체 시간 제한에 끊는다")
  void totalTimeoutWithinSingleRead() throws IOException {
    try (ServerSocket listener =
            dripServer("HTTP/1.1 200 OK\r\nTransfer-Encoding: chunked\r\n\r\n", "0");
        ImageDownloader downloader =
            downloader(Duration.ofSeconds(2), Duration.ofMillis(500), Set.of(serverAddress))) {
      String url = "http://images.test:" + listener.getLocalPort() + "/chunk";
      assertThatThrownBy(() -> downloader.download(url))
          .isInstanceOf(ImageFetchException.class)
          .hasMessageContaining("시간");
    }
  }
}

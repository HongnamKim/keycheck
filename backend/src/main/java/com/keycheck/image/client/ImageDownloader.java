package com.keycheck.image.client;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.time.Duration;
import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import org.apache.hc.client5.http.DnsResolver;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.util.Timeout;

/**
 * 축소본을 만들 원본 이미지를 받는다 (설계 3.2). 간격 제한은 직접 쓰지 않는다. geekhack 이미지는 호출자가 RequestGate를 통과한 뒤
 * 부른다.
 *
 * <p>이미지 주소는 글 작성자가 마음대로 쓰는 값이므로 서버 안쪽(클라우드 메타데이터 169.254.169.254, 사설망, 루프백)으로 가는 요청을
 * 막는다.
 *
 * <ul>
 *   <li>http·https 주소만 받는다.
 *   <li>호스트를 풀어 막힌 주소(isBlocked)가 하나라도 있으면 거절하고, 그때 검사한 주소로만 연결한다. 연결할 때 다시 풀지 않으므로 검사
 *       뒤에 DNS 응답을 바꿔도(리바인딩) 소용없다.
 *   <li>리다이렉트는 자동으로 따라가지 않고 MAX_REDIRECTS번까지 직접 따라가며 매번 같은 검사를 한다.
 *   <li>리다이렉트로 geekhack에 가는 요청은 하지 않는다. 호출자는 첫 주소로만 요청 간격을 잡으므로, 따라가면 간격 제한을 우회한다.
 *   <li>Referer를 보내지 않는다.
 * </ul>
 *
 * <p>응답 압축은 풀지 않는다. 이미지는 풀 필요가 없고, geekhack 첨부는 비표준 "Content-Encoding: none"을 보내 풀려고 하면
 * HttpClient가 거절한다. 미리 gzip으로 압축해 둔 이미지(Content-Encoding: gzip)는 압축된 바이트를 그대로 받으므로 읽을 수 없는
 * 이미지로 센다.
 *
 * <p>한 이미지 받기 전체의 시간 제한은 요청 밖에서 지킨다. 1xx 응답만 계속 오거나 한 번 읽기 안에서 조금씩 오면 사이사이 검사가 돌지
 * 않으므로, 마감에 다른 스레드가 요청을 취소해 연결을 끊는다. 다만 연결 중에 끊으면 HttpClient가 호스트의 남은 주소로 연결을 이어
 * 시도하므로, 연결할 주소를 MAX_ADDRESSES개로 줄여 둔다. 그래서 많아야 연결 시간 제한 한 번만큼 더 걸린 뒤 실패한다.
 */
public class ImageDownloader implements AutoCloseable {
  /** 받을 수 있는 원본 최대 바이트. 넘으면 읽기를 끊는다. */
  public static final int MAX_BYTES = 25 * 1024 * 1024;

  /** 따라갈 리다이렉트 최대 횟수 */
  static final int MAX_REDIRECTS = 3;

  /** 한 호스트에서 연결을 시도할 주소 최대 개수 */
  static final int MAX_ADDRESSES = 2;

  /** URI는 포트 범위를 보지 않는다 */
  private static final int MAX_PORT = 65535;

  private static final String GEEKHACK_HOST = "geekhack.org";

  /** NAT64 잘 알려진 접두어 64:ff9b::/96. 뒤 4바이트가 IPv4다. */
  private static final byte[] NAT64_PREFIX = {0, 0x64, (byte) 0xFF, (byte) 0x9B, 0, 0, 0, 0, 0, 0, 0, 0};

  /** NAT64 지역용 접두어 64:ff9b:1::/48. 망마다 접두어 길이가 달라 IPv4가 담긴 자리를 알 수 없으므로 통째로 막는다. */
  private static final byte[] NAT64_LOCAL_PREFIX = {0, 0x64, (byte) 0xFF, (byte) 0x9B, 0, 1};

  private final CloseableHttpClient client;
  private final Duration totalTimeout;

  /** 마감에 요청을 취소한다. 끝난 요청의 예약은 바로 지운다. */
  private final ScheduledThreadPoolExecutor deadlines;

  /**
   * @param timeout 연결·읽기(한 번 읽을 때 기다리는) 시간 제한
   * @param totalTimeout 리다이렉트를 포함한 한 이미지 받기 전체의 시간 제한. 조금씩 보내 읽기 시간 제한에 걸리지 않는 응답이 작업을 붙잡지
   *     않게 한다.
   */
  public ImageDownloader(String userAgent, Duration timeout, Duration totalTimeout) {
    this(userAgent, timeout, totalTimeout, InetAddress::getAllByName, Set.of());
  }

  /**
   * @param resolver 호스트 이름을 주소로 푼다. 운영은 시스템 DNS, 테스트는 가짜.
   * @param allowed 막힌 주소라도 연결할 주소. 테스트가 가짜 서버(127.0.0.1)를 넣을 때만 쓴다. 운영은 비어 있다.
   */
  ImageDownloader(
      String userAgent,
      Duration timeout,
      Duration totalTimeout,
      HostResolver resolver,
      Set<InetAddress> allowed) {
    this.totalTimeout = totalTimeout;
    this.deadlines =
        new ScheduledThreadPoolExecutor(
            1, Thread.ofPlatform().name("image-download-deadline").daemon().factory());
    this.deadlines.setRemoveOnCancelPolicy(true);
    this.client =
        HttpClients.custom()
            .setConnectionManager(
                PoolingHttpClientConnectionManagerBuilder.create()
                    .setDnsResolver(new CheckedDnsResolver(resolver, allowed))
                    .setDefaultConnectionConfig(
                        ConnectionConfig.custom()
                            .setConnectTimeout(Timeout.of(timeout))
                            .setSocketTimeout(Timeout.of(timeout))
                            .build())
                    .build())
            .setUserAgent(userAgent)
            // 리다이렉트는 download가 직접 따라가며 주소를 검사한다
            .disableRedirectHandling()
            // 서버가 보낸 Retry-After만큼 기다렸다 다시 보내지 않는다. 실패는 작업이 다음 차례에 다시 고른다.
            .disableAutomaticRetries()
            // Accept-Encoding을 보내지 않고 응답을 풀지 않는다(이유는 클래스 주석)
            .disableContentCompression()
            .build();
  }

  /**
   * @throws ImageFetchException 받지 못했다(주소 형식, 막힌 주소, 4xx·5xx, 시간 초과, 크기 초과). 이미지 탓으로 센다.
   */
  public byte[] download(String url) {
    long deadline = System.nanoTime() + totalTimeout.toNanos();
    URI uri = checked(toUri(url));
    for (int redirects = 0; ; redirects++) {
      checkDeadline(deadline, uri);
      HttpGet request = new HttpGet(uri);
      ScheduledFuture<?> timer =
          deadlines.schedule(request::cancel, deadline - System.nanoTime(), TimeUnit.NANOSECONDS);
      try {
        ClassicHttpResponse response = client.executeOpen(null, request, null);
        int status = response.getCode();
        if (isRedirect(status)) {
          if (redirects == MAX_REDIRECTS) {
            throw new ImageFetchException("리다이렉트가 너무 많다: " + url);
          }
          Header location = response.getFirstHeader(HttpHeaders.LOCATION);
          if (location == null) {
            throw new ImageFetchException("리다이렉트할 주소가 없다: " + uri);
          }
          uri = checked(uri.resolve(toUri(location.getValue())));
          if (isGeekhack(uri)) {
            throw new ImageFetchException("geekhack으로 가는 리다이렉트는 따라가지 않는다: " + uri);
          }
          continue;
        }
        if (status < 200 || status >= 300) {
          throw new ImageFetchException("응답 상태 " + status + ": " + uri);
        }
        return read(response.getEntity(), deadline, uri);
      } catch (IOException e) {
        if (request.isCancelled()) {
          throw new ImageFetchException(tooSlow(uri), e);
        }
        throw new ImageFetchException("원본을 받지 못했다: " + uri, e);
      } catch (IllegalStateException e) {
        // 마감에 취소한 요청은 HttpClient 안에서 "이미 놓은 연결"(IllegalStateException)로도 끝난다.
        // 놓치면 실패로 세지 못해 작업이 같은 이미지를 계속 다시 고른다.
        if (request.isCancelled()) {
          throw new ImageFetchException(tooSlow(uri), e);
        }
        throw e;
      } finally {
        timer.cancel(false);
        // 응답을 그냥 닫으면 남은 본문(큰 이미지, 오류 페이지)을 끝까지 받으므로 연결을 끊는다.
        // 본문을 끝까지 읽었으면 연결은 이미 풀에 돌아가 있어 아무것도 하지 않는다.
        request.cancel();
      }
    }
  }

  private byte[] read(HttpEntity entity, long deadline, URI uri) throws IOException {
    if (entity == null) {
      throw new ImageFetchException("본문이 없다: " + uri);
    }
    if (entity.getContentLength() > MAX_BYTES) {
      throw new ImageFetchException(tooLarge(uri));
    }
    InputStream in = entity.getContent();
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    byte[] buffer = new byte[64 * 1024];
    int count;
    while ((count = in.read(buffer)) != -1) {
      if (out.size() + count > MAX_BYTES) {
        throw new ImageFetchException(tooLarge(uri));
      }
      checkDeadline(deadline, uri);
      out.write(buffer, 0, count);
    }
    return out.toByteArray();
  }

  private static String tooLarge(URI uri) {
    return MAX_BYTES / (1024 * 1024) + "MB를 넘는 이미지: " + uri;
  }

  private static String tooSlow(URI uri) {
    return "받는 시간이 너무 길다: " + uri;
  }

  private static void checkDeadline(long deadline, URI uri) {
    if (System.nanoTime() - deadline > 0) {
      throw new ImageFetchException(tooSlow(uri));
    }
  }

  private static boolean isRedirect(int status) {
    return status == 301 || status == 302 || status == 303 || status == 307 || status == 308;
  }

  private static URI toUri(String url) {
    try {
      return new URI(url);
    } catch (URISyntaxException e) {
      throw new ImageFetchException("주소 형식이 잘못됐다: " + url, e);
    }
  }

  /** http·https이고 호스트가 있으며 포트가 범위 안인 주소만 받는다 */
  private static URI checked(URI uri) {
    String scheme = uri.getScheme();
    boolean http = "http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme);
    if (!http || uri.getHost() == null) {
      throw new ImageFetchException("http·https 이미지 주소가 아니다: " + uri);
    }
    // 범위를 넘는 포트는 HttpClient가 IllegalArgumentException으로 거절해 실패로 세지 못한다
    if (uri.getPort() > MAX_PORT) {
      throw new ImageFetchException("포트가 범위를 넘는 주소: " + uri);
    }
    return uri;
  }

  /** geekhack(geekhack.org와 그 하위 호스트)에 있는 주소. geekhack 요청 간격을 거쳐야 한다. */
  public static boolean isGeekhack(URI uri) {
    String host = uri.getHost();
    if (host == null) {
      return false;
    }
    host = host.toLowerCase(Locale.ROOT);
    // 끝에 점이 붙은 이름(geekhack.org.)도 같은 호스트다
    if (host.endsWith(".")) {
      host = host.substring(0, host.length() - 1);
    }
    return host.equals(GEEKHACK_HOST) || host.endsWith("." + GEEKHACK_HOST);
  }

  /**
   * 외부 이미지 호스트로 볼 수 없는 주소. 0.0.0.0/8과 ::, 루프백, 사설(10/8, 172.16/12, 192.168/16), 링크 로컬(169.254/16,
   * fe80::/10), CGNAT(100.64/10), IPv6 ULA(fc00::/7)와 옛 사이트 로컬(fec0::/10), IPv4-mapped·IPv4-compatible IPv6, NAT64
   * 지역용 접두어(64:ff9b:1::/48), 멀티캐스트. NAT64 잘 알려진 접두어(64:ff9b::/96)는 담긴 IPv4를 같은 규칙으로 본다.
   */
  static boolean isBlocked(InetAddress address) {
    if (address.isAnyLocalAddress()
        || address.isLoopbackAddress()
        || address.isSiteLocalAddress()
        || address.isLinkLocalAddress()
        || address.isMulticastAddress()) {
      return true;
    }
    byte[] bytes = address.getAddress();
    if (address instanceof Inet4Address) {
      int first = bytes[0] & 0xFF;
      int second = bytes[1] & 0xFF;
      return first == 0 || (first == 100 && second >= 64 && second <= 127);
    }
    if (startsWith(bytes, NAT64_PREFIX)) {
      // DNS64가 있는 망에서는 IPv4만 있는 호스트도 이 주소로 풀리므로 통째로 막지 않고, NAT64가 연결할 IPv4를 본다
      return isBlocked(ipv4(Arrays.copyOfRange(bytes, 12, 16)));
    }
    return (bytes[0] & 0xFE) == 0xFC || isIpv4Embedded(bytes) || startsWith(bytes, NAT64_LOCAL_PREFIX);
  }

  private static boolean startsWith(byte[] bytes, byte[] prefix) {
    return Arrays.equals(bytes, 0, prefix.length, prefix, 0, prefix.length);
  }

  private static InetAddress ipv4(byte[] bytes) {
    try {
      return InetAddress.getByAddress(bytes);
    } catch (UnknownHostException e) {
      throw new IllegalArgumentException(e); // 4바이트면 나지 않는다
    }
  }

  /**
   * ::ffff:a.b.c.d(IPv4-mapped)와 ::a.b.c.d(IPv4-compatible). 운영체제가 IPv4로 연결하거나 더는 쓰지 않는 형식이라 IPv4 검사를 피해
   * 갈 수 있어 통째로 막는다.
   */
  private static boolean isIpv4Embedded(byte[] bytes) {
    for (int i = 0; i < 10; i++) {
      if (bytes[i] != 0) {
        return false;
      }
    }
    return (bytes[10] == (byte) 0xFF && bytes[11] == (byte) 0xFF) || (bytes[10] == 0 && bytes[11] == 0);
  }

  @Override
  public void close() throws IOException {
    deadlines.shutdownNow();
    client.close();
  }

  /** 호스트 이름을 주소로 푼다 */
  @FunctionalInterface
  interface HostResolver {
    InetAddress[] resolve(String host) throws UnknownHostException;
  }

  /**
   * HttpClient가 연결할 때마다 부른다. 돌려준 주소로만 연결한다. 풀린 주소는 모두 검사하고 앞의 MAX_ADDRESSES개만 돌려준다. HttpClient는
   * 연결하지 못하면(마감에 취소해도) 다음 주소로 이어 가므로, 닿지 않는 주소를 많이 주는 호스트가 작업을 오래 붙잡지 못하게 한다.
   */
  @RequiredArgsConstructor
  static class CheckedDnsResolver implements DnsResolver {
    private final HostResolver resolver;
    private final Set<InetAddress> allowed;

    @Override
    public InetAddress[] resolve(String host) throws UnknownHostException {
      InetAddress[] addresses = resolver.resolve(host);
      // 비어 있으면 HttpClient가 이름을 그대로 소켓에 넘겨 검사 없이 다시 풀게 되므로 여기서 끝낸다
      if (addresses == null || addresses.length == 0) {
        throw new UnknownHostException("주소를 찾지 못한 호스트: " + host);
      }
      for (InetAddress address : addresses) {
        if (isBlocked(address) && !allowed.contains(address)) {
          throw new UnknownHostException(
              "허용하지 않는 주소(루프백·사설·링크 로컬 등)로 풀리는 호스트: "
                  + host
                  + " -> "
                  + address.getHostAddress());
        }
      }
      return Arrays.copyOf(addresses, Math.min(MAX_ADDRESSES, addresses.length));
    }

    /** 인증(SPNEGO)에만 쓰이고 우리는 쓰지 않는다. 이름을 풀지 않고 그대로 둔다. */
    @Override
    public String resolveCanonicalHostname(String host) {
      return host;
    }
  }
}

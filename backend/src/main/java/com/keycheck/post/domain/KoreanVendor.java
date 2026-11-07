package com.keycheck.post.domain;

import java.net.URI;
import java.text.Normalizer;
import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 알려진 한국 벤더. 요약기는 원문 표기를 따라 지역을 KR이 아닌 값(원문이 South Korea를 SK로 적은 경우 등)으로 옮기거나 판매 링크를
 * 짝짓지 못하기도 해서, 이름이나 판매 사이트 주소로도 한국 벤더를 알아본다.
 *
 * <p>이름은 대소문자·전각·띄어쓰기·점·하이픈과 괄호 속 표기("Swagkeys (KR)")를 무시하고 통째로 비교한다. 일부만 같은 이름("Geonix")은
 * 걸리지 않는다. 주소는 그 사이트이거나 하위 도메인(www 등)일 때만 걸린다.
 */
public enum KoreanVendor {
  GEONWORKS(Set.of("geon", "geonwork", "geonworks", "건웍스", "건웍"), Set.of("geon.works", "geonworks.kr")),
  SWAGKEYS(Set.of("swagkeys", "swagkey", "스웩키", "스웨그키"), Set.of("swagkeys.com", "swagkey.kr")),
  KLC_PLAYGROUND(Set.of("klc", "klcplayground"), Set.of("klc-playground.com"));

  /** 괄호와 그 안의 글자(지역 표기 등) */
  private static final Pattern PARENTHESES = Pattern.compile("\\(.*?\\)");

  /** 글자·숫자가 아닌 것(띄어쓰기, 점, 하이픈 등) */
  private static final Pattern NOT_LETTER_OR_DIGIT = Pattern.compile("[^\\p{L}\\p{N}]");

  /** 비교용으로 다듬은 이름 */
  private final Set<String> names;

  /** 판매 사이트 호스트(소문자) */
  private final Set<String> hosts;

  KoreanVendor(Set<String> names, Set<String> hosts) {
    this.names = names;
    this.hosts = hosts;
  }

  /** 이름이 알려진 한국 벤더면 그 벤더 */
  public static Optional<KoreanVendor> byName(String name) {
    if (name == null) {
      return Optional.empty();
    }
    String key = normalize(name);
    return Arrays.stream(values()).filter(vendor -> vendor.names.contains(key)).findFirst();
  }

  /** 판매 링크가 알려진 한국 벤더 사이트면 그 벤더 */
  public static Optional<KoreanVendor> byUrl(String url) {
    return Arrays.stream(values()).filter(vendor -> vendor.owns(url)).findFirst();
  }

  /** 이 벤더의 판매 사이트(하위 도메인 포함) 주소인지. 주소로 읽을 수 없으면 false */
  public boolean owns(String url) {
    String host = host(url);
    return host != null
        && hosts.stream().anyMatch(own -> host.equals(own) || host.endsWith("." + own));
  }

  private static String normalize(String name) {
    // 전각 글자(ＧＥＯＮ, （KR）)와 자모가 나뉜 한글을 보통 글자로 맞춘다
    String compatible = Normalizer.normalize(name, Normalizer.Form.NFKC);
    String withoutParentheses = PARENTHESES.matcher(compatible).replaceAll("");
    return NOT_LETTER_OR_DIGIT.matcher(withoutParentheses).replaceAll("").toLowerCase(Locale.ROOT);
  }

  /** http(s) 주소의 소문자 호스트. 아니면 null */
  private static String host(String url) {
    if (url == null || url.isBlank()) {
      return null;
    }
    try {
      URI uri = URI.create(url.strip());
      String scheme = uri.getScheme();
      if (scheme == null
          || !(scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"))
          || uri.getHost() == null) {
        return null;
      }
      return uri.getHost().toLowerCase(Locale.ROOT);
    } catch (IllegalArgumentException e) {
      return null;
    }
  }
}

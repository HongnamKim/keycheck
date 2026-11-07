package com.keycheck.post.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PostedAtEstimatorTest {
  private static final Instant T0 = Instant.parse("2026-01-01T00:00:00Z");
  private static final Instant FAR_FUTURE = Instant.parse("2100-01-01T00:00:00Z");

  private static Instant hours(long hours) {
    return T0.plus(Duration.ofHours(hours));
  }

  /** 글 100은 T0, 글 200은 T0+100시간: 글 번호 하나에 1시간 */
  private final PostedAtEstimator estimator =
      new PostedAtEstimator(Map.of(100L, T0, 200L, hours(100)));

  @Test
  @DisplayName("앞뒤 기준점 사이는 글 번호 비율로 보간한다")
  void interpolates() {
    assertThat(estimator.estimate(150, null, FAR_FUTURE)).isEqualTo(hours(50));
    assertThat(estimator.estimate(101, null, FAR_FUTURE)).isEqualTo(hours(1));
  }

  @Test
  @DisplayName("뒤쪽에 기준점이 없으면 앞쪽 가장 가까운 기준점에서 가장 먼 기준점과의 기울기로 외삽한다")
  void extrapolatesAfter() {
    assertThat(estimator.estimate(250, null, FAR_FUTURE)).isEqualTo(hours(150));
  }

  @Test
  @DisplayName("앞쪽에 기준점이 없으면 뒤쪽 가장 가까운 기준점에서 가장 먼 기준점과의 기울기로 외삽한다")
  void extrapolatesBefore() {
    assertThat(estimator.estimate(50, null, FAR_FUTURE)).isEqualTo(hours(-50));
  }

  @Test
  @DisplayName("외삽 기울기는 가장 가까운 두 기준점이 아니라 그쪽 끝 기준점까지의 전체 기울기다")
  void extrapolatesWithOverallSlope() {
    // 100·101은 같은 시각(가까운 두 점의 기울기 0)이지만 100~200 전체로는 글 번호 하나에 1시간
    PostedAtEstimator older =
        new PostedAtEstimator(Map.of(100L, T0, 101L, T0, 200L, hours(100)));
    assertThat(older.estimate(50, null, FAR_FUTURE)).isEqualTo(hours(-50));

    // 199·200은 7분 차이(가까운 두 점의 기울기 7분)지만 100~200 전체로는 글 번호 하나에 1시간
    PostedAtEstimator newer =
        new PostedAtEstimator(
            Map.of(100L, T0, 199L, hours(100).minus(Duration.ofMinutes(7)), 200L, hours(100)));
    assertThat(newer.estimate(300, null, FAR_FUTURE)).isEqualTo(hours(200));
  }

  @Test
  @DisplayName("기준점이 없거나 한쪽에 하나뿐이면 null")
  void nullWhenNotEnoughAnchors() {
    assertThat(new PostedAtEstimator(Map.of()).estimate(150, null, FAR_FUTURE)).isNull();

    PostedAtEstimator one = new PostedAtEstimator(Map.of(100L, T0));
    assertThat(one.estimate(150, null, FAR_FUTURE)).isNull();
    assertThat(one.estimate(50, null, FAR_FUTURE)).isNull();
  }

  @Test
  @DisplayName("마지막 게시물 시각보다 늦으면 그 시각으로 자른다")
  void capsAtLastPostAt() {
    assertThat(estimator.estimate(150, hours(10), FAR_FUTURE)).isEqualTo(hours(10));
    assertThat(estimator.estimate(150, hours(60), FAR_FUTURE)).isEqualTo(hours(50));
  }

  @Test
  @DisplayName("지금보다 늦으면 지금으로 자른다")
  void capsAtNow() {
    assertThat(estimator.estimate(250, null, hours(120))).isEqualTo(hours(120));
    assertThat(estimator.estimate(250, hours(130), hours(120))).isEqualTo(hours(120));
  }

  @Test
  @DisplayName("기준점이 같은 시각이면 보간은 그 시각이고, 외삽은 기울기가 0이라 null")
  void sameTimeAnchors() {
    PostedAtEstimator same = new PostedAtEstimator(Map.of(100L, T0, 200L, T0));

    assertThat(same.estimate(150, null, FAR_FUTURE)).isEqualTo(T0);
    assertThat(same.estimate(300, null, FAR_FUTURE)).isNull();
    assertThat(same.estimate(50, null, FAR_FUTURE)).isNull();
  }

  @Test
  @DisplayName("글 번호가 클수록 이른 이상 데이터면 외삽은 null이고, 보간은 두 시각 사이다")
  void reversedAnchors() {
    PostedAtEstimator reversed = new PostedAtEstimator(Map.of(100L, hours(100), 200L, T0));

    assertThat(reversed.estimate(300, null, FAR_FUTURE)).isNull();
    assertThat(reversed.estimate(50, null, FAR_FUTURE)).isNull();
    assertThat(reversed.estimate(150, null, FAR_FUTURE)).isEqualTo(hours(50));
  }

  @Test
  @DisplayName("기울기가 아주 커도 예외 없이 지금으로 자른다")
  void hugeSlopeDoesNotThrow() {
    PostedAtEstimator steep =
        new PostedAtEstimator(Map.of(1L, Instant.parse("1970-01-01T00:00:00Z"), 2L, T0));

    assertThat(steep.estimate(Long.MAX_VALUE, null, FAR_FUTURE)).isEqualTo(FAR_FUTURE);
  }
}

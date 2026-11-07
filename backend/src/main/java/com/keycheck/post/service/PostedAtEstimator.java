package com.keycheck.post.service;

import java.time.Instant;
import java.util.Map;
import java.util.Map.Entry;
import java.util.NavigableMap;
import java.util.TreeMap;

/**
 * 작성일이 없는 글(틀 글)의 작성일을 글 번호로 추정한다. geekhack 글 번호는 작성 순서대로 늘어나므로 작성일을 아는 글(기준점)
 * 사이를 글 번호 비율로 보간하고, 한쪽에만 기준점이 있으면 그쪽의 가장 가까운 기준점과 가장 먼 기준점의 기울기(전체 속도)로 외삽한다.
 * 가까운 두 기준점만 쓰면 번호가 붙어 있을 때 국소 기울기가 수천 번호에 걸쳐 늘어나 몇 년씩 틀어진다.
 */
public class PostedAtEstimator {
  /** 글 번호 → 작성일 */
  private final NavigableMap<Long, Instant> anchors;

  public PostedAtEstimator(Map<Long, Instant> anchors) {
    this.anchors = new TreeMap<>(anchors);
  }

  /**
   * @param lastPostAt 마지막 게시물 시각. 글은 이보다 늦게 쓰였을 수 없다. null이면 이 상한은 없다.
   * @param now 지금. 이보다 늦은 값은 지금으로 자른다.
   * @return 기준점이 모자라면(양쪽에 하나씩도, 한쪽에 둘도 없으면) null. 외삽 기울기가 0 이하여도 null
   */
  public Instant estimate(long topicId, Instant lastPostAt, Instant now) {
    Entry<Long, Instant> before = anchors.lowerEntry(topicId);
    Entry<Long, Instant> after = anchors.higherEntry(topicId);
    Instant estimate;
    if (before != null && after != null) {
      estimate = along(before, millisPerTopic(before, after), topicId);
    } else if (before != null) {
      estimate = extrapolate(anchors.firstEntry(), before, topicId);
    } else if (after != null) {
      estimate = extrapolate(anchors.lastEntry(), after, topicId);
    } else {
      return null;
    }
    if (estimate == null) {
      return null;
    }

    if (lastPostAt != null && estimate.isAfter(lastPostAt)) {
      estimate = lastPostAt;
    }
    return estimate.isAfter(now) ? now : estimate;
  }

  /** farthest가 nearest와 같거나(기준점 하나), 기울기가 0 이하(같은 시각이거나 글 번호가 클수록 이른 이상 데이터)면 null. */
  private static Instant extrapolate(
      Entry<Long, Instant> farthest, Entry<Long, Instant> nearest, long topicId) {
    if (farthest.getKey().equals(nearest.getKey())) {
      return null;
    }
    double millisPerTopic = millisPerTopic(farthest, nearest);
    return millisPerTopic > 0 ? along(nearest, millisPerTopic, topicId) : null;
  }

  /** 글 번호 하나가 늘 때 늘어나는 작성일(밀리초). 두 점의 순서와 상관없다. */
  private static double millisPerTopic(Entry<Long, Instant> a, Entry<Long, Instant> b) {
    return (double) (b.getValue().toEpochMilli() - a.getValue().toEpochMilli())
        / (b.getKey() - a.getKey());
  }

  /** from에서 기울기를 따라 topicId까지 간 시각. 아주 멀어도 Math.round가 long 범위에서 멈춰 예외가 나지 않는다. */
  private static Instant along(Entry<Long, Instant> from, double millisPerTopic, long topicId) {
    return from.getValue()
        .plusMillis(Math.round(millisPerTopic * ((double) topicId - from.getKey())));
  }
}

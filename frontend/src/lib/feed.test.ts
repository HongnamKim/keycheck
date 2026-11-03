import { describe, expect, it } from "vitest";
import type { PostCard } from "./api";
import {
  applyBatch,
  fromSnapshot,
  otherSnapshotKeys,
  readSnapshot,
  snapshotKey,
  startFeed,
  type FeedState,
} from "./feed";

function card(id: number): PostCard {
  return {
    id,
    title: `글 ${id}`,
    category: "KEYBOARD",
    subcategory: null,
    representativeImageUrl: null,
    summaryStatus: "PENDING",
    summaryFirstLine: null,
    views: 10,
    replies: 1,
    statsCheckedAt: "2026-11-03T00:00:00Z",
    postedAt: null,
    estimatedPostedAt: null,
    lastPostAt: null,
    detailLoaded: false,
  };
}

const ids = (state: FeedState) => state.items.map((p) => p.id);

describe("startFeed", () => {
  it("첫 묶음으로 시작한다. 더 있으면 idle", () => {
    const state = startFeed({ items: [card(3), card(2)], nextCursor: "c1", hasMore: true });
    expect(ids(state)).toEqual([3, 2]);
    expect(state).toMatchObject({ nextCursor: "c1", hasMore: true, emptyStreak: 0, status: "idle" });
  });

  it("첫 묶음이 0건이고 더 없으면 end (수집된 글 없음)", () => {
    expect(startFeed({ items: [], nextCursor: null, hasMore: false })).toMatchObject({ items: [], status: "end" });
  });

  it("첫 묶음이 0건인데 더 있으면 waiting (잠시 뒤 다시 요청)", () => {
    expect(startFeed({ items: [], nextCursor: "c0", hasMore: true })).toMatchObject({ status: "waiting", emptyStreak: 1 });
  });
});

describe("applyBatch", () => {
  const base = startFeed({ items: [card(5), card(4)], nextCursor: "c1", hasMore: true });

  it("다음 묶음을 뒤에 붙이고 이미 있는 id는 뺀다", () => {
    const next = applyBatch(base, { items: [card(4), card(3)], nextCursor: "c2", hasMore: true });
    expect(ids(next)).toEqual([5, 4, 3]);
    expect(next).toMatchObject({ nextCursor: "c2", status: "idle" });
  });

  it("더 없으면 end", () => {
    expect(applyBatch(base, { items: [card(1)], nextCursor: null, hasMore: false }).status).toBe("end");
  });

  it("0건 + 더 있음이면 받은 nextCursor로 기다리고, 연속 3번째 0건이면 failed", () => {
    const empty = { items: [], nextCursor: "c1", hasMore: true };
    const first = applyBatch(base, empty);
    expect(first).toMatchObject({ status: "waiting", emptyStreak: 1, nextCursor: "c1" });
    const second = applyBatch(first, empty);
    expect(second).toMatchObject({ status: "waiting", emptyStreak: 2 });
    expect(applyBatch(second, empty)).toMatchObject({ status: "failed", emptyStreak: 3 });
  });

  it("글을 받으면 0건 연속 횟수를 0으로 되돌린다", () => {
    const waiting = applyBatch(base, { items: [], nextCursor: "c1", hasMore: true });
    expect(applyBatch(waiting, { items: [card(3)], nextCursor: "c2", hasMore: true })).toMatchObject({
      status: "idle",
      emptyStreak: 0,
    });
  });

  it("nextCursor가 null이어도 더 있으면 그대로 둔다 (다음 요청은 처음부터)", () => {
    const next = applyBatch(base, { items: [card(3)], nextCursor: null, hasMore: true });
    expect(next).toMatchObject({ nextCursor: null, hasMore: true, status: "idle" });
  });
});

describe("snapshotKey", () => {
  it("목록 주소마다 따로 저장한다", () => {
    expect(snapshotKey("/")).not.toBe(snapshotKey("/?category=keycap"));
  });
});

describe("otherSnapshotKeys", () => {
  it("지금 목록이 아닌 다른 목록의 저장본 키만 고른다 (다른 앱의 키는 건드리지 않는다)", () => {
    const keys = [snapshotKey("/"), snapshotKey("/?category=keycap"), "other:key"];
    expect(otherSnapshotKeys(keys, snapshotKey("/"))).toEqual([snapshotKey("/?category=keycap")]);
  });
});

describe("readSnapshot", () => {
  const now = Date.parse("2026-11-03T12:00:00Z");
  const saved = { items: [card(2)], nextCursor: "c1", hasMore: true, scrollY: 1200, savedAt: now - 10 * 60_000 };

  it("30분 이내 저장본은 그대로 읽는다", () => {
    expect(readSnapshot(JSON.stringify(saved), now)).toEqual(saved);
  });

  it("30분이 지난 저장본은 버린다", () => {
    expect(readSnapshot(JSON.stringify({ ...saved, savedAt: now - 31 * 60_000 }), now)).toBeNull();
  });

  it.each([[null], ["not json"], ["null"], [JSON.stringify({ ...saved, items: "x" })], [JSON.stringify({ ...saved, scrollY: "1" })]])(
    "없거나 깨진 값 %j → null",
    (raw) => {
      expect(readSnapshot(raw, now)).toBeNull();
    },
  );
});

describe("fromSnapshot", () => {
  it("저장본으로 이어서 시작한다", () => {
    const state = fromSnapshot({ items: [card(2)], nextCursor: "c1", hasMore: true, scrollY: 0, savedAt: 0 });
    expect(state).toMatchObject({ nextCursor: "c1", hasMore: true, emptyStreak: 0, status: "idle" });
    expect(ids(state)).toEqual([2]);
  });

  it("끝까지 본 저장본이면 end", () => {
    expect(fromSnapshot({ items: [], nextCursor: null, hasMore: false, scrollY: 0, savedAt: 0 }).status).toBe("end");
  });
});

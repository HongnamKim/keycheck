import type { PostCard } from "./api";

/** 한 묶음. PostBatch, SaleBatch가 모두 맞는다 */
interface FeedBatch<T> {
  items: T[];
  nextCursor: string | null;
  hasMore: boolean;
}

/**
 * 무한 스크롤 목록의 상태.
 * - idle: 더 받을 수 있음(끝의 감시 요소가 보이면 다음 묶음 요청)
 * - loading: 요청 중
 * - waiting: 0건을 받아 잠시 뒤 같은 cursor로 다시 요청
 * - failed: 요청 실패 또는 연속 0건. 다시 시도 버튼을 보여준다
 * - end: 마지막 글까지 받음
 */
export type FeedStatus = "idle" | "loading" | "waiting" | "failed" | "end";

export interface FeedState<T extends { id: number } = PostCard> {
  items: T[];
  nextCursor: string | null;
  hasMore: boolean;
  /** 연속으로 0건을 받은 횟수 */
  emptyStreak: number;
  status: FeedStatus;
}

/** 이만큼 연속으로 0건이면 자동 재요청을 멈추고 다시 시도 버튼을 보여준다 */
const MAX_EMPTY_STREAK = 3;

/** 받은 묶음을 붙인다. 이미 있는 id는 뺀다. 다음 요청은 늘 받은 nextCursor로 한다. */
export function applyBatch<T extends { id: number }>(state: FeedState<T>, batch: FeedBatch<T>): FeedState<T> {
  const seen = new Set(state.items.map((p) => p.id));
  const items = [...state.items, ...batch.items.filter((p) => !seen.has(p.id))];
  const emptyStreak = batch.items.length === 0 && batch.hasMore ? state.emptyStreak + 1 : 0;

  let status: FeedStatus = "idle";
  if (!batch.hasMore) status = "end";
  else if (emptyStreak >= MAX_EMPTY_STREAK) status = "failed";
  else if (emptyStreak > 0) status = "waiting";

  return { items, nextCursor: batch.nextCursor, hasMore: batch.hasMore, emptyStreak, status };
}

/** 서버가 받은 첫 묶음으로 시작한다 */
export function startFeed<T extends { id: number }>(batch: FeedBatch<T>): FeedState<T> {
  return applyBatch({ items: [], nextCursor: null, hasMore: true, emptyStreak: 0, status: "idle" }, batch);
}

/** 상세에 갔다 돌아올 때 이어 보도록 sessionStorage에 남기는 목록 */
export interface FeedSnapshot<T extends { id: number } = PostCard> {
  items: T[];
  nextCursor: string | null;
  hasMore: boolean;
  scrollY: number;
  /** 저장한 시각(ms) */
  savedAt: number;
}

const SNAPSHOT_MAX_AGE_MS = 30 * 60_000;

const SNAPSHOT_PREFIX = "keycheck:feed:";

/** 목록 주소(listHref)마다 따로 저장한다 */
export function snapshotKey(listHref: string): string {
  return `${SNAPSHOT_PREFIX}${listHref}`;
}

/** 저장소 키 가운데 모든 목록의 저장본 키. 메뉴·전체 보기로 목록에 새로 들어갈 때 지운다 */
export function snapshotKeys(keys: string[]): string[] {
  return keys.filter((k) => k.startsWith(SNAPSHOT_PREFIX));
}

/** 저장소 키 가운데 지금 목록(current)이 아닌 다른 목록의 저장본 키 */
export function otherSnapshotKeys(keys: string[], current: string): string[] {
  return keys.filter((k) => k.startsWith(SNAPSHOT_PREFIX) && k !== current);
}

/** 저장본을 읽는다. 없거나 깨졌거나 30분이 지났으면 null */
export function readSnapshot<T extends { id: number } = PostCard>(raw: string | null, now: number): FeedSnapshot<T> | null {
  if (raw === null) return null;
  let value: unknown;
  try {
    value = JSON.parse(raw);
  } catch {
    return null;
  }
  if (typeof value !== "object" || value === null) return null;

  const s = value as FeedSnapshot<T>;
  const valid =
    Array.isArray(s.items) &&
    (s.nextCursor === null || typeof s.nextCursor === "string") &&
    typeof s.hasMore === "boolean" &&
    typeof s.scrollY === "number" &&
    typeof s.savedAt === "number";
  if (!valid || now - s.savedAt > SNAPSHOT_MAX_AGE_MS) return null;
  return s;
}

/** 저장본으로 이어서 시작한다 */
export function fromSnapshot<T extends { id: number }>(snapshot: FeedSnapshot<T>): FeedState<T> {
  return {
    items: snapshot.items,
    nextCursor: snapshot.nextCursor,
    hasMore: snapshot.hasMore,
    emptyStreak: 0,
    status: snapshot.hasMore ? "idle" : "end",
  };
}

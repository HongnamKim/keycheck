import type { SaleCard, SaleVendor } from "./api";
import { kstDateKey } from "./format";
import { DAY_MS, OPEN_ENDED_DAYS, dayNumber, endInstant, instant } from "./saleStatus";

/** 공지 하나에서 나온 판매 건들 */
export interface NoticeGroup {
  noticeId: number;
  vendor: SaleVendor;
  /** 공지 시각(ISO) */
  postedAt: string;
  items: SaleCard[];
}

/** 공지 순 보기의 하루. day는 한국 날짜 "YYYY-MM-DD" */
export interface DayGroup {
  day: string;
  notices: NoticeGroup[];
}

/** 같은 공지인지 가르는 키. noticeId가 없으면 판매처와 공지 시각으로 대신한다 */
function noticeKey(item: SaleCard): string {
  return String(item.noticeId ?? `${item.vendor}|${item.postedAt}`);
}

/**
 * 공지 순 보기(설계 3.7 A). 입력 순서(공지 최근 순)를 지키며 한국 날짜별로, 그 안에서 같은 공지끼리 묶는다.
 * 이어 받은 페이지를 붙인 목록을 넣으면 페이지 경계에서 이어지는 같은 공지도 한 묶음이 된다.
 */
export function groupByNotice(items: SaleCard[]): DayGroup[] {
  const days: DayGroup[] = [];
  for (const item of items) {
    const day = kstDateKey(new Date(item.postedAt));
    let current = days.at(-1);
    if (current?.day !== day) {
      current = { day, notices: [] };
      days.push(current);
    }
    const notice = current.notices.at(-1);
    if (notice && noticeKey(notice.items[0]) === noticeKey(item)) {
      notice.items.push(item);
    } else {
      current.notices.push({ noticeId: item.noticeId, vendor: item.vendor, postedAt: item.postedAt, items: [item] });
    }
  }
  return days;
}

const WEEKDAYS = ["일", "월", "화", "수", "목", "금", "토"];

/** 날짜 머리 글자: 오늘, 어제, 그 밖에는 "11월 6일 금요일". today는 서버가 받은 시각의 한국 날짜 */
export function dayLabel(day: string, today: string): string {
  const diff = dayNumber(today) - dayNumber(day);
  if (diff === 0) return "오늘";
  if (diff === 1) return "어제";
  const [year, month, date] = day.split("-").map(Number);
  const weekday = WEEKDAYS[new Date(Date.UTC(year, month - 1, date)).getUTCDay()];
  return `${month}월 ${date}일 ${weekday}요일`;
}

export type ScheduleBucketKey = "closing" | "today" | "tomorrow" | "week" | "later" | "onSale" | "tbd";

export interface ScheduleBucket {
  key: ScheduleBucketKey;
  label: string;
  items: SaleCard[];
}

/** 화면에 놓는 순서와 라벨 */
const BUCKETS: [ScheduleBucketKey, string][] = [
  ["closing", "곧 마감"],
  ["today", "오늘"],
  ["tomorrow", "내일"],
  ["week", "이번 주"],
  ["later", "다음 주 이후"],
  ["onSale", "판매 중"],
  ["tbd", "일정 미정"],
];

/** 곧 마감으로 보는 남은 기간, 일정 미정을 남기는 공지 뒤 기간 */
const CLOSING_MS = 7 * DAY_MS;
const ANNOUNCE_DAYS = 30;

/**
 * 끝난 판매(서버 판매 일정 API와 같은 기준). 마감이 있으면 마감(날짜만이면 23:59:59)이 지금 이전.
 * 마감이 없으면 시작 뒤 7일, 시작도 없으면 공지 뒤 7일(예고는 30일)이 지났을 때.
 */
function isOver(sale: SaleCard, nowMs: number): boolean {
  const end = endInstant(sale);
  if (end !== null) return end <= nowMs;
  if (sale.startDate) return instant(sale.startDate, sale.startTime) + OPEN_ENDED_DAYS * DAY_MS <= nowMs;
  const days = sale.phase === "ANNOUNCE" ? ANNOUNCE_DAYS : OPEN_ENDED_DAYS;
  return new Date(sale.postedAt).getTime() + days * DAY_MS <= nowMs;
}

/** 어느 구간인지. 시작했는지는 saleBadge와 같게 본다(날짜만 아는 시작은 그날이 지나야 시작) */
function bucketOf(sale: SaleCard, nowMs: number, today: string): ScheduleBucketKey {
  if (sale.startDate) {
    const started = sale.startTime ? nowMs >= instant(sale.startDate, sale.startTime) : sale.startDate < today;
    if (!started) {
      const days = dayNumber(sale.startDate) - dayNumber(today);
      if (days <= 0) return "today";
      if (days === 1) return "tomorrow";
      if (days <= 6) return "week";
      return "later";
    }
  } else if (sale.phase === "ANNOUNCE") {
    return "tbd";
  }
  const end = endInstant(sale);
  return end !== null && end - nowMs <= CLOSING_MS ? "closing" : "onSale";
}

/** 시작 순간. 날짜만 알면 그날 시각을 아는 건들 뒤에 둔다(설계 3.8과 같다) */
function startOrder(date: string, time: string | null): number {
  return time ? instant(date, time) : instant(date, "23:59") + 59_000;
}

/** 구간 안 정렬 기준: 곧 마감은 마감, 시작 구간은 시작, 판매 중은 시작(없으면 공지), 일정 미정은 공지 */
function orderOf(sale: SaleCard, key: ScheduleBucketKey): number {
  const posted = new Date(sale.postedAt).getTime();
  if (key === "closing") return endInstant(sale) ?? posted;
  if (key === "tbd" || !sale.startDate) return posted;
  return startOrder(sale.startDate, sale.startTime);
}

/**
 * 판매 일정 순 보기(설계 3.7 B). now는 서버가 목록을 받은 시각.
 * 끝난 건은 빼고 곧 마감 → 오늘 → 내일 → 이번 주 → 다음 주 이후 → 판매 중 → 일정 미정으로 나눈다. 빈 구간은 뺀다.
 * 구간 안은 기준 시각 이른 순, 같으면 입력 순서.
 */
export function scheduleBuckets(items: SaleCard[], now: Date): ScheduleBucket[] {
  const nowMs = now.getTime();
  const today = kstDateKey(now);
  const byKey = new Map<ScheduleBucketKey, SaleCard[]>();
  for (const sale of items) {
    if (isOver(sale, nowMs)) continue;
    const key = bucketOf(sale, nowMs, today);
    const list = byKey.get(key);
    if (list) list.push(sale);
    else byKey.set(key, [sale]);
  }
  return BUCKETS.filter(([key]) => byKey.has(key)).map(([key, label]) => ({
    key,
    label,
    items: byKey.get(key)!.sort((a, b) => orderOf(a, key) - orderOf(b, key)),
  }));
}

/**
 * 상세 아래 이어서 보기(판매 일정 순). 이 판매 건이 든 구간부터, 그 구간은 이 건부터 남기고 뒤 구간은 그대로 둔다.
 * 어느 구간에도 없으면(지난 판매) 빈 목록.
 */
export function scheduleFrom(buckets: ScheduleBucket[], saleId: number): ScheduleBucket[] {
  const at = buckets.findIndex((bucket) => bucket.items.some((item) => item.id === saleId));
  if (at < 0) return [];
  const first = buckets[at];
  const start = first.items.findIndex((item) => item.id === saleId);
  return [{ ...first, items: first.items.slice(start) }, ...buckets.slice(at + 1)];
}

/**
 * 지난 판매의 정렬 기준 시각(ms). 서버 SaleTimes.pastAt과 같은 규칙: 마감(날짜만이면 23:59:59),
 * 없으면 시작(날짜만이면 0시), 없으면 공지 시각.
 */
export function pastAtMillis(sale: SaleCard): number {
  const end = endInstant(sale);
  if (end !== null) return end;
  if (sale.startDate) return instant(sale.startDate, sale.startTime);
  return new Date(sale.postedAt).getTime();
}

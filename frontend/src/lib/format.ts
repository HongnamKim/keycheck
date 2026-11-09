import type { Board, BoardFilter, Category, Subcategory } from "./api";
import { subOptions, type ListQuery } from "./query";

/**
 * 조회수 표기 (설계 5장)
 * - 1,000 미만: 그대로 (999)
 * - 1만 미만: 앞 두 자리만 남기고 내림 (3,400+)
 * - 1만 이상: 만 단위 소수 첫째 자리까지 내림 (1.2만+, 8만+)
 */
export function formatViews(views: number): string {
  if (views < 1_000) return String(views);
  if (views < 10_000) return `${(Math.floor(views / 100) * 100).toLocaleString("en-US")}+`;

  const tenthsOfMan = Math.floor(views / 1_000); // 만 단위 × 10
  const whole = Math.floor(tenthsOfMan / 10).toLocaleString("en-US");
  const decimal = tenthsOfMan % 10;
  return decimal === 0 ? `${whole}만+` : `${whole}.${decimal}만+`;
}

/** 지난 시간을 상대 시간으로 표기 (예: 방금, 5분 전, 3시간 전, 2일 전). now보다 늦은 시각도 방금으로 본다 */
export function formatAgo(date: Date, now: Date = new Date()): string {
  const minutes = Math.floor((now.getTime() - date.getTime()) / 60_000);
  if (minutes < 1) return "방금";
  if (minutes < 60) return `${minutes}분 전`;

  const hours = Math.floor(minutes / 60);
  if (hours < 24) return `${hours}시간 전`;

  return `${Math.floor(hours / 24)}일 전`;
}

/** 조회수를 마지막으로 확인한 시각을 상대 시간으로 표기 (예: 3시간 전 확인) */
export function formatCheckedAt(checkedAt: Date, now: Date = new Date()): string {
  return `${formatAgo(checkedAt, now)} 확인`;
}

const KST_OFFSET_MS = 9 * 60 * 60 * 1_000;

/** 원문 작성일을 한국 시간 날짜로 표기 (예: 2026.10.30) */
export function formatPostedAt(iso: string): string {
  const kst = new Date(new Date(iso).getTime() + KST_OFFSET_MS);
  const pad = (n: number) => String(n).padStart(2, "0");
  return `${kst.getUTCFullYear()}.${pad(kst.getUTCMonth() + 1)}.${pad(kst.getUTCDate())}`;
}

/** 한국 시간 오늘 날짜(예: 2026-11-07). 서버가 그린 시각에서 구해 화면에 문자열로 넘긴다 */
export function kstDateKey(now: Date): string {
  return new Date(now.getTime() + KST_OFFSET_MS).toISOString().slice(0, 10);
}

/** 시각을 한국 시간 HH:mm으로 표기 (예: 18:52). 공지 시각 표시용 */
export function formatKstTime(iso: string): string {
  return new Date(new Date(iso).getTime() + KST_OFFSET_MS).toISOString().slice(11, 16);
}

/** 시각을 한국 시간 짧은 월/일로 표기 (예: 11/1). 카드 메타의 날짜 */
export function formatShortDate(iso: string): string {
  const kst = new Date(new Date(iso).getTime() + KST_OFFSET_MS);
  return `${kst.getUTCMonth() + 1}/${kst.getUTCDate()}`;
}

const WEEKDAYS = ["일", "월", "화", "수", "목", "금", "토"];

/** "YYYY-MM-DD" 날짜(시간대 없음)를 나눠 읽는다. new Date(문자열)는 시간대에 따라 하루 밀릴 수 있어 쓰지 않는다 */
function dateParts(date: string): { month: number; day: number; weekday: string } {
  const [year, month, day] = date.split("-").map(Number);
  return { month, day, weekday: WEEKDAYS[new Date(Date.UTC(year, month - 1, day)).getUTCDay()] };
}

/** 메인 날짜 줄의 날짜 (예: 2026-11-07 → 2026.11.07 토) */
export function formatDateline(today: string): string {
  return `${today.split("-").join(".")} ${dateParts(today).weekday}`;
}

/** "YYYY-MM-DD" 날짜를 월/일로 표기 (예: 11/13, withWeekday면 11/13(금)) */
export function formatMonthDay(date: string, withWeekday = false): string {
  const { month, day, weekday } = dateParts(date);
  return withWeekday ? `${month}/${day}(${weekday})` : `${month}/${day}`;
}

/**
 * 작성일 표기. 정확한 작성일(postedAt)이 있으면 그것을, 없으면 글 번호로 추정한 날짜에 "쯤"을 붙인다.
 * 둘 다 없으면 null.
 */
export function formatPostedDate(
  postedAt: string | null,
  estimatedPostedAt: string | null,
): { text: string; estimated: boolean } | null {
  if (postedAt) return { text: `${formatPostedAt(postedAt)} 작성`, estimated: false };
  if (estimatedPostedAt) return { text: `${formatPostedAt(estimatedPostedAt)}쯤 작성`, estimated: true };
  return null;
}

/**
 * 메인 가로 줄 카드의 짧은 작성일 (예: 11/1). 정확한 작성일이 없는 틀 글은 추정 날짜에 "쯤"을 붙이고(11/1쯤),
 * 둘 다 없으면 null.
 */
export function formatShortPostedDate(postedAt: string | null, estimatedPostedAt: string | null): string | null {
  if (postedAt) return formatShortDate(postedAt);
  if (estimatedPostedAt) return `${formatShortDate(estimatedPostedAt)}쯤`;
  return null;
}

/** 마지막 댓글 시각 (예: 마지막 댓글 3일 전). 댓글이 없으면 마지막 게시물이 원글이라 null */
export function formatLastPost(lastPostAt: string | null, replies: number, now: Date = new Date()): string | null {
  if (!lastPostAt || replies <= 0) return null;
  return `마지막 댓글 ${formatAgo(new Date(lastPostAt), now)}`;
}

const CATEGORY_LABELS: Record<Category, string> = {
  KEYBOARD: "키보드",
  KEYCAP: "키캡",
  OTHER: "기타",
  UNCLASSIFIED: "미분류",
};

export function categoryLabel(category: Category): string {
  return CATEGORY_LABELS[category];
}

const SUBCATEGORY_LABELS: Record<Subcategory, string> = {
  KB_100: "100%",
  KB_96: "96%·1800",
  KB_80: "80%",
  KB_75: "75%",
  KB_65: "65%",
  KB_60: "60%",
  KB_40: "50% 이하",
  KB_ERGO: "앨리스·스플릿",
  KB_ETC: "그 외",
  KC_GMK: "GMK",
  KC_SP: "SP",
  KC_MW: "MW",
  KC_KKB: "KKB",
  KC_PBTFANS: "PBTfans",
  KC_ETC: "그 외",
};

export function subcategoryLabel(subcategory: Subcategory): string {
  return SUBCATEGORY_LABELS[subcategory];
}

/** 카드·상세의 분류 표시 (예: 키보드 · 65%). 세부 분류가 없으면 분류만 */
export function postCategoryLabel(category: Category, subcategory: Subcategory | null): string {
  return subcategory ? `${categoryLabel(category)} · ${subcategoryLabel(subcategory)}` : categoryLabel(category);
}

/**
 * 메인 사진 위 칩 글자 (예: 60%, SP, 키캡). 세부 분류가 있으면 그것만, "그 외"면 분류를 쓴다.
 * 미분류(틀 글)면 null이라 칩을 그리지 않는다.
 */
export function photoChipLabel(category: Category, subcategory: Subcategory | null): string | null {
  if (category === "UNCLASSIFIED") return null;
  if (subcategory && subcategory !== "KB_ETC" && subcategory !== "KC_ETC") return subcategoryLabel(subcategory);
  return categoryLabel(category);
}

/** 제목 앞의 게시판 표시(예: [IC], (GB), [Interest Check])와 뒤 공백. "[GB][IC]"처럼 여럿이어도 모두 */
const BOARD_MARK = /^\s*(?:[[(]\s*(?:IC|GB|Interest Check|Group Buy)\s*[\])]\s*)+/i;

/**
 * 메인(벤토·가로 줄)에 쓰는 짧은 제목. 요약기가 뽑은 상품 이름(productName)이 있으면 그것을,
 * 없으면 원래 제목에서 첫 " | " 뒤(날짜 등)를 자르고 앞의 게시판 표시를 뗀다. " - "는 이름의 일부일 수 있어 두고,
 * 다듬은 결과가 비면 원래 제목을 쓴다. 목록 페이지와 상세는 원래 제목 그대로다.
 */
export function displayTitle(productName: string | null | undefined, title: string): string {
  const name = productName?.trim();
  if (name) return name;
  // 자른 뒤에 떼야 "[GB] | 날짜"처럼 표시만 남는 제목이 빈 결과가 된다
  const trimmed = title.split(/\s\|\s/)[0].replace(BOARD_MARK, "").trim();
  return trimmed || title;
}

/** 글마다 붙는 게시판 배지 글자 */
export function boardLabel(board: Board): string {
  return board === "GB" ? "공동구매" : "IC";
}

/** 게시판 목록의 제목과 한 줄 설명 (승인된 시안 문구) */
export const BOARD_INFO: Record<BoardFilter, { title: string; desc: string }> = {
  ic: { title: "IC", desc: "관심 조사 글이에요. 아직 판매 전이라 의견을 모으는 단계예요." },
  gb: { title: "공동구매", desc: "주문을 받는 중이거나 곧 받을 글이에요. 기간과 벤더를 확인해 보세요." },
};

/** 국내 판매 목록(/sales)의 제목과 설명. 화면과 검색 결과용 설명이 같다 */
export const SALE_LIST_INFO = {
  title: "국내 판매",
  desc: "Swagkeys·GeonWorks 디스코드 공지에서 판매 소식만 골라 정리했어요.",
};

/** 보던 목록의 이름 (예: 공동구매 목록, IC · 키보드 · 65% · 조회순 목록). 상세 하단 주변 글 목록의 제목 */
export function listTitle(query: ListQuery): string {
  const parts = [BOARD_INFO[query.board].title];
  if (query.category !== "all") parts.push(categoryLabel(query.category === "keyboard" ? "KEYBOARD" : "KEYCAP"));
  const sub = subOptions(query.category).find((option) => option.sub === query.sub);
  if (sub) parts.push(subcategoryLabel(sub.code));
  if (query.sort === "views") parts.push("조회순");
  return `${parts.join(" · ")} 목록`;
}

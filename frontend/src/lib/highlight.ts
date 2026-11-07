import type { Board, HighlightSlot, HighlightSlotKind, PostCard } from "./api";
import { boardLabel } from "./format";
import { imageSources } from "./image";

/** 띄어쓰기 자리에서 줄이 갈리지 않게 붙인다(시안 &nbsp; 자리) */
const NBSP = " ";

/**
 * 칸마다 고른 게시판, 순위 기준, 키커에 "최근"을 붙이는지.
 * 넓은 칸(큰 칸·세로 칸·마감 띠 대체)은 "최근"을 붙이고, 작은 칸은 시안처럼 뺀다.
 */
const SLOT_RULES: Record<HighlightSlotKind, { board: Board; metric: string; wide: boolean }> = {
  FEATURE: { board: "GB", metric: "조회", wide: true },
  TALL: { board: "IC", metric: "조회", wide: true },
  DEADLINE: { board: "GB", metric: "조회", wide: true },
  IC_REPLIES: { board: "IC", metric: "댓글", wide: false },
  IC_VIEWS: { board: "IC", metric: "조회", wide: false },
  GB_VIEWS: { board: "GB", metric: "조회", wide: false },
};

/** 고른 창 이름. 14일은 시안처럼 2주, 전체로 넓혔으면(null) 전체 기간 */
function windowLabel(windowDays: number | null, wide: boolean): string {
  if (windowDays === null) return "전체 기간";
  const days = windowDays === 14 ? "2주" : `${windowDays}일`;
  return wide ? `최근${NBSP}${days}` : days;
}

/**
 * 칸 키커 토막(시안 .mc-segs). 예: ["최근 7일 새 공동구매", "조회 1위"], 작은 칸 ["2주 새 IC", "댓글 1위"].
 * 마감 띠는 ["곧 끝나는 공동구매"] 한 토막.
 */
export function slotKicker(slot: HighlightSlot): string[] {
  if (slot.deadline) return ["곧 끝나는 공동구매"];
  const rule = SLOT_RULES[slot.slot];
  const segments = [`${windowLabel(slot.windowDays, rule.wide)} 새 ${boardLabel(rule.board)}`];
  if (slot.rank !== null) segments.push(`${rule.metric}${NBSP}${slot.rank}위`);
  return segments;
}

/**
 * 하이라이트 배치. 여섯 칸이 다 있으면 시안 벤토, 칸이 빠졌으면 남은 칸을 같은 크기 격자로,
 * 하나도 없으면 빈 상태 문구(설계 3.2 "그 칸은 비우고 화면은 남은 칸으로 그린다").
 */
export function bentoLayout(slots: HighlightSlot[]): "bento" | "grid" | "empty" {
  if (slots.length === 0) return "empty";
  return slots.length === 6 && slots.some((slot) => slot.slot === "DEADLINE") ? "bento" : "grid";
}

/**
 * 칸 이미지 주소. 큰 칸(large)은 1280 축소본, 없으면 640 축소본, 그것도 없으면 원본을 읽는다.
 * 나머지 칸은 640 축소본, 없으면 원본. 축소본을 못 읽으면 원본으로 바꾼다. 대표 이미지가 없으면 null.
 */
export function bentoImage(post: PostCard, large: boolean): { src: string; fallback: string | null } | null {
  if (!post.representativeImageUrl) return null;
  const thumb = (large && post.representativeLargeUrl) || post.representativeThumbUrl;
  return imageSources(thumb, post.representativeImageUrl);
}

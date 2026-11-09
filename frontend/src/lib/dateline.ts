import type { Highlights } from "./api";

/** 띄어쓰기 자리에서 줄이 갈리지 않게 붙인다(시안 &nbsp; 자리) */
const NBSP = " ";

/** 날짜 줄 숫자 토막 하나(예: GB 5) */
export interface DatelineCount {
  label: string;
  count: number;
}

/**
 * 메인 날짜 줄의 최근 7일 현황 토막: geekhack IC · GB · 국내 판매. 날짜 줄은 현황만 보여 주므로 공동구매를 GB로 줄여 쓴다.
 * 받지 못한 쪽(하이라이트 null, 국내 판매 수 undefined)은 0으로 채우지 않고 빼며, "최근 7일"은 남은 첫 토막 앞에 붙인다.
 */
export function datelineCounts(
  highlights: Pick<Highlights, "newIcCount" | "newGbCount"> | null,
  saleCount: number | undefined,
): DatelineCount[] {
  const counts: DatelineCount[] = [];
  if (highlights) {
    counts.push({ label: "geekhack IC", count: highlights.newIcCount }, { label: "GB", count: highlights.newGbCount });
  }
  if (saleCount !== undefined) counts.push({ label: "국내 판매", count: saleCount });
  if (counts.length > 0) counts[0].label = `최근${NBSP}7일 ${counts[0].label}`;
  return counts;
}

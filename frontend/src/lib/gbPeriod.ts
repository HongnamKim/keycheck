import { formatMonthDay } from "./format";

/**
 * 공동구매 기간 표시. 날짜는 모두 "YYYY-MM-DD"(시간대 없음)이고, today는 서버가 구한 한국 시간 오늘이다.
 * 마감일을 모르면 D-day를 단정하지 않도록 아무것도 그리지 않는다(설계 2장).
 */

const DAY_MS = 24 * 60 * 60 * 1_000;

/** 1970-01-01부터 센 날 수. 시간대 없이 날짜만 비교한다 */
function dayNumber(date: string): number {
  const [year, month, day] = date.split("-").map(Number);
  return Date.UTC(year, month - 1, day) / DAY_MS;
}

/** 마감일까지 남은 날 수(당일 0). 마감이 지났거나 마감일을 모르면 null */
export function gbDaysLeft(endDate: string | null, today: string): number | null {
  if (!endDate) return null;
  const days = dayNumber(endDate) - dayNumber(today);
  return days < 0 ? null : days;
}

/**
 * 기간 칩. range는 "10/30 → 11/13"(시작일을 모르면 "~ 11/13", 마감이 지났으면 끝에 " 끝남"),
 * dday는 마감 전이면 "D-8"·"D-DAY", 지났으면 null. 마감일을 모르면 null.
 * 가로 줄 카드 칩, 큰 칸 장부(withWeekday), 마감 띠 메타가 함께 쓴다.
 */
export function gbPeriodChip(
  startDate: string | null,
  endDate: string | null,
  today: string,
  withWeekday = false,
): { range: string; dday: string | null; ended: boolean } | null {
  if (!endDate) return null;
  const end = formatMonthDay(endDate, withWeekday);
  const range = startDate ? `${formatMonthDay(startDate, withWeekday)} → ${end}` : `~ ${end}`;
  const days = gbDaysLeft(endDate, today);
  if (days === null) return { range: `${range} 끝남`, dday: null, ended: true };
  return { range, dday: days === 0 ? "D-DAY" : `D-${days}`, ended: false };
}

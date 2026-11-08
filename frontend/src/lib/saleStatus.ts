import type { SaleCard, SalePhase, SaleType, SaleVendor } from "./api";
import { formatKstTime, formatMonthDay, kstDateKey } from "./format";

/** 상태 배지 모양: 강조색 채움, 진한 테두리, 회색 테두리, 회색 점선 */
export type BadgeTone = "fill" | "line" | "soft" | "dash";

type Schedule = Pick<SaleCard, "phase" | "startDate" | "startTime" | "endDate" | "endTime" | "postedAt">;

const HOUR_MS = 60 * 60 * 1_000;
export const DAY_MS = 24 * HOUR_MS;
const KST_OFFSET_MS = 9 * HOUR_MS;
/** 마감 없는 판매를 이 기간까지만 판매 중으로 본다 */
export const OPEN_ENDED_DAYS = 7;

/** 한국 날짜("YYYY-MM-DD")와 시각("HH:mm")이 가리키는 순간(ms). 시각을 모르면 그날 0시 */
export function instant(date: string, time: string | null): number {
  const [year, month, day] = date.split("-").map(Number);
  const [hour, minute] = (time ?? "00:00").split(":").map(Number);
  return Date.UTC(year, month - 1, day, hour, minute) - KST_OFFSET_MS;
}

/** 1970-01-01부터 센 날 수. 한국 달력 날짜끼리 뺀다 */
export function dayNumber(date: string): number {
  const [year, month, day] = date.split("-").map(Number);
  return Date.UTC(year, month - 1, day) / DAY_MS;
}

/** 끝나는 순간. 날짜만 알면 그날 23:59:59 */
export function endInstant(sale: Schedule): number | null {
  if (!sale.endDate) return null;
  return sale.endTime ? instant(sale.endDate, sale.endTime) : instant(sale.endDate, "23:59") + 59_000;
}

function untilLabel(ms: number): string {
  return ms < HOUR_MS ? `${Math.max(1, Math.floor(ms / 60_000))}분 뒤` : `${Math.floor(ms / HOUR_MS)}시간 뒤`;
}

/**
 * 판매 상태 배지(설계 3.6). now는 서버가 목록을 받은 시각. 마감 없이 7일이 지난 판매는 null.
 * 날짜만 아는 시작·마감은 날짜 단위로만 말한다.
 */
export function saleBadge(sale: Schedule, now: Date): { label: string; tone: BadgeTone } | null {
  const nowMs = now.getTime();
  const today = kstDateKey(now);
  const end = endInstant(sale);
  if (end !== null && nowMs > end) return { label: "판매 종료", tone: "soft" };

  if (sale.startDate) {
    const started = sale.startTime
      ? nowMs >= instant(sale.startDate, sale.startTime)
      : sale.startDate < today;
    if (!started) {
      const days = dayNumber(sale.startDate) - dayNumber(today);
      if (!sale.startTime) {
        return days === 0 ? { label: "오늘 시작", tone: "fill" } : { label: `D-${days} 시작`, tone: "soft" };
      }
      const diff = instant(sale.startDate, sale.startTime) - nowMs;
      if (diff <= DAY_MS) return { label: `${untilLabel(diff)} 시작`, tone: "fill" };
      return { label: `D-${days} 시작`, tone: "soft" };
    }
  } else if (sale.phase === "ANNOUNCE") {
    return { label: "일정 미정", tone: "dash" };
  }

  // 판매 중
  if (end !== null && sale.endDate) {
    const days = dayNumber(sale.endDate) - dayNumber(today);
    if (!sale.endTime) {
      if (days === 0) return { label: "오늘 마감", tone: "fill" };
      if (days === 1) return { label: "D-1 마감", tone: "fill" };
    } else {
      const diff = end - nowMs;
      if (diff <= DAY_MS) return { label: `${untilLabel(diff)} 마감`, tone: "fill" };
      if (diff <= 2 * DAY_MS) return { label: `D-${days} 마감`, tone: "fill" };
    }
    return { label: "판매 중", tone: "line" };
  }

  const base = sale.startDate ? instant(sale.startDate, sale.startTime) : new Date(sale.postedAt).getTime();
  if (nowMs - base > OPEN_ENDED_DAYS * DAY_MS) return null;
  return { label: "판매 중", tone: "line" };
}

const TYPE_LABELS: Record<SaleType, string> = {
  SALE: "판매",
  PREORDER: "예약판매",
  RESTOCK: "재입고",
  DROP: "드랍",
  RAFFLE: "래플",
};

/** 카드의 구분 글자 (예: 재입고 예정, 판매 시작, 예약판매 일정 변경, 래플) */
export function saleKindLabel(saleType: SaleType, phase: SalePhase): string {
  if (saleType === "SALE" && phase === "OPEN") return "판매 시작";
  const name = TYPE_LABELS[saleType];
  if (phase === "ANNOUNCE") return `${name} 예정`;
  if (phase === "CHANGE") return `${name} 일정 변경`;
  return name;
}

/** 공지 시각(ISO)을 한국 날짜 "YYYY-MM-DD"와 "HH:mm"으로 */
function postedParts(postedAt: string): { date: string; time: string } {
  return { date: kstDateKey(new Date(postedAt)), time: formatKstTime(postedAt) };
}

/** 카드·상세의 판매 시각 줄 (예: 판매 시작 11/8(일) 20:30, 11/10(화) 23:59까지, 11/6(금) 19:30부터) */
export function saleWhenText(sale: Schedule, now: Date): string {
  const nowMs = now.getTime();
  const today = kstDateKey(now);
  const end = endInstant(sale);
  if (end !== null && sale.endDate && nowMs > end) return `${formatMonthDay(sale.endDate, true)} 마감`;

  if (sale.startDate) {
    const started = sale.startTime ? nowMs >= instant(sale.startDate, sale.startTime) : sale.startDate < today;
    if (!started) {
      const day = formatMonthDay(sale.startDate, true);
      return `판매 시작 ${sale.startTime ? `${day} ${sale.startTime}` : day}`;
    }
  } else if (sale.phase === "ANNOUNCE") {
    return "판매 일정 미정";
  }

  if (sale.endDate) {
    const day = formatMonthDay(sale.endDate, true);
    return `${sale.endTime ? `${day} ${sale.endTime}` : day}까지`;
  }
  const from = sale.startDate
    ? { date: sale.startDate, time: sale.startTime }
    : postedParts(sale.postedAt);
  const day = formatMonthDay(from.date, true);
  return `${from.time ? `${day} ${from.time}` : day}부터`;
}

export function vendorLabel(vendor: SaleVendor): string {
  return vendor === "SWAGKEYS" ? "Swagkeys" : "GeonWorks";
}

import type { SaleCard, SalePick } from "./api";
import { CRAWLER_UA } from "./bot";
import { formatMonthDay } from "./format";
import type { bentoLayout } from "./highlight";
import { saleWhenText } from "./saleStatus";
// 서버에서만 import한다(bot.ts와 같은 Next 내부 경로). 스크립트 글자에 정규식 원문만 싣는다
import { HTML_LIMITED_BOT_UA_RE } from "next/dist/shared/lib/router/utils/html-bots";

/**
 * 메인 하이라이트 큰 칸을 고르는 인라인 스크립트(설계 3.7). 메인은 정적이라 서버에서 요청마다 고를 수 없어,
 * 정적 화면에 두 모양을 함께 넣고 이 스크립트가 화면을 그리기 전에 반반으로 고른다.
 * 스크립트 바로 앞 요소(두 모양을 감싼 요소)의 data-pick을 "kr"로 바꾸면 국내 판매 쪽이 보인다.
 * 검색·미리보기 봇(isCrawler와 같은 규칙)이나 UA가 없으면 고르지 않아 geekhack 쪽이 그대로 보인다.
 */
export function homePickScript(): string {
  const crawler = JSON.stringify(CRAWLER_UA.source);
  const limitedBot = JSON.stringify(HTML_LIMITED_BOT_UA_RE.source);
  return (
    "(function(){" +
    "var s=document.currentScript,el=s&&s.previousElementSibling;if(!el)return;" +
    "var ua=navigator.userAgent;" +
    `if(!ua||new RegExp(${crawler},"i").test(ua)||new RegExp(${limitedBot},"i").test(ua))return;` +
    'if(Math.random()<0.5)el.setAttribute("data-pick","kr")' +
    "})()"
  );
}

/** 두 모양에 들어갈 국내 판매. krFeature는 국내 판매 쪽 큰 칸, bandGk·bandKr은 각 모양의 띠 */
export interface HomeVariants {
  krFeature: SalePick | null;
  bandGk: SalePick | null;
  bandKr: SalePick | null;
}

/**
 * 하이라이트 두 모양(설계 3.7). geekhack 쪽은 큰 칸 그대로에 띠 1순위, 국내 판매 쪽은 큰 칸 1순위에 띠 2순위(없으면 띠 없음).
 * 큰 칸을 바꿀 수 있는 건 여섯 칸 벤토일 때만이다. 칸이 모자란 격자에서는 띠 1순위만.
 */
export function homeVariants(picks: SalePick[], layout: ReturnType<typeof bentoLayout>): HomeVariants {
  const first = picks[0] ?? null;
  if (layout !== "bento" || !first) return { krFeature: null, bandGk: first, bandKr: null };
  return { krFeature: first, bandGk: first, bandKr: picks[1] ?? null };
}

/** 고른 단계마다 붙이는 키커(설계 3.8) */
export const SALE_PICK_KICKER: Record<SalePick["reason"], string> = {
  UPCOMING: "곧 시작하는 국내 판매",
  CLOSING: "곧 마감하는 국내 판매",
  RECENT: "최근 국내 판매 소식",
};

/**
 * 국내 판매 큰 칸 장부의 시각 칸. 곧 마감이면 마감, 아니면 판매 시작 날짜(시각을 모르면 날짜만).
 * 그 날짜를 모르면 카드의 판매 시각 줄을 쓴다. now는 서버가 받은 시각.
 */
export function pickLedgerWhen(pick: SalePick, now: Date): { key: string; value: string } {
  const { sale } = pick;
  const closing = pick.reason === "CLOSING";
  const date = closing ? sale.endDate : sale.startDate;
  const time = closing ? sale.endTime : sale.startTime;
  if (!date) return { key: "판매 시각", value: saleWhenText(sale, now) };
  const day = formatMonthDay(date, true);
  return { key: closing ? "마감" : "판매 시작", value: time ? `${day} ${time}` : day };
}

/** 넓은 자리(큰 칸·띠) 사진. 2048 보관본을 먼저 읽고, 못 읽으면 1280. 둘 다 없으면 null */
export function wideSaleImage(sale: SaleCard): { src: string; fallback: string | null } | null {
  if (sale.imageUrl) return { src: sale.imageUrl, fallback: sale.largeUrl };
  return sale.largeUrl ? { src: sale.largeUrl, fallback: null } : null;
}

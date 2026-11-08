import { connection } from "next/server";
import { Fragment, type ReactNode } from "react";
import type { HighlightSlotKind } from "@/lib/api";
import { getCachedHighlights, getCachedSaleHighlights } from "@/lib/cachedApi";
import { bentoLayout } from "@/lib/highlight";
import { homePickScript, homeVariants } from "@/lib/homePick";
import BentoCell from "./BentoCell";
import InlineScript from "./InlineScript";
import SaleBand from "./SaleBand";
import SaleFeatureCell from "./SaleFeatureCell";
import styles from "./HighlightBento.module.css";

/** 칸 종류마다 벤토 자리(시안 a~f) */
const AREAS: Record<HighlightSlotKind, string> = {
  FEATURE: styles.a,
  TALL: styles.b,
  DEADLINE: styles.c,
  IC_REPLIES: styles.d,
  IC_VIEWS: styles.e,
  GB_VIEWS: styles.f,
};

/** 읽는 순서: 큰 칸 → 세로 칸·작은 칸 둘 → 마감 띠 → 공동구매 작은 칸(시안 순서, 모바일 화면 순서와 같다) */
const READING_ORDER: HighlightSlotKind[] = ["FEATURE", "TALL", "IC_REPLIES", "IC_VIEWS", "DEADLINE", "GB_VIEWS"];

/**
 * 메인 하이라이트. 여섯 칸이 다 있으면 시안 벤토로, 칸이 빠졌으면 남은 칸을 같은 크기 격자로 그리고, 아래에 국내 판매 띠를 둔다.
 * 날짜 줄과 같은 요청을 쓴다(한 렌더에서 한 번만 보낸다).
 * 벤토이고 국내 판매 후보가 있으면 두 모양(geekhack 큰 칸 + 띠 1순위 / 국내 판매 큰 칸 + 띠 2순위)을 함께 그리고,
 * 바로 뒤 인라인 스크립트가 화면을 그리기 전에 반반으로 고른다(설계 3.7, 메인을 정적으로 두려고 브라우저에서 고른다).
 * 스크립트가 돌지 않으면(봇, JS 꺼짐, 사이트 안 이동) 기본값 geekhack 쪽이 보인다.
 */
export default async function HighlightBento() {
  const [highlights, sales] = await Promise.all([getCachedHighlights(), getCachedSaleHighlights()]);
  if (!highlights) {
    // 오류 안내는 요청 때만 그려 정적 셸·재생성 결과·브라우저 캐시에 남지 않게 한다
    await connection();
    // 하이라이트가 실패해도 아래 게시판 줄은 그대로 둔다
    return <p className={styles.message}>하이라이트를 불러오지 못했어요</p>;
  }

  const layout = bentoLayout(highlights.slots);
  if (layout === "empty") return <p className={styles.message}>아직 고를 글이 없어요</p>;

  const compact = layout === "grid";
  const slots = highlights.slots.toSorted((x, y) => READING_ORDER.indexOf(x.slot) - READING_ORDER.indexOf(y.slot));
  /** 벤토. krCell이 있으면 큰 칸 자리에 geekhack 큰 칸과 함께 넣는다(어느 쪽이 보일지는 감싼 요소의 data-pick이 정한다) */
  const grid = (krCell?: ReactNode) => (
    <div className={compact ? styles.grid : styles.bento}>
      {slots.map((slot) => {
        const cell = (
          <BentoCell
            key={slot.slot}
            slot={slot}
            today={highlights.today}
            compact={compact}
            className={compact ? undefined : AREAS[slot.slot]}
          />
        );
        if (slot.slot !== "FEATURE" || !krCell) return cell;
        return (
          <Fragment key={slot.slot}>
            <div className={styles.gkOnly}>{cell}</div>
            <div className={styles.krOnly}>{krCell}</div>
          </Fragment>
        );
      })}
    </div>
  );

  if (!sales) {
    // 국내 판매가 실패하면 판매 부분만 빼고 그린다. 빠진 화면은 요청 때만 그려 정적 셸·재생성 결과에 남지 않게 한다
    await connection();
    return grid();
  }

  const { krFeature, bandGk, bandKr } = homeVariants(sales.picks, layout);
  // 상태 배지·판매 시각의 지금. 서버가 판매 칸을 받은 시각으로 구한다(브라우저에서 날짜를 읽지 않는다)
  const now = new Date(sales.fetchedAt);
  if (!krFeature) {
    return (
      <>
        {grid()}
        {bandGk && <SaleBand pick={bandGk} now={now} />}
      </>
    );
  }

  return (
    <>
      <div className={styles.pick} data-pick="gk" suppressHydrationWarning>
        {grid(<SaleFeatureCell pick={krFeature} now={now} className={AREAS.FEATURE} />)}
        {bandGk && (
          <div className={styles.gkOnly}>
            <SaleBand pick={bandGk} now={now} />
          </div>
        )}
        {bandKr && (
          <div className={styles.krOnly}>
            <SaleBand pick={bandKr} now={now} />
          </div>
        )}
      </div>
      {/* 바로 앞 요소(.pick)의 data-pick을 고른다. 꼭 감싼 요소 바로 뒤에 둔다 */}
      <InlineScript code={homePickScript()} />
    </>
  );
}

/** 하이라이트를 받기 전 자리. 벤토와 같은 격자로 높이를 잡는다 */
export function HighlightBentoFallback() {
  return (
    <div className={`${styles.bento} ${styles.pending}`} aria-hidden="true">
      {READING_ORDER.map((kind) => (
        <span key={kind} className={`${styles.placeholder} ${AREAS[kind]}`} />
      ))}
    </div>
  );
}

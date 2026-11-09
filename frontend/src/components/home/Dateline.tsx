import { connection } from "next/server";
import { Fragment } from "react";
import { getCachedHighlights, getCachedSaleHighlights } from "@/lib/cachedApi";
import { datelineCounts } from "@/lib/dateline";
import { formatDateline, kstDateKey } from "@/lib/format";
import Segs from "./Segs";
import styles from "./Dateline.module.css";

/**
 * 메인 맨 위 날짜 줄. 하이라이트를 고른 한국 시간 오늘과 최근 7일 현황(geekhack IC·GB 새 글 수, 국내 판매 건 수).
 * 하이라이트·국내 판매 칸과 같은 요청을 쓴다(한 렌더에서 한 번씩만 보낸다).
 */
export default async function Dateline() {
  const [highlights, sales] = await Promise.all([getCachedHighlights(), getCachedSaleHighlights()]);
  if (!highlights || !sales) {
    // 하나라도 실패하면 요청 때만 그린다. 아래 현재 시각이 미리 그리기를 끊지 않고, 숫자가 빠진 줄이 캐시에 남지 않는다
    await connection();
  }
  // 오류 안내는 하이라이트 칸이 맡는다. 받은 쪽 숫자만 보여 주고(0으로 채우지 않는다), 둘 다 실패하면 오늘 날짜만 보여준다
  const today = highlights ? highlights.today : kstDateKey(new Date());
  const counts = datelineCounts(highlights, sales?.newSaleCount);

  return (
    <div className={styles.dateline}>
      <span className={styles.strong}>{formatDateline(today)}</span>
      {counts.length > 0 && (
        <Segs
          items={counts.map(({ label, count }) => (
            <Fragment key={label}>
              {label} <strong className={styles.strong}>{count}</strong>
            </Fragment>
          ))}
        />
      )}
    </div>
  );
}

/** 날짜 줄을 받기 전 자리. 높이를 잡아 아래 내용이 밀리지 않게 한다 */
export function DatelineFallback() {
  return <div className={`${styles.dateline} ${styles.pending}`} aria-hidden="true" />;
}

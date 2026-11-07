import { connection } from "next/server";
import { getCachedHighlights } from "@/lib/cachedApi";
import { formatDateline, kstDateKey } from "@/lib/format";
import Segs from "./Segs";
import styles from "./Dateline.module.css";

/** 띄어쓰기 자리에서 줄이 갈리지 않게 붙인다(시안 &nbsp; 자리) */
const NBSP = " ";

/**
 * 메인 맨 위 날짜 줄. 하이라이트를 고른 한국 시간 오늘과 최근 7일 새 글 수, 2주 안에 마감하는 공동구매 수.
 * 하이라이트와 같은 요청을 쓴다(한 렌더에서 한 번만 보낸다).
 */
export default async function Dateline() {
  const highlights = await getCachedHighlights();
  if (!highlights) {
    // 요청 때만 그린다. 아래 현재 시각이 미리 그리기를 끊지 않고, 숫자 없는 줄이 캐시에 남지 않는다
    await connection();
    // 오류는 하이라이트 칸이 남긴다. 숫자 없이 오늘 날짜만 보여준다
    return (
      <div className={styles.dateline}>
        <span className={styles.strong}>{formatDateline(kstDateKey(new Date()))}</span>
      </div>
    );
  }

  return (
    <div className={styles.dateline}>
      <span className={styles.strong}>{formatDateline(highlights.today)}</span>
      <Segs
        items={[
          <>
            최근{NBSP}7일 새 IC <strong className={styles.strong}>{highlights.newIcCount}</strong>
          </>,
          <>
            새 공동구매 <strong className={styles.strong}>{highlights.newGbCount}</strong>
          </>,
          <>
            2주 안에 마감 <strong className={styles.strong}>{highlights.endingSoonGbCount}</strong>
          </>,
        ]}
      />
    </div>
  );
}

/** 날짜 줄을 받기 전 자리. 높이를 잡아 아래 내용이 밀리지 않게 한다 */
export function DatelineFallback() {
  return <div className={`${styles.dateline} ${styles.pending}`} aria-hidden="true" />;
}

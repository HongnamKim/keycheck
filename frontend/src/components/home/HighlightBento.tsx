import { unstable_rethrow } from "next/navigation";
import { getHighlights, type HighlightSlotKind, type Highlights } from "@/lib/api";
import { bentoLayout } from "@/lib/highlight";
import BentoCell from "./BentoCell";
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
 * 메인 하이라이트. 여섯 칸이 다 있으면 시안 벤토로, 칸이 빠졌으면 남은 칸을 같은 크기 격자로 그린다.
 * 날짜 줄과 같은 요청을 쓴다(한 렌더에서 한 번만 보낸다).
 */
export default async function HighlightBento() {
  let highlights: Highlights;
  try {
    highlights = await getHighlights();
  } catch (err) {
    unstable_rethrow(err);
    // 하이라이트가 실패해도 아래 게시판 줄은 그대로 둔다
    console.error(err);
    return <p className={styles.message}>하이라이트를 불러오지 못했어요</p>;
  }

  const layout = bentoLayout(highlights.slots);
  if (layout === "empty") return <p className={styles.message}>아직 고를 글이 없어요</p>;

  const compact = layout === "grid";
  const slots = highlights.slots.toSorted((x, y) => READING_ORDER.indexOf(x.slot) - READING_ORDER.indexOf(y.slot));
  return (
    <div className={compact ? styles.grid : styles.bento}>
      {slots.map((slot) => (
        <BentoCell
          key={slot.slot}
          slot={slot}
          today={highlights.today}
          compact={compact}
          className={compact ? undefined : AREAS[slot.slot]}
        />
      ))}
    </div>
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

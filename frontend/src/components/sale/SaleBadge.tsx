import type { BadgeTone } from "@/lib/saleStatus";
import styles from "./SaleBadge.module.css";

/** 판매 상태 배지. fill은 강조색 채움, line은 진한 테두리, soft는 회색 테두리, dash는 회색 점선 */
export default function SaleBadge({ badge }: { badge: { label: string; tone: BadgeTone } }) {
  return <span className={`${styles.badge} ${styles[badge.tone]}`}>{badge.label}</span>;
}

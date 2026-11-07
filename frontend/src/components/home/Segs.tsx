import type { ReactNode } from "react";
import styles from "./Segs.module.css";

/**
 * 메타 토막(예: 조회 1.2만+ · 댓글 3 · 11/1). 가운뎃점은 CSS가 토막 사이에 넣고,
 * 좁은 화면에서는 토막 사이에서만 줄을 바꾼다(시안 .mc-segs). 빈 토막(null)은 뺀다.
 */
export default function Segs({ items, className }: { items: ReactNode[]; className?: string }) {
  return (
    <span className={className ? `${styles.segs} ${className}` : styles.segs}>
      {items
        .filter((item) => item !== null && item !== undefined && item !== false)
        .map((item, i) => (
          <span key={i}>{item}</span>
        ))}
    </span>
  );
}

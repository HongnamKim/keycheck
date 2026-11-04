import type { Board } from "@/lib/api";
import { boardLabel } from "@/lib/format";
import styles from "./BoardBadge.module.css";

/** 게시판 배지. IC는 테두리만, 공동구매는 강조색으로 채운다. 글자는 기본이 게시판 이름(IC, 공동구매) */
export default function BoardBadge({
  board,
  label = boardLabel(board),
  className,
  decorative = false,
}: {
  board: Board;
  label?: string;
  className?: string;
  /** 옆 글자와 뜻이 겹쳐 화면 읽기 프로그램에 읽히지 않게 할 때 */
  decorative?: boolean;
}) {
  const classes = [styles.badge, board === "GB" ? styles.gb : styles.ic, className].filter(Boolean).join(" ");
  return (
    <span className={classes} aria-hidden={decorative || undefined}>
      {label}
    </span>
  );
}

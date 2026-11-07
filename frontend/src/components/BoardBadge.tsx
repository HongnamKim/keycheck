import type { Board } from "@/lib/api";
import { boardLabel } from "@/lib/format";
import styles from "./BoardBadge.module.css";

/** 게시판 배지. IC는 테두리만, 공동구매는 강조색으로 채운다. 글자는 게시판 이름(IC, 공동구매) */
export default function BoardBadge({ board, className }: { board: Board; className?: string }) {
  const classes = [styles.badge, board === "GB" ? styles.gb : styles.ic, className].filter(Boolean).join(" ");
  return <span className={classes}>{boardLabel(board)}</span>;
}

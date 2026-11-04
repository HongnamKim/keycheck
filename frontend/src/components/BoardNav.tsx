"use client";

import { usePathname, useSearchParams } from "next/navigation";
import type { BoardFilter } from "@/lib/api";
import { navCurrent } from "@/lib/query";
import FreshListLink from "./FreshListLink";
import styles from "./BoardNav.module.css";

const ITEMS: [BoardFilter, string][] = [
  ["ic", "IC"],
  ["gb", "공동구매"],
];

/** 헤더의 게시판 메뉴. 지금 있는 게시판(목록, 또는 그 목록에서 연 상세)에 밑줄을 긋는다 */
export default function BoardNav() {
  const pathname = usePathname();
  const boardParam = useSearchParams().get("board");
  return <BoardNavLinks pathname={pathname} boardParam={boardParam} />;
}

/** 주소를 읽기 전(정적 셸)에는 인자 없이 그려 아무것도 표시하지 않는다 */
export function BoardNavLinks({ pathname = "", boardParam = null }: { pathname?: string; boardParam?: string | null }) {
  return (
    <nav className={styles.nav} aria-label="게시판">
      {ITEMS.map(([board, label]) => (
        <FreshListLink
          key={board}
          href={`/${board}`}
          className={styles.link}
          aria-current={navCurrent(board, pathname, boardParam)}
        >
          {label}
        </FreshListLink>
      ))}
    </nav>
  );
}

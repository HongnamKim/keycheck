import Link from "next/link";
import { saleListHref, type SaleListQuery, type SaleView } from "@/lib/saleQuery";
import styles from "./SaleViewTabs.module.css";

const VIEWS: [SaleView, string][] = [
  ["notices", "공지 순"],
  ["schedule", "판매 일정 순"],
];

/** 목록 보기 탭(공지 순 | 판매 일정 순). 보던 판매처·분류 필터는 그대로 둔다 */
export default function SaleViewTabs({ query }: { query: SaleListQuery }) {
  return (
    <nav className={styles.tabs} aria-label="보기">
      {VIEWS.map(([view, label]) => (
        <Link
          key={view}
          href={saleListHref({ ...query, view })}
          className={styles.tab}
          aria-current={query.view === view ? "page" : undefined}
        >
          {label}
        </Link>
      ))}
    </nav>
  );
}

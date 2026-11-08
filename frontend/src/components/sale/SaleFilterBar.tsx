import Link from "next/link";
import type { SaleCategoryFilter, SaleVendorFilter } from "@/lib/api";
import { saleListHref, type SaleListQuery } from "@/lib/saleQuery";
import styles from "./SaleFilterBar.module.css";

const VENDORS: [SaleVendorFilter, string][] = [
  ["all", "전체"],
  ["swagkeys", "Swagkeys"],
  ["geonworks", "GeonWorks"],
];

const CATEGORIES: [SaleCategoryFilter, string][] = [
  ["all", "전체"],
  ["keyboard", "키보드"],
  ["keycap", "키캡"],
  ["switch", "스위치"],
  ["parts", "기타"],
];

/** 판매처와 분류 필터. 두 보기(공지 순, 판매 일정 순)에 같이 적용하고 보던 보기는 그대로 둔다. 바꾸면 목록을 처음부터 다시 받는다 */
export default function SaleFilterBar({ query }: { query: SaleListQuery }) {
  return (
    <div className={styles.bar}>
      <nav className={styles.group} aria-label="판매처">
        <span className={styles.label} aria-hidden="true">
          판매처
        </span>
        {VENDORS.map(([vendor, label]) => (
          <Link
            key={vendor}
            href={saleListHref({ ...query, vendor })}
            className={styles.filter}
            aria-current={query.vendor === vendor ? "page" : undefined}
          >
            {label}
          </Link>
        ))}
      </nav>
      <nav className={styles.group} aria-label="분류">
        <span className={styles.label} aria-hidden="true">
          분류
        </span>
        {CATEGORIES.map(([category, label]) => (
          <Link
            key={category}
            href={saleListHref({ ...query, category })}
            className={styles.filter}
            aria-current={query.category === category ? "page" : undefined}
          >
            {label}
          </Link>
        ))}
      </nav>
    </div>
  );
}

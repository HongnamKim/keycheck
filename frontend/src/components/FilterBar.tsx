import Link from "next/link";
import type { CategoryFilter, PostSort } from "@/lib/api";
import { subcategoryLabel } from "@/lib/format";
import { listHref, subOptions, withCategory, type ListQuery } from "@/lib/query";
import InfoTip from "./InfoTip";
import styles from "./FilterBar.module.css";

const FILTERS: [CategoryFilter, string][] = [
  ["all", "전체"],
  ["keyboard", "키보드"],
  ["keycap", "키캡"],
];

const SORTS: [PostSort, string][] = [
  ["recent", "최근 작성순"],
  ["views", "조회순"],
];

/** 키보드는 배열, 키캡은 제조사로 세부 분류를 고른다 */
export const ALL_SUB_LABELS: Partial<Record<CategoryFilter, string>> = {
  keyboard: "전체 배열",
  keycap: "전체 제조사",
};

/** 분류 필터와 정렬, 키보드·키캡이면 세부 분류. 바꾸면 목록을 처음부터 다시 받는다. */
export default function FilterBar({ query }: { query: ListQuery }) {
  const subs = subOptions(query.category);

  return (
    <div className={styles.bar}>
      <nav className={styles.group} aria-label="분류">
        {FILTERS.map(([category, label]) => (
          <Link
            key={category}
            href={listHref(withCategory(query, category))}
            className={styles.filter}
            // 세부 분류를 골랐으면 분류 버튼은 지금 주소가 아니라 고른 분류만 표시한다
            aria-current={query.category === category ? (query.sub ? "true" : "page") : undefined}
          >
            {label}
          </Link>
        ))}
      </nav>
      <nav className={`${styles.group} ${styles.sorts}`} aria-label="정렬">
        {SORTS.map(([sort, label]) => (
          <span key={sort} className={styles.sortItem}>
            <Link
              href={listHref({ ...query, sort })}
              className={styles.sort}
              aria-current={query.sort === sort ? "page" : undefined}
            >
              {label}
            </Link>
            {sort === "views" && (
              <InfoTip
                label="조회순 안내"
                text="지금까지 수집한 글 안에서 조회수가 많은 순서예요. geekhack 전체 순위와는 다를 수 있어요."
              />
            )}
          </span>
        ))}
      </nav>
      {subs.length > 0 && (
        <nav className={styles.subs} aria-label="세부 분류">
          <Link
            href={listHref({ ...query, sub: undefined })}
            className={styles.sub}
            aria-current={query.sub ? undefined : "page"}
          >
            {ALL_SUB_LABELS[query.category]}
          </Link>
          {subs.map(({ sub, code }) => (
            <Link
              key={sub}
              href={listHref({ ...query, sub })}
              className={styles.sub}
              aria-current={query.sub === sub ? "page" : undefined}
            >
              {subcategoryLabel(code)}
            </Link>
          ))}
        </nav>
      )}
    </div>
  );
}

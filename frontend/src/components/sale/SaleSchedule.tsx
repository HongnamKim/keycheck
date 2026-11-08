import type { SaleCard } from "@/lib/api";
import { saleHref, type SaleListQuery } from "@/lib/saleQuery";
import { scheduleBuckets } from "@/lib/saleTimeline";
import SaleRow from "./SaleRow";
import styles from "./SaleTimeline.module.css";

/**
 * 판매 일정 순 보기(설계 3.7 B). 서버가 받은 끝나지 않은 판매(최대 300건)를 구간별로 한 번에 그린다(이어 받기 없음).
 * 구간은 fetchedAt(서버가 받은 시각)을 기준으로 나눈다.
 */
export default function SaleSchedule({
  query,
  items,
  fetchedAt,
}: {
  query: SaleListQuery;
  items: SaleCard[];
  fetchedAt: string;
}) {
  const now = new Date(fetchedAt);
  const buckets = scheduleBuckets(items, now);
  if (buckets.length === 0) return <p className={styles.empty}>지금 다가오는 판매가 없어요</p>;

  return (
    <div className={styles.timeline}>
      {buckets.map((bucket) => (
        <section key={bucket.key}>
          <h2 className={styles.head}>
            <span className={styles.dot} aria-hidden="true" />
            {bucket.label}
            <span className={styles.count}>{bucket.items.length}건</span>
          </h2>
          <ul className={`${styles.block} ${styles.rows}`}>
            {bucket.items.map((sale) => (
              <li key={sale.id}>
                <SaleRow sale={sale} href={saleHref(sale.id, query)} now={now} showVendor />
              </li>
            ))}
          </ul>
        </section>
      ))}
    </div>
  );
}

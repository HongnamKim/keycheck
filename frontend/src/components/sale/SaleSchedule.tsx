import type { SaleCard } from "@/lib/api";
import type { FirstSaleBatch } from "@/lib/cachedApi";
import { saleHref, saleListHref, type SaleListQuery } from "@/lib/saleQuery";
import { scheduleBuckets, scheduleFrom } from "@/lib/saleTimeline";
import SalePastMore from "./SalePastMore";
import SaleRow from "./SaleRow";
import styles from "./SaleTimeline.module.css";

/**
 * 판매 일정 순 보기(설계 3.7 B). 서버가 받은 끝나지 않은 판매(최대 300건)를 구간별로 한 번에 그린다(이어 받기 없음).
 * 구간은 fetchedAt(서버가 받은 시각)을 기준으로 나눈다.
 * 끝에 지난 판매 구간: 첫 쪽은 서버에서 그리고, 더 있으면 SalePastMore가 다음 쪽을 받아 붙인다. past가 없거나 비면 구간을 그리지 않는다.
 * 상세 아래 이어서 보기는 currentId(지금 보는 판매 건)를 준다: 그 건이 든 구간의 그 건부터 그리고 그 줄을 표시한다.
 * 그 건이 지난 판매면 다가오는 구간 없이 past(그 건부터 받은 쪽)만 그린다. 비었다는 안내는 목록에서만 보인다(이어서 보기는 비면 영역째 빠진다).
 */
export default function SaleSchedule({
  query,
  items,
  fetchedAt,
  past,
  currentId,
}: {
  query: SaleListQuery;
  items: SaleCard[];
  fetchedAt: string;
  past: FirstSaleBatch | null;
  currentId?: number;
}) {
  const now = new Date(fetchedAt);
  const all = scheduleBuckets(items, now);
  const buckets = currentId === undefined ? all : scheduleFrom(all, currentId);
  const pastItems = past?.batch.items ?? [];
  const empty = currentId === undefined ? <p className={styles.empty}>지금 다가오는 판매가 없어요</p> : null;
  if (buckets.length === 0 && pastItems.length === 0) return empty;
  const pastNow = past ? new Date(past.fetchedAt) : now;

  return (
    <div className={styles.timeline}>
      {buckets.length === 0 && empty}
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
                <SaleRow sale={sale} href={saleHref(sale.id, query)} now={now} showVendor current={sale.id === currentId} />
              </li>
            ))}
          </ul>
        </section>
      ))}
      {past && pastItems.length > 0 && (
        <section>
          <h2 className={styles.head}>
            <span className={styles.dot} aria-hidden="true" />
            지난 판매
          </h2>
          <div className={styles.block}>
            <ul className={styles.rows}>
              {pastItems.map((sale) => (
                <li key={sale.id}>
                  <SaleRow sale={sale} href={saleHref(sale.id, query)} now={pastNow} showVendor current={sale.id === currentId} />
                </li>
              ))}
            </ul>
            <SalePastMore key={saleListHref(query)} query={query} nextCursor={past.batch.nextCursor} hasMore={past.batch.hasMore} fetchedAt={past.fetchedAt} />
          </div>
        </section>
      )}
    </div>
  );
}

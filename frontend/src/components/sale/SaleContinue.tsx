import type { SaleCard } from "@/lib/api";
import { getCachedSalePast, getCachedSaleSchedule, getFirstSaleBatch } from "@/lib/cachedApi";
import type { SaleListQuery } from "@/lib/saleQuery";
import { pastAtMillis, scheduleBuckets, scheduleFrom } from "@/lib/saleTimeline";
import SaleFeed from "./SaleFeed";
import SaleSchedule from "./SaleSchedule";
import styles from "./SaleContinue.module.css";

/** 목록 밖(메인 등)에서 연 상세는 공지 순·필터 없이 잇는다 */
const DEFAULT_QUERY: SaleListQuery = { vendor: "all", category: "all", view: "notices" };

/**
 * 상세 아래 "이어서 보기"(설계 3.7). 들어온 목록(from)의 보기 순서와 필터대로 이 판매 건부터 목록을 잇고, 이 건의 줄을 표시한다.
 * 공지 순: 이 건의 공지 묶음(같은 공지의 다른 판매 포함)부터 더 오래된 공지로 이어 받는다.
 * 판매 일정 순: 이 건이 든 구간의 이 건부터 다음 구간들, 이어서 지난 판매 첫 쪽. 이 건이 지난 판매면 지난 판매를 이 건부터.
 * 배지·날짜의 기준은 각 캐시 응답의 받은 시각이다. 받지 못했거나 보여 줄 것이 없으면 아무것도 그리지 않는다(상세는 그대로).
 */
export default async function SaleContinue({ sale, from }: { sale: SaleCard; from: SaleListQuery | null }) {
  const query = from ?? DEFAULT_QUERY;
  const schedule = query.view === "schedule";
  const body = schedule ? await scheduleBody(sale, query) : await noticesBody(sale, query, from);
  if (!body) return null;

  return (
    <section className={styles.continue} aria-labelledby="continue-title">
      <div className={styles.head}>
        <h2 id="continue-title" className={styles.title}>
          이어서 보기
        </h2>
        <p className={styles.desc}>{schedule ? "판매 일정 순으로 이어서 보여 드려요" : "공지 순으로 이어서 보여 드려요"}</p>
      </div>
      {body}
    </section>
  );
}

async function noticesBody(sale: SaleCard, query: SaleListQuery, from: SaleListQuery | null) {
  // 목록 cursor "공지 시각 밀리초_id"는 그보다 앞선 공지와, 같은 시각이면 id가 큰 건을 준다. 서버가 id 0을 받지 않아
  // 공지 시각 + 1ms와 id 1로 묻는다: 이 공지(같은 시각)의 건 전부부터 더 오래된 공지까지 온다
  const cursor = `${new Date(sale.postedAt).getTime() + 1}_1`;
  const first = await getFirstSaleBatch(query.vendor, query.category, cursor);
  if (!first || first.batch.items.length === 0) return null;
  // 메인에서 열었으면 줄 링크에 목록을 싣지 않아 계속 "메인으로" 돌아간다(글 상세의 주변 글과 같다)
  return <SaleFeed query={query} initial={first.batch} renderedAt={first.fetchedAt} currentId={sale.id} linkFrom={from} />;
}

async function scheduleBody(sale: SaleCard, query: SaleListQuery) {
  const schedule = await getCachedSaleSchedule(query.vendor, query.category);
  if (!schedule) return null;
  const active = scheduleFrom(scheduleBuckets(schedule.items, new Date(schedule.fetchedAt)), sale.id);
  // 다가오는 구간에 있으면 지난 판매는 목록과 같은 첫 쪽(같은 캐시). 없으면(지난 판매) 이 건부터:
  // 지난 판매 cursor "지난 시각 밀리초_id"는 그보다 앞선 건과, 같은 시각이면 id가 작은 건을 주므로 id + 1이면 이 건부터 온다
  const past =
    active.length > 0
      ? await getCachedSalePast(query.vendor, query.category)
      : await getCachedSalePast(query.vendor, query.category, `${pastAtMillis(sale)}_${sale.id + 1}`);
  if (active.length === 0 && !past?.batch.items.length) return null;
  return <SaleSchedule query={query} items={schedule.items} fetchedAt={schedule.fetchedAt} past={past} currentId={sale.id} />;
}

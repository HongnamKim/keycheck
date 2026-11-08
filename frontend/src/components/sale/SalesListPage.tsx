import { connection } from "next/server";
import { Suspense, type ReactNode } from "react";
import { getCachedSalePast, getCachedSaleSchedule, getFirstSaleBatch } from "@/lib/cachedApi";
import { SALE_LIST_INFO } from "@/lib/format";
import { parseSaleListQuery, saleListHref } from "@/lib/saleQuery";
import LoadFailed from "../LoadFailed";
import SaleFeed from "./SaleFeed";
import SaleFilterBar from "./SaleFilterBar";
import SaleSchedule from "./SaleSchedule";
import SaleViewTabs from "./SaleViewTabs";
import styles from "./SalesListPage.module.css";

type SearchParams = PageProps<"/sales">["searchParams"];

/** 국내 판매 목록 화면(/sales). 제목·설명은 바로 그리고, 보기 탭·필터와 목록은 주소의 쿼리를 읽은 뒤 그린다 */
export default function SalesListPage({ searchParams }: { searchParams: SearchParams }) {
  return (
    <div className={styles.page}>
      <div className={styles.head}>
        <h1 className={styles.title}>{SALE_LIST_INFO.title}</h1>
        <p className={styles.desc}>{SALE_LIST_INFO.desc}</p>
      </div>
      <Suspense fallback={<p className={styles.message}>판매 소식을 불러오는 중이에요</p>}>
        <SaleList searchParams={searchParams} />
      </Suspense>
    </div>
  );
}

async function SaleList({ searchParams }: { searchParams: SearchParams }) {
  const query = parseSaleListQuery(await searchParams);
  let body: ReactNode = null;
  if (query.view === "schedule") {
    // 지난 판매만 실패해도 다가오는 판매는 보여준다
    const [schedule, past] = await Promise.all([
      getCachedSaleSchedule(query.vendor, query.category),
      getCachedSalePast(query.vendor, query.category),
    ]);
    if (schedule) body = <SaleSchedule query={query} items={schedule.items} fetchedAt={schedule.fetchedAt} past={past} />;
  } else {
    const first = await getFirstSaleBatch(query.vendor, query.category);
    // 필터가 바뀌면(다른 주소) 목록을 새로 시작한다
    if (first) body = <SaleFeed key={saleListHref(query)} query={query} initial={first.batch} renderedAt={first.fetchedAt} />;
  }
  if (!body) {
    // 오류 안내는 요청 때만 그려 정적 셸·재생성 결과·브라우저 캐시에 남지 않게 한다
    await connection();
    return (
      <LoadFailed>
        {/* 같은 목록 주소를 새로 열어 다시 불러온다 */}
        <a href={saleListHref(query)} className="outline-button">
          다시 시도
        </a>
      </LoadFailed>
    );
  }
  return (
    <div className={styles.feed}>
      <SaleViewTabs query={query} />
      <SaleFilterBar query={query} />
      {body}
    </div>
  );
}

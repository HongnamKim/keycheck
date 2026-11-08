import { Suspense } from "react";
import { connection } from "next/server";
import { getFirstSaleBatch } from "@/lib/cachedApi";
import { saleListHref } from "@/lib/saleQuery";
import FreshListLink from "../FreshListLink";
import { RailCardPlaceholder } from "./RailCard";
import { RailTrack } from "./RailScroller";
import SaleRailCard from "./SaleRailCard";
import styles from "./BoardRail.module.css";

/** 메인에 보여주는 최근 판매 수 */
const RECENT_COUNT = 10;
/** 판매를 받기 전 그리는 카드 자리 수 */
const PLACEHOLDER_COUNT = 5;
/** 넘김 버튼 이름에 붙일 말 */
const NAME = "국내 판매";

/**
 * 메인의 "03 국내 판매" 가로 줄. 머리는 바로 그리고, 최근 판매는 따로 받아
 * 느려도 다른 줄과 하이라이트를 막지 않는다. 머리 모양은 BoardRail과 같다.
 */
export default function SaleRail() {
  return (
    <section id="home-sales" className={styles.sec} aria-labelledby="home-sales-title">
      <div className={styles.head}>
        <span className={styles.no} aria-hidden="true">
          03
        </span>
        <div className={styles.text}>
          <h2 id="home-sales-title" className={styles.title}>
            국내 판매
          </h2>
          <p className={styles.sub}>
            Swagkeys · GeonWorks 디스코드 공지
            <span className={styles.sep}> · </span>
            <span className={styles.tail}>새로 올라온 순</span>
          </p>
        </div>
        <div className={styles.tools}>
          <FreshListLink href={saleListHref({ vendor: "all", category: "all", view: "notices" })} className={styles.more}>
            전체 보기 →
          </FreshListLink>
        </div>
      </div>
      <Suspense
        fallback={
          // board="gb"는 넘김 버튼 위치용이다. 판매 카드 사진도 공동구매와 같은 3:2라 높이가 같다
          <RailTrack board="gb" name={NAME} placeholder>
            {Array.from({ length: PLACEHOLDER_COUNT }, (_, i) => (
              <RailCardPlaceholder key={i} board="gb" />
            ))}
          </RailTrack>
        }
      >
        <RecentSales />
      </Suspense>
    </section>
  );
}

async function RecentSales() {
  // 판매 목록(/sales) 기본 목록과 같은 캐시를 쓴다
  const first = await getFirstSaleBatch("all", "all");
  if (!first) {
    // 오류 안내는 요청 때만 그려 정적 셸·재생성 결과·브라우저 캐시에 남지 않게 한다
    await connection();
    return <p className={styles.message}>글을 불러오지 못했어요</p>;
  }
  const sales = first.batch.items.slice(0, RECENT_COUNT);
  if (sales.length === 0) return <p className={styles.message}>아직 모은 판매 소식이 없어요</p>;

  // 상태 배지의 지금. 목록을 받은 시각으로 서버에서 구한다(브라우저에서 날짜를 읽지 않는다)
  const now = new Date(first.fetchedAt);
  return (
    // board="gb"는 넘김 버튼 위치용이다. 판매 카드 사진도 공동구매와 같은 3:2라 버튼 높이가 맞는다
    <RailTrack board="gb" name={NAME}>
      {sales.map((sale) => (
        <SaleRailCard key={sale.id} sale={sale} now={now} />
      ))}
    </RailTrack>
  );
}

import "server-only";

/**
 * 서버 컴포넌트에서만 쓰는 캐시한 백엔드 호출. api.ts는 클라이언트 컴포넌트도 import해서 'use cache'를 둘 수 없어 따로 둔다.
 * 백엔드가 실패하면(시간 제한, !res.ok) 예외 대신 null을 돌려준다. 'use cache' 안에서 던진 오류는 받는 쪽이 잡아도
 * 빌드의 미리 그리기를 실패시키기 때문이다. 실패는 캐시 수명을 0으로 둬 저장하지 않고, 미리 그리기에서는 요청 때 채울 구멍이 된다.
 */

import { cacheLife } from "next/cache";
import { unstable_rethrow } from "next/navigation";
import {
  getHighlights,
  getPosts,
  getSaleHighlights,
  getSalePast,
  getSales,
  getSaleSchedule,
  type BoardFilter,
  type CategoryFilter,
  type Highlights,
  type PostBatch,
  type PostSort,
  type SaleBatch,
  type SaleCard,
  type SaleCategoryFilter,
  type SalePick,
  type SaleVendorFilter,
  type SubFilter,
} from "./api";

/** 실패한 결과의 캐시 수명. 저장하지 않고 다음 요청에서 다시 부른다 */
const NO_CACHE = { stale: 0, revalidate: 0, expire: 0 };

/** 목록 첫 묶음과 받은 시각. 화면의 "n분 전" 같은 상대 시간과 오늘 날짜는 받은 시각을 기준으로 한다 */
export interface FirstBatch {
  batch: PostBatch;
  fetchedAt: string;
}

/**
 * 목록 첫 묶음. 목록 페이지(/ic, /gb, 필터 주소)와 메인 두 줄이 함께 쓴다. 실패하면 null.
 * 상세에서 링크로 돌아올 때 다시 받지 않도록 브라우저에 15분 남기고, 서버는 60초마다 새로 받는다.
 */
export async function getFirstBatch(
  board: BoardFilter,
  category: CategoryFilter,
  sort: PostSort,
  sub: SubFilter | null,
): Promise<FirstBatch | null> {
  "use cache";
  try {
    const batch = await getPosts(board, category, sort, null, sub ?? undefined);
    cacheLife({ stale: 900, revalidate: 60, expire: 3600 });
    // 캐시 안에서 읽은 현재 시각은 미리 그리기를 끊지 않는다
    return { batch, fetchedAt: new Date().toISOString() };
  } catch (err) {
    unstable_rethrow(err);
    console.error(err);
    cacheLife(NO_CACHE);
    return null;
  }
}

/**
 * 메인 하이라이트와 날짜 줄 숫자. 실패하면 null. 새 글이 첫 방문자에게 늦게 보이는 시간을 줄이려고 expire를 짧게 둔다
 * (300초 이상이어야 미리 그리기에 남는다).
 */
export async function getCachedHighlights(): Promise<Highlights | null> {
  "use cache";
  try {
    const highlights = await getHighlights();
    cacheLife({ stale: 300, revalidate: 60, expire: 900 });
    return highlights;
  } catch (err) {
    unstable_rethrow(err);
    console.error(err);
    cacheLife(NO_CACHE);
    return null;
  }
}

/** 국내 판매 첫 묶음과 받은 시각. 배지 계산은 받은 시각을 기준으로 한다 */
export interface FirstSaleBatch {
  batch: SaleBatch;
  fetchedAt: string;
}

/**
 * 국내 판매 목록 첫 묶음. cursor를 주면 그 위치부터 받은 첫 묶음(상세 아래 이어서 보기).
 * getFirstBatch와 같은 캐시 수명. 실패하면 null
 */
export async function getFirstSaleBatch(
  vendor: SaleVendorFilter,
  category: SaleCategoryFilter,
  cursor: string | null = null,
): Promise<FirstSaleBatch | null> {
  "use cache";
  try {
    const batch = await getSales(vendor, category, cursor);
    cacheLife({ stale: 900, revalidate: 60, expire: 3600 });
    return { batch, fetchedAt: new Date().toISOString() };
  } catch (err) {
    unstable_rethrow(err);
    console.error(err);
    cacheLife(NO_CACHE);
    return null;
  }
}

/** 국내 판매 일정 순 목록. getFirstSaleBatch와 같은 캐시 수명. 실패하면 null */
export async function getCachedSaleSchedule(
  vendor: SaleVendorFilter,
  category: SaleCategoryFilter,
): Promise<{ items: SaleCard[]; fetchedAt: string } | null> {
  "use cache";
  try {
    const items = await getSaleSchedule(vendor, category);
    cacheLife({ stale: 900, revalidate: 60, expire: 3600 });
    return { items, fetchedAt: new Date().toISOString() };
  } catch (err) {
    unstable_rethrow(err);
    console.error(err);
    cacheLife(NO_CACHE);
    return null;
  }
}

/** 국내 지난 판매 첫 쪽. cursor를 주면 그 위치부터 받은 쪽(상세 아래 이어서 보기). getFirstSaleBatch와 같은 캐시 수명. 실패하면 null */
export async function getCachedSalePast(
  vendor: SaleVendorFilter,
  category: SaleCategoryFilter,
  cursor: string | null = null,
): Promise<FirstSaleBatch | null> {
  "use cache";
  try {
    const batch = await getSalePast(vendor, category, cursor);
    cacheLife({ stale: 900, revalidate: 60, expire: 3600 });
    return { batch, fetchedAt: new Date().toISOString() };
  } catch (err) {
    unstable_rethrow(err);
    console.error(err);
    cacheLife(NO_CACHE);
    return null;
  }
}

/** 메인 국내 판매 칸. getCachedHighlights와 같은 캐시 수명. 실패하면 null */
export async function getCachedSaleHighlights(): Promise<{ picks: SalePick[]; fetchedAt: string } | null> {
  "use cache";
  try {
    const picks = await getSaleHighlights();
    cacheLife({ stale: 300, revalidate: 60, expire: 900 });
    return { picks, fetchedAt: new Date().toISOString() };
  } catch (err) {
    unstable_rethrow(err);
    console.error(err);
    cacheLife(NO_CACHE);
    return null;
  }
}

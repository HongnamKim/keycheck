import type { SaleCategoryFilter, SaleVendorFilter } from "./api";

/** 목록 보기: 공지 순(기본), 판매 일정 순 */
export type SaleView = "notices" | "schedule";

/** 국내 판매 목록(/sales)의 상태. 쿼리에서 읽는다 */
export interface SaleListQuery {
  vendor: SaleVendorFilter;
  category: SaleCategoryFilter;
  view: SaleView;
}

type SearchParams = Record<string, string | string[] | undefined>;

export const SALE_VENDORS: readonly SaleVendorFilter[] = ["all", "swagkeys", "geonworks"];
export const SALE_CATEGORIES: readonly SaleCategoryFilter[] = ["all", "keyboard", "keycap", "switch", "parts"];
export const SALE_VIEWS: readonly SaleView[] = ["notices", "schedule"];

function pick<T extends string>(value: string | string[] | undefined, allowed: readonly T[]): T {
  return allowed.find((item) => item === value) ?? allowed[0];
}

/** 잘못된 값은 오류 없이 기본값(all, 공지 순)으로 바꾼다 */
export function parseSaleListQuery(params: SearchParams): SaleListQuery {
  return {
    vendor: pick(params.vendor, SALE_VENDORS),
    category: pick(params.category, SALE_CATEGORIES),
    view: pick(params.view, SALE_VIEWS),
  };
}

/** 기본값(공지 순, all)을 뺀 view, vendor, category 쿼리 */
function filterSearch(query: SaleListQuery): URLSearchParams {
  const search = new URLSearchParams();
  if (query.view !== "notices") search.set("view", query.view);
  if (query.vendor !== "all") search.set("vendor", query.vendor);
  if (query.category !== "all") search.set("category", query.category);
  return search;
}

function withQuestionMark(search: URLSearchParams): string {
  const qs = search.toString();
  return qs ? `?${qs}` : "";
}

/** 목록 주소 (예: /sales?view=schedule&vendor=geonworks&category=keycap). 모두 기본값이면 /sales */
export function saleListHref(query: SaleListQuery): string {
  return `/sales${withQuestionMark(filterSearch(query))}`;
}

/**
 * 상세 주소. 목록에서 열면 list=1과 보던 보기·필터를 실어 "목록으로"가 같은 목록으로 돌아가게 한다
 * (예: /sales/3?list=1&view=schedule&vendor=geonworks). 메인에서 열면 쿼리 없이.
 */
export function saleHref(id: number, from?: SaleListQuery | null): string {
  if (!from) return `/sales/${id}`;
  const search = new URLSearchParams({ list: "1" });
  for (const [key, value] of filterSearch(from)) search.set(key, value);
  return `/sales/${id}${withQuestionMark(search)}`;
}

/** 상세의 돌아가기 주소. 목록에서 왔으면 그 목록, 아니면 메인 */
export function saleBackHref(from: SaleListQuery | null): string {
  return from ? saleListHref(from) : "/";
}

/** 상세 주소의 쿼리로 보던 목록을 읽는다. list=1이 아니면 목록에서 온 것이 아니라 null(메인에서 열었다) */
export function parseSaleDetailQuery(params: SearchParams): SaleListQuery | null {
  return params.list === "1" ? parseSaleListQuery(params) : null;
}

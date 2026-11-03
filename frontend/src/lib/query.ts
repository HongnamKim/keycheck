import type { CategoryFilter, PostSort, SubFilter, Subcategory } from "./api";

/** 목록 화면의 URL 쿼리. 목록은 무한 스크롤이라 페이지 번호가 없다. sub는 키보드·키캡일 때만 있다. */
export interface ListQuery {
  category: CategoryFilter;
  sub?: SubFilter;
  sort: PostSort;
}

/** 세부 분류 필터 하나: 주소 값과 그에 해당하는 백엔드 세부 분류 코드 */
export interface SubOption {
  sub: SubFilter;
  code: Subcategory;
}

type SearchParams = Record<string, string | string[] | undefined>;

const CATEGORIES: readonly CategoryFilter[] = ["all", "keyboard", "keycap"];
const SORTS: readonly PostSort[] = ["recent", "views"];
const DEFAULT_QUERY: ListQuery = { category: "all", sort: "recent" };

const SUB_OPTIONS: Record<CategoryFilter, readonly SubOption[]> = {
  all: [],
  keyboard: [
    { sub: "100", code: "KB_100" },
    { sub: "96", code: "KB_96" },
    { sub: "80", code: "KB_80" },
    { sub: "75", code: "KB_75" },
    { sub: "65", code: "KB_65" },
    { sub: "60", code: "KB_60" },
    { sub: "40", code: "KB_40" },
    { sub: "ergo", code: "KB_ERGO" },
    { sub: "etc", code: "KB_ETC" },
  ],
  keycap: [
    { sub: "gmk", code: "KC_GMK" },
    { sub: "sp", code: "KC_SP" },
    { sub: "mw", code: "KC_MW" },
    { sub: "kkb", code: "KC_KKB" },
    { sub: "pbtfans", code: "KC_PBTFANS" },
    { sub: "etc", code: "KC_ETC" },
  ],
};

/** 분류에서 고를 수 있는 세부 분류(화면 순서). 전체는 없다 */
export function subOptions(category: CategoryFilter): readonly SubOption[] {
  return SUB_OPTIONS[category];
}

/** 양의 정수 문자열만 숫자로 바꾼다. 아니면 null */
function parsePositiveInt(value: string): number | null {
  if (!/^[1-9]\d*$/.test(value)) return null;
  const n = Number(value);
  return Number.isSafeInteger(n) ? n : null;
}

function pick<T extends string>(value: string | string[] | undefined, allowed: readonly T[], fallback: T): T {
  return allowed.find((item) => item === value) ?? fallback;
}

/**
 * 잘못된 값은 오류 없이 기본값(all, recent)으로 바꾼다. sub는 category와 맞을 때만 받고, 아니면 버린다.
 * 예전 주소의 page 쿼리는 무시한다.
 */
export function parseListQuery(params: SearchParams): ListQuery {
  const category = pick(params.category, CATEGORIES, DEFAULT_QUERY.category);
  const sub = subOptions(category).find((option) => option.sub === params.sub)?.sub;
  const sort = pick(params.sort, SORTS, DEFAULT_QUERY.sort);
  return sub ? { category, sub, sort } : { category, sort };
}

/** 분류를 고른 목록. 세부 분류는 분류마다 달라 비운다(같은 분류를 다시 고르면 그 분류 전체) */
export function withCategory(query: ListQuery, category: CategoryFilter): ListQuery {
  return { category, sort: query.sort };
}

/** 기본값을 뺀 쿼리 문자열. 모두 기본값이면 빈 문자열 */
function searchOf(query: ListQuery): string {
  const search = new URLSearchParams();
  if (query.category !== DEFAULT_QUERY.category) search.set("category", query.category);
  if (query.sub) search.set("sub", query.sub);
  if (query.sort !== DEFAULT_QUERY.sort) search.set("sort", query.sort);
  const qs = search.toString();
  return qs ? `?${qs}` : "";
}

/** 기본값은 빼고 category, sub, sort 순서로 목록 주소를 만든다 (예: /?category=keycap&sub=gmk&sort=views) */
export function listHref(query: ListQuery): string {
  return `/${searchOf(query)}`;
}

/** 상세 주소. 보던 목록의 쿼리를 실어 두어 "목록으로"가 같은 목록으로 돌아가게 한다 */
export function postHref(id: number, query: ListQuery): string {
  return `/posts/${id}${searchOf(query)}`;
}

/** 상세 주소의 글 ID. 양의 정수가 아니면 null */
export function parsePostId(id: string): number | null {
  return parsePositiveInt(id);
}

import type { Board, BoardFilter, CategoryFilter, PostSort, SubFilter, Subcategory } from "./api";

/**
 * 게시판 목록 화면(/ic, /gb)의 상태. board는 경로에서, 나머지는 URL 쿼리에서 읽는다.
 * 목록은 무한 스크롤이라 페이지 번호가 없다. sub는 키보드·키캡일 때만 있다.
 */
export interface ListQuery {
  board: BoardFilter;
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

const BOARDS: readonly BoardFilter[] = ["ic", "gb"];
const CATEGORIES: readonly CategoryFilter[] = ["all", "keyboard", "keycap"];
const SORTS: readonly PostSort[] = ["recent", "views"];
const DEFAULT_CATEGORY: CategoryFilter = "all";
const DEFAULT_SORT: PostSort = "recent";

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
 * 게시판 목록의 쿼리. 잘못된 값은 오류 없이 기본값(all, recent)으로 바꾼다. sub는 category와 맞을 때만 받고, 아니면 버린다.
 * 예전 주소의 page 쿼리와 board 쿼리는 무시한다(게시판은 경로가 정한다).
 */
export function parseListQuery(board: BoardFilter, params: SearchParams): ListQuery {
  const category = pick(params.category, CATEGORIES, DEFAULT_CATEGORY);
  const sub = subOptions(category).find((option) => option.sub === params.sub)?.sub;
  const sort = pick(params.sort, SORTS, DEFAULT_SORT);
  return sub ? { board, category, sub, sort } : { board, category, sort };
}

/** 상세 주소의 쿼리로 보던 목록을 읽는다. board가 없거나 잘못됐으면 목록에서 온 것이 아니라 null(메인에서 열었다) */
export function parseDetailQuery(params: SearchParams): ListQuery | null {
  const board = BOARDS.find((item) => item === params.board);
  return board ? parseListQuery(board, params) : null;
}

/**
 * 상세 하단 주변 글 목록의 조건. 목록에서 왔으면 그 목록, 메인에서 왔으면(from이 null) 그 글 게시판의 기본 목록.
 * board는 그 글의 게시판.
 */
export function neighborsQuery(from: ListQuery | null, board: Board): ListQuery {
  return from ?? { board: board === "GB" ? "gb" : "ic", category: DEFAULT_CATEGORY, sort: DEFAULT_SORT };
}

/** 분류를 고른 목록. 세부 분류는 분류마다 달라 비운다(같은 분류를 다시 고르면 그 분류 전체) */
export function withCategory(query: ListQuery, category: CategoryFilter): ListQuery {
  return { board: query.board, category, sort: query.sort };
}

/** 기본값을 뺀 category, sub, sort 쿼리. 모두 기본값이면 비어 있다 */
function filterSearch(query: ListQuery): URLSearchParams {
  const search = new URLSearchParams();
  if (query.category !== DEFAULT_CATEGORY) search.set("category", query.category);
  if (query.sub) search.set("sub", query.sub);
  if (query.sort !== DEFAULT_SORT) search.set("sort", query.sort);
  return search;
}

function withQuestionMark(search: URLSearchParams): string {
  const qs = search.toString();
  return qs ? `?${qs}` : "";
}

/** 게시판 경로에 기본값을 뺀 category, sub, sort를 붙인다 (예: /gb?category=keycap&sub=gmk&sort=views) */
export function listHref(query: ListQuery): string {
  return `/${query.board}${withQuestionMark(filterSearch(query))}`;
}

/**
 * 상세 주소. 목록에서 열면 보던 목록(board 먼저)을 실어 두어 "목록으로"가 같은 목록으로 돌아가게 한다
 * (예: /posts/12?board=gb&category=keycap). 메인에서 열면 쿼리 없이.
 */
export function postHref(id: number, from?: ListQuery | null): string {
  if (!from) return `/posts/${id}`;
  const search = new URLSearchParams({ board: from.board });
  for (const [key, value] of filterSearch(from)) search.set(key, value);
  return `/posts/${id}${withQuestionMark(search)}`;
}

/** 상세의 돌아가기 주소. 목록에서 왔으면 그 목록, 아니면 메인 */
export function backHref(from: ListQuery | null): string {
  return from ? listHref(from) : "/";
}

/**
 * 헤더 게시판 메뉴의 aria-current. 그 게시판 목록이면 "page", 그 게시판 목록에서 연 상세면 "true".
 * boardParam은 지금 주소의 board 쿼리.
 */
export function navCurrent(board: BoardFilter, pathname: string, boardParam: string | null): "page" | "true" | undefined {
  if (pathname === `/${board}`) return "page";
  if (pathname.startsWith("/posts/") && boardParam === board) return "true";
  return undefined;
}

/** 헤더 "국내 판매" 메뉴의 aria-current. /sales면 "page", 상세(/sales/…)면 "true" */
export function salesNavCurrent(pathname: string): "page" | "true" | undefined {
  if (pathname === "/sales") return "page";
  if (pathname.startsWith("/sales/")) return "true";
  return undefined;
}

/** 상세 주소의 글 ID. 양의 정수가 아니면 null */
export function parsePostId(id: string): number | null {
  return parsePositiveInt(id);
}

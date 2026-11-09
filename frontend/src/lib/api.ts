/** 백엔드 응답 타입과 호출 함수. 응답 형식은 backend의 post/dto와 맞춘다. */

import { cache } from "react";
import type { ListQuery } from "./query";

/** UNCLASSIFIED: 틀 글이라 아직 분류하지 않음. "전체"에서만 보인다 */
export type Category = "KEYBOARD" | "KEYCAP" | "OTHER" | "UNCLASSIFIED";
/**
 * 세부 분류. KEYBOARD는 배열(KB_), KEYCAP은 제조사(KC_). OTHER·UNCLASSIFIED와 요약 전 글은 null.
 * 요약기가 요약할 때 함께 정한다.
 */
export type Subcategory =
  | "KB_100"
  | "KB_96"
  | "KB_80"
  | "KB_75"
  | "KB_65"
  | "KB_60"
  | "KB_40"
  | "KB_ERGO"
  | "KB_ETC"
  | "KC_GMK"
  | "KC_SP"
  | "KC_MW"
  | "KC_KKB"
  | "KC_PBTFANS"
  | "KC_ETC";
export type SummaryStatus = "PENDING" | "DONE" | "FAILED";
/** 글이 올라온 geekhack 게시판. IC: Interest Checks, GB: Group Buys and Preorders */
export type Board = "IC" | "GB";
/** 목록 board 파라미터. 백엔드는 all(기본)도 받지만 화면은 게시판별로만 부른다 */
export type BoardFilter = "ic" | "gb";
export type CategoryFilter = "all" | "keyboard" | "keycap";
export type PostSort = "recent" | "views";
/** 목록 sub 파라미터(주소용 짧은 값). keyboard면 배열, keycap이면 제조사. etc는 둘 다 쓴다 */
export type SubFilter = "100" | "96" | "80" | "75" | "65" | "60" | "40" | "ergo" | "gmk" | "sp" | "mw" | "kkb" | "pbtfans" | "etc";

export interface PostCard {
  id: number;
  board: Board;
  title: string;
  /** 요약기가 뽑은 상품·프로젝트 이름(예: SWG Nightshift). 요약 전이거나 모르면 null. 메인은 lib/format의 displayTitle로 쓴다 */
  productName: string | null;
  category: Category;
  subcategory: Subcategory | null;
  representativeImageUrl: string | null;
  /** 대표 이미지 축소본(가로 최대 640px) 주소. 대표 이미지나 축소본이 없으면 null이고 화면은 원본을 쓴다 */
  representativeThumbUrl: string | null;
  /** 대표 이미지 큰 축소본(가로 최대 1280px) 주소. 메인 하이라이트 큰 칸에 쓴다. 대표 이미지나 큰 축소본이 없으면 null */
  representativeLargeUrl: string | null;
  summaryStatus: SummaryStatus;
  /** 요약기가 뽑은 벤더 중 한국 벤더(지역 KR, 또는 백엔드가 이름·판매 사이트로 알아본 한국 벤더)가 있으면 true. 요약 전이거나 벤더를 모르면 false */
  hasKoreanVendor: boolean;
  summaryFirstLine: string | null;
  views: number;
  replies: number;
  statsCheckedAt: string;
  postedAt: string | null;
  /** postedAt이 없는 틀 글만: 글 번호로 추정한 작성 시각. postedAt이 있으면 늘 null */
  estimatedPostedAt: string | null;
  /** 목록에서 읽은 마지막 게시물(댓글) 시각. 아직 읽은 적 없으면 null */
  lastPostAt: string | null;
  /**
   * 요약기가 뽑은 공동구매 시작일·마감일("YYYY-MM-DD", 시간대 없는 날짜). 공동구매 글만 있고, 모르면 null.
   * new Date()로 파싱하지 않고 lib/gbPeriod로 쓴다.
   */
  gbStartDate: string | null;
  gbEndDate: string | null;
  /** false면 목록에서만 가져온 틀 글: 본문·이미지를 아직 읽지 않아 representativeImageUrl·postedAt이 null */
  detailLoaded: boolean;
}

/**
 * 목록 한 묶음(최대 20건).
 * - hasMore=false: 끝. hasMore=true: 다음 요청에 nextCursor를 그대로 넘긴다(null이면 처음부터).
 * - items가 0건인데 hasMore=true면 geekhack에서 더 가져오지 못한 순간이라, 잠시 뒤 같은 nextCursor로 다시 요청한다.
 */
export interface PostBatch {
  items: PostCard[];
  nextCursor: string | null;
  hasMore: boolean;
}

export interface SpecItem {
  label: string;
  value: string;
}

/** 요약기가 원문에서 뽑은 판매처(벤더) */
export interface Vendor {
  /** 원문 표기 그대로 */
  name: string;
  /** 대문자 지역 코드(KR, US, EU 등). 한국은 "KR". 모르면 null */
  region: string | null;
  /** 원문 첫 글 링크 중 그 벤더의 판매 페이지. 없으면 null. 화면은 lib/vendor의 vendorLink로 걸러 쓴다 */
  url: string | null;
}

/** 상세 갤러리 이미지 한 장 */
export interface PostImage {
  /** 원본 주소. 확대 보기와 원본 열기에 쓴다 */
  url: string;
  /** 축소본(가로 최대 640px) 주소. 아직 없으면 null이고 화면은 원본을 쓴다 */
  thumbUrl: string | null;
}

export interface PostDetail {
  id: number;
  board: Board;
  title: string;
  /** 요약기가 뽑은 상품·프로젝트 이름. 요약 전이거나 모르면 null. 상세 화면은 원래 제목을 쓴다 */
  productName: string | null;
  author: string;
  category: Category;
  subcategory: Subcategory | null;
  representativeImageUrl: string | null;
  /** 대표 이미지 축소본(가로 최대 640px) 주소. 대표 이미지나 축소본이 없으면 null */
  representativeThumbUrl: string | null;
  /** 대표 이미지 큰 축소본(가로 최대 1280px) 주소. 상세 맨 위에 쓴다. 대표 이미지나 큰 축소본이 없으면 null */
  representativeLargeUrl: string | null;
  /** 규칙에서 빠지지 않은 후보 이미지 전체, 본문 순서, 주소 중복 제외 (대표 이미지 포함) */
  images: PostImage[];
  summaryStatus: SummaryStatus;
  /** 지금 요약 중이면 서버가 요약을 시작한 뒤 흐른 밀리초, 아니면 null. 옛 백엔드는 키가 없다 */
  summarizingForMs?: number | null;
  summaryLines: string[];
  specs: SpecItem[];
  /** 한국 벤더가 앞, 그 안과 나머지는 원문 순서. 요약 전이거나 벤더를 모르면 빈 배열 */
  vendors: Vendor[];
  originalUrl: string;
  views: number;
  replies: number;
  statsCheckedAt: string;
  postedAt: string | null;
  /** postedAt이 없는 틀 글만: 글 번호로 추정한 작성 시각. postedAt이 있으면 늘 null */
  estimatedPostedAt: string | null;
  /** 목록에서 읽은 마지막 게시물(댓글) 시각. 아직 읽은 적 없으면 null */
  lastPostAt: string | null;
  /** false면 백엔드가 이번 요청에서 원문을 읽지 못한 틀 글(요약·스펙·이미지 없음) */
  detailLoaded: boolean;
}

/**
 * 같은 조건의 목록에서 한 글의 위아래 글. 둘 다 목록 순서대로다.
 * before의 마지막 원소가 바로 위 글, after의 첫 원소가 바로 아래 글.
 */
export interface PostNeighbors {
  before: PostCard[];
  after: PostCard[];
}

/** 메인 하이라이트 칸 종류. 이 순서로 앞 칸에 들어간 글을 뒤 칸에서 빼고 고른다(백엔드 enum HighlightSlot) */
export type HighlightSlotKind = "FEATURE" | "TALL" | "DEADLINE" | "IC_REPLIES" | "IC_VIEWS" | "GB_VIEWS";

/** 하이라이트 한 칸(백엔드 HighlightSlotResponse) */
export interface HighlightSlot {
  slot: HighlightSlotKind;
  /** DEADLINE 칸이 곧 끝나는 공동구매(마감 띠)로 뽑혔으면 true. false면 사진 칸으로 대신 채운 것 */
  deadline: boolean;
  /** 고른 창(최근 N일). 창을 넓혀 전체에서 골랐거나 마감 띠면 null */
  windowDays: number | null;
  /** 그 창의 조회(IC_REPLIES는 댓글) 순위. 마감 띠면 null */
  rank: number | null;
  post: PostCard;
}

/** 메인 하이라이트와 날짜 줄 숫자(백엔드 HighlightResponse) */
export interface Highlights {
  /** 서버가 칸을 고른 한국 시간 오늘("YYYY-MM-DD") */
  today: string;
  /** 최근 7일 새 IC 수 */
  newIcCount: number;
  /** 최근 7일 새 공동구매 수 */
  newGbCount: number;
  /** 오늘부터 14일 안에 마감하는 공동구매 수 */
  endingSoonGbCount: number;
  /** 고른 칸만 칸 순서대로. 후보가 없어 빈 칸은 빠진다 */
  slots: HighlightSlot[];
}

/** 사이트맵 글 한 건(백엔드 PostSitemapItemResponse). lastModified는 요약을 마친 시각이고 모르면 null */
export interface SitemapPost {
  id: number;
  lastModified: string | null;
}

/** RSS 글 한 건(백엔드 PostFeedItemResponse). summaryLines는 비어 있지 않다 */
export interface FeedPost {
  id: number;
  board: Board;
  title: string;
  productName: string | null;
  category: Category;
  summaryLines: string[];
  postedAt: string | null;
  /** 요약을 마친 시각. 이 열이 생기기 전에 요약한 글은 null */
  summarizedAt: string | null;
}

export interface PostStats {
  views: number;
  replies: number;
  statsCheckedAt: string;
}

// 목록 다음 묶음과 상세 화면의 조회수 갱신은 브라우저에서 호출하므로 NEXT_PUBLIC_ 변수를 쓴다.
export const API_BASE_URL = process.env.NEXT_PUBLIC_API_BASE_URL ?? "http://localhost:8080";

/** 백엔드 호출 시간 제한. 백엔드가 멈추거나 느리면 이 시간 뒤 실패로 보고 화면이 안내로 바뀐다 */
const API_TIMEOUT_MS = 8000;
/** 상세 시간 제한. 처음 여는 틀 글은 백엔드가 geekhack 요청 차례를 5초까지 기다린 뒤 원문을 읽어(시간 제한 10초) 채운다 */
const POST_TIMEOUT_MS = 15000;

/**
 * 백엔드 호출 옵션. 모든 호출에 시간 제한을 걸고, 호출자가 signal을 넘기면 둘 중 먼저 끊기는 쪽을 따른다.
 * 화면 서버에서 부를 때(isServer)만 토큰이 있으면 X-Keycheck-Server 헤더를 붙인다(Cloudflare 속도 제한에서 빼는 표시).
 */
export function apiRequestInit(
  isServer: boolean,
  token: string | undefined,
  signal?: AbortSignal,
  timeoutMs = API_TIMEOUT_MS,
): RequestInit {
  const timeout = AbortSignal.timeout(timeoutMs);
  const init: RequestInit = { signal: timeout };
  if (signal) {
    // AbortSignal.any가 없는 브라우저(Safari 17.4 전 등, Next 16 지원 범위 안)에서는 호출자의 signal만 따른다
    init.signal = typeof AbortSignal.any === "function" ? AbortSignal.any([signal, timeout]) : signal;
  }
  if (isServer && token) init.headers = { "X-Keycheck-Server": token };
  return init;
}

/** 모든 백엔드 호출이 거치는 함수. path는 /api부터 */
function apiFetch(path: string, signal?: AbortSignal, timeoutMs?: number): Promise<Response> {
  const init = apiRequestInit(typeof window === "undefined", process.env.KEYCHECK_SERVER_TOKEN, signal, timeoutMs);
  return fetch(`${API_BASE_URL}${path}`, init);
}

/** cursor 없이 부르면 첫 묶음. sub는 category와 맞는 값만 넘긴다(아니면 400). 서버와 브라우저 모두에서 부른다. */
export async function getPosts(
  board: BoardFilter,
  category: CategoryFilter,
  sort: PostSort,
  cursor?: string | null,
  sub?: SubFilter,
): Promise<PostBatch> {
  const query = new URLSearchParams({ board, category, sort });
  if (sub) query.set("sub", sub);
  if (cursor) query.set("cursor", cursor);
  const res = await apiFetch(`/api/posts?${query}`);
  if (!res.ok) throw new Error(`목록 조회 실패: ${res.status}`);
  return res.json();
}

/**
 * 메인 하이라이트. 서버에서 부르고, 한 렌더 안에서 여러 번 불러도 요청은 한 번이다.
 * signal을 넘긴 fetch는 Next가 같은 요청을 합치지 않으므로 cache로 묶는다.
 */
export const getHighlights = cache(async (): Promise<Highlights> => {
  const res = await apiFetch("/api/posts/highlights");
  if (!res.ok) throw new Error(`하이라이트 조회 실패: ${res.status}`);
  return res.json();
});

/**
 * 없거나 숨김인 글이면 null. 서버에서 부르고, 상세 화면의 요약 대기 중에는 브라우저에서도 부른다.
 * fill=false면 백엔드가 틀 글의 원문을 읽지 않고 그대로 돌려준다(봇 방문, 브라우저 재조회, 미리보기 카드). 그때는 기본 시간 제한을 쓴다.
 */
export async function getPost(
  id: number,
  signal?: AbortSignal,
  options?: { fill?: boolean },
): Promise<PostDetail | null> {
  const noFill = options?.fill === false;
  const path = noFill ? `/api/posts/${id}?fill=false` : `/api/posts/${id}`;
  const res = await apiFetch(path, signal, noFill ? undefined : POST_TIMEOUT_MS);
  if (res.status === 404) return null;
  if (!res.ok) throw new Error(`상세 조회 실패: ${res.status}`);
  return res.json();
}

/**
 * 상세 페이지와 generateMetadata가 함께 쓰는 서버 호출. 한 요청에서 한 번만 부른다.
 * signal을 넘긴 fetch는 Next가 같은 요청을 합치지 않으므로 cache로 묶는다. fill은 요청 UA로 정한다.
 */
export const getPostForPage = cache((id: number, fill: boolean) => getPost(id, undefined, { fill }));

/**
 * 사이트맵에 넣을 글(요약이 끝난 글만, 글 번호 내림차순). 서버에서 부른다.
 * lastModified가 없는 옛 백엔드 응답도 받도록 빠진 값은 null로 채운다
 */
export async function getSitemapPosts(): Promise<SitemapPost[]> {
  const res = await apiFetch("/api/posts/sitemap");
  if (!res.ok) throw new Error(`사이트맵 조회 실패: ${res.status}`);
  const items: { id: number; lastModified?: string | null }[] = await res.json();
  return items.map((item) => ({ id: item.id, lastModified: item.lastModified ?? null }));
}

/** RSS에 넣을 글(사이트맵과 같은 조건, 요약을 마친 순서로 최대 50건). 서버에서 부른다 */
export async function getFeedPosts(): Promise<FeedPost[]> {
  const res = await apiFetch("/api/posts/feed");
  if (!res.ok) throw new Error(`RSS 글 조회 실패: ${res.status}`);
  const posts: unknown = await res.json();
  // 모양이 다른 응답은 실패로 본다(RSS 라우트가 503으로 돌려준다)
  if (!Array.isArray(posts)) throw new Error("RSS 글 조회 실패: 배열이 아닌 응답");
  return posts as FeedPost[];
}

/** 조회수·댓글 수. 확인한 지 오래됐으면 백엔드가 geekhack에서 다시 읽는다(설계 4.3). 브라우저에서 호출한다. */
export async function getStats(id: number, signal?: AbortSignal): Promise<PostStats> {
  const res = await apiFetch(`/api/posts/${id}/stats`, signal);
  if (!res.ok) throw new Error(`조회수 갱신 실패: ${res.status}`);
  return res.json();
}

/** 상세 하단의 주변 글. query는 보던 목록 조건, size는 위아래 각각 최대 개수(1~10). 처음 페이지는 서버에서, 앞뒤 페이지는 브라우저에서 부른다. */
export async function getNeighbors(id: number, query: ListQuery, size: number): Promise<PostNeighbors> {
  const search = new URLSearchParams({ board: query.board, category: query.category, sort: query.sort, size: String(size) });
  if (query.sub) search.set("sub", query.sub);
  const res = await apiFetch(`/api/posts/${id}/neighbors?${search}`);
  if (!res.ok) throw new Error(`주변 글 조회 실패: ${res.status}`);
  return res.json();
}

/** 국내 판매처. 글의 Vendor(요약기가 뽑은 판매처)와 다르다 */
export type SaleVendor = "SWAGKEYS" | "GEONWORKS";
export type SaleCategory = "KEYBOARD" | "KEYCAP" | "SWITCH" | "PARTS";
export type SaleType = "SALE" | "PREORDER" | "RESTOCK" | "DROP" | "RAFFLE";
/** ANNOUNCE: 예고, OPEN: 판매 시작, CHANGE: 일정 변경 */
export type SalePhase = "ANNOUNCE" | "OPEN" | "CHANGE";
export type SaleVendorFilter = "all" | "swagkeys" | "geonworks";
export type SaleCategoryFilter = "all" | "keyboard" | "keycap" | "switch" | "parts";

/** 국내 판매 건(백엔드 SaleCardResponse). 날짜는 한국 날짜 "YYYY-MM-DD", 시각은 "HH:mm"(시간대 없음), 모르면 null */
export interface SaleCard {
  id: number;
  /** 이 건이 나온 공지 ID. 같은 공지의 건을 묶는 데 쓴다 */
  noticeId: number;
  vendor: SaleVendor;
  productName: string;
  category: SaleCategory;
  saleType: SaleType;
  phase: SalePhase;
  startDate: string | null;
  startTime: string | null;
  endDate: string | null;
  endTime: string | null;
  summaryFirstLine: string | null;
  /** 축소본(가로 640px). 사진이 없으면 null */
  thumbUrl: string | null;
  /** 큰 축소본(가로 1280px). 없으면 null */
  largeUrl: string | null;
  /** 보관본(가로 2048px). 홈 큰 칸·하이라이트·상세 맨 위처럼 넓은 자리용. 없으면 null */
  imageUrl: string | null;
  /** 디스코드 공지 시각(ISO) */
  postedAt: string;
}

export interface SaleBatch {
  items: SaleCard[];
  nextCursor: string | null;
  hasMore: boolean;
}

export interface SaleImage {
  url: string;
  largeUrl: string | null;
  thumbUrl: string | null;
}

export interface SaleDetail extends SaleCard {
  summaryLines: string[];
  shippingNote: string | null;
  links: { text: string; url: string }[];
  images: SaleImage[];
}

export interface SalePick {
  reason: "UPCOMING" | "CLOSING" | "RECENT";
  sale: SaleCard;
}

/** 국내 판매 목록. cursor 없이 부르면 첫 묶음. 서버와 브라우저 모두에서 부른다 */
export async function getSales(
  vendor: SaleVendorFilter,
  category: SaleCategoryFilter,
  cursor?: string | null,
): Promise<SaleBatch> {
  const query = new URLSearchParams({ vendor, category });
  if (cursor) query.set("cursor", cursor);
  const res = await apiFetch(`/api/sales?${query}`);
  if (!res.ok) throw new Error(`국내 판매 목록 조회 실패: ${res.status}`);
  return res.json();
}

/** 끝나지 않은 판매 건 전체(최대 300, 공지 시각 최근 순). 쪽 나눔 없음 */
export async function getSaleSchedule(
  vendor: SaleVendorFilter,
  category: SaleCategoryFilter,
): Promise<SaleCard[]> {
  const query = new URLSearchParams({ vendor, category });
  const res = await apiFetch(`/api/sales/schedule?${query}`);
  if (!res.ok) throw new Error(`국내 판매 일정 조회 실패: ${res.status}`);
  const body: { items: SaleCard[] } = await res.json();
  return body.items;
}

/** 끝난 판매 목록(판매 일정에 들지 않는 건 전부, 지난 시각 최근 순). cursor 없이 부르면 첫 쪽. 서버와 브라우저 모두에서 부른다 */
export async function getSalePast(
  vendor: SaleVendorFilter,
  category: SaleCategoryFilter,
  cursor?: string | null,
): Promise<SaleBatch> {
  const query = new URLSearchParams({ vendor, category });
  if (cursor) query.set("cursor", cursor);
  const res = await apiFetch(`/api/sales/past?${query}`);
  if (!res.ok) throw new Error(`국내 지난 판매 조회 실패: ${res.status}`);
  return res.json();
}

/** 없거나 숨김인 판매 건이면 null */
export async function getSale(id: number, signal?: AbortSignal): Promise<SaleDetail | null> {
  const res = await apiFetch(`/api/sales/${id}`, signal);
  if (res.status === 404) return null;
  if (!res.ok) throw new Error(`국내 판매 상세 조회 실패: ${res.status}`);
  return res.json();
}

/** 상세 페이지와 generateMetadata가 함께 쓰는 서버 호출. 한 요청에서 한 번만 부른다 */
export const getSaleForPage = cache((id: number) => getSale(id));

/** 메인 국내 판매 칸에 쓸 건들(곧 시작·곧 마감·최근 순). 서버에서 부른다 */
export async function getSaleHighlights(): Promise<SalePick[]> {
  const res = await apiFetch("/api/sales/highlights");
  if (!res.ok) throw new Error(`국내 판매 하이라이트 조회 실패: ${res.status}`);
  const body: { picks: SalePick[] } = await res.json();
  return body.picks;
}

/** 사이트맵에 넣을 판매 건. 서버에서 부른다 */
export async function getSaleSitemap(): Promise<SitemapPost[]> {
  const res = await apiFetch("/api/sales/sitemap");
  if (!res.ok) throw new Error(`국내 판매 사이트맵 조회 실패: ${res.status}`);
  const items: { id: number; lastModified?: string | null }[] = await res.json();
  return items.map((item) => ({ id: item.id, lastModified: item.lastModified ?? null }));
}

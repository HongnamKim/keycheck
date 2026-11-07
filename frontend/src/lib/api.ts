/** 백엔드 응답 타입과 호출 함수. 응답 형식은 backend의 post/dto와 맞춘다. */

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
  summaryLines: string[];
  specs: SpecItem[];
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

export interface PostStats {
  views: number;
  replies: number;
  statsCheckedAt: string;
}

// 목록 다음 묶음과 상세 화면의 조회수 갱신은 브라우저에서 호출하므로 NEXT_PUBLIC_ 변수를 쓴다.
export const API_BASE_URL = process.env.NEXT_PUBLIC_API_BASE_URL ?? "http://localhost:8080";

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
  const res = await fetch(`${API_BASE_URL}/api/posts?${query}`);
  if (!res.ok) throw new Error(`목록 조회 실패: ${res.status}`);
  return res.json();
}

/** 메인 하이라이트. 서버에서 부르고, 한 렌더 안에서 여러 번 불러도 요청은 한 번이다. */
export async function getHighlights(): Promise<Highlights> {
  const res = await fetch(`${API_BASE_URL}/api/posts/highlights`);
  if (!res.ok) throw new Error(`하이라이트 조회 실패: ${res.status}`);
  return res.json();
}

/** 없거나 숨김인 글이면 null. 서버에서 부르고, 상세 화면의 요약 대기 중에는 브라우저에서도 부른다. */
export async function getPost(id: number, signal?: AbortSignal): Promise<PostDetail | null> {
  const res = await fetch(`${API_BASE_URL}/api/posts/${id}`, { signal });
  if (res.status === 404) return null;
  if (!res.ok) throw new Error(`상세 조회 실패: ${res.status}`);
  return res.json();
}

/** 조회수·댓글 수. 확인한 지 오래됐으면 백엔드가 geekhack에서 다시 읽는다(설계 4.3). 브라우저에서 호출한다. */
export async function getStats(id: number, signal?: AbortSignal): Promise<PostStats> {
  const res = await fetch(`${API_BASE_URL}/api/posts/${id}/stats`, { signal });
  if (!res.ok) throw new Error(`조회수 갱신 실패: ${res.status}`);
  return res.json();
}

/** 상세 하단의 주변 글. query는 보던 목록 조건, size는 위아래 각각 최대 개수(1~10). 처음 페이지는 서버에서, 앞뒤 페이지는 브라우저에서 부른다. */
export async function getNeighbors(id: number, query: ListQuery, size: number): Promise<PostNeighbors> {
  const search = new URLSearchParams({ board: query.board, category: query.category, sort: query.sort, size: String(size) });
  if (query.sub) search.set("sub", query.sub);
  const res = await fetch(`${API_BASE_URL}/api/posts/${id}/neighbors?${search}`);
  if (!res.ok) throw new Error(`주변 글 조회 실패: ${res.status}`);
  return res.json();
}

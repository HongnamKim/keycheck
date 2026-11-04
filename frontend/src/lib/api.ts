/** 백엔드 응답 타입과 호출 함수. 응답 형식은 backend의 post/dto와 맞춘다. */

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
  category: Category;
  subcategory: Subcategory | null;
  representativeImageUrl: string | null;
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

export interface PostDetail {
  id: number;
  board: Board;
  title: string;
  author: string;
  category: Category;
  subcategory: Subcategory | null;
  representativeImageUrl: string | null;
  /** 규칙에서 빠지지 않은 후보 이미지 전체, 본문 순서 (대표 이미지 포함) */
  imageUrls: string[];
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

/** 없거나 숨김인 글이면 null */
export async function getPost(id: number): Promise<PostDetail | null> {
  const res = await fetch(`${API_BASE_URL}/api/posts/${id}`);
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

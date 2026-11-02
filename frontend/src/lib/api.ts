/** 백엔드 응답 타입과 호출 함수. 응답 형식은 backend의 post/dto와 맞춘다. */

export type Category = "KEYBOARD" | "KEYCAP" | "OTHER";
export type SummaryStatus = "PENDING" | "DONE" | "FAILED";
export type CategoryFilter = "all" | "keyboard" | "keycap";
export type PostSort = "recent" | "views";

export interface PostCard {
  id: number;
  title: string;
  category: Category;
  representativeImageUrl: string | null;
  summaryStatus: SummaryStatus;
  summaryFirstLine: string | null;
  views: number;
  replies: number;
  statsCheckedAt: string;
  postedAt: string | null;
}

export interface PostPage {
  items: PostCard[];
  page: number;
  totalPages: number;
}

export interface SpecItem {
  label: string;
  value: string;
}

export interface PostDetail {
  id: number;
  title: string;
  author: string;
  category: Category;
  representativeImageUrl: string | null;
  summaryStatus: SummaryStatus;
  summaryLines: string[];
  specs: SpecItem[];
  originalUrl: string;
  views: number;
  replies: number;
  statsCheckedAt: string;
  postedAt: string | null;
}

// 상세 화면의 조회수 갱신은 브라우저에서 호출하므로 NEXT_PUBLIC_ 변수를 쓴다.
export const API_BASE_URL = process.env.NEXT_PUBLIC_API_BASE_URL ?? "http://localhost:8080";

export async function getPosts(category: CategoryFilter, sort: PostSort, page: number): Promise<PostPage> {
  const query = new URLSearchParams({ category, sort, page: String(page) });
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

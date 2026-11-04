/**
 * 상세 하단 주변 글 목록의 페이지 계산. 페이지는 목록 순서(위가 앞)대로 글을 담고,
 * 앞뒤 페이지는 지금 페이지의 첫 글·마지막 글을 기준으로 주변 글 API를 불러 만든다.
 */

/** 앞뒤 페이지 한 쪽의 글 수. 주변 글 API size 상한(10)과 같다 */
export const NEIGHBOR_PAGE_SIZE = 10;

export interface NeighborPage<T> {
  items: T[];
  /** 위(목록 앞쪽)에 글이 더 있을 수 있는지 */
  hasPrev: boolean;
  /** 아래(목록 뒤쪽)에 글이 더 있을 수 있는지 */
  hasNext: boolean;
}

/**
 * 처음 페이지: 위 글 + 지금 글 + 아래 글. size는 위아래를 각각 몇 개까지 받았는지다.
 * 받은 수가 size보다 적은 쪽은 목록(확보한 범위)의 끝이다.
 */
export function initialPage<T>(before: T[], current: T, after: T[], size: number): NeighborPage<T> {
  return {
    items: [...before, current, ...after],
    hasPrev: before.length >= size,
    hasNext: after.length >= size,
  };
}

/** 지금 페이지 마지막 글의 아래 글(after)로 다음 페이지를 만든다. 비었으면 지금 페이지에서 "다음"만 끈다 */
export function nextPage<T>(page: NeighborPage<T>, after: T[], size: number): NeighborPage<T> {
  if (after.length === 0) return { ...page, hasNext: false };
  return { items: after, hasPrev: true, hasNext: after.length >= size };
}

/** 지금 페이지 첫 글의 위 글(before)로 이전 페이지를 만든다. 비었으면 지금 페이지에서 "이전"만 끈다 */
export function prevPage<T>(page: NeighborPage<T>, before: T[], size: number): NeighborPage<T> {
  if (before.length === 0) return { ...page, hasPrev: false };
  return { items: before, hasPrev: before.length >= size, hasNext: true };
}

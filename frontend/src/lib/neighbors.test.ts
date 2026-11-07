import { describe, expect, it } from "vitest";
import { initialPage, nextPage, prevPage } from "./neighbors";

const range = (from: number, count: number) => Array.from({ length: count }, (_, i) => from + i);

describe("initialPage", () => {
  it("위 글, 지금 글, 아래 글 순서로 담는다", () => {
    expect(initialPage([1, 2], 3, [4, 5], 2).items).toEqual([1, 2, 3, 4, 5]);
  });

  it("위아래를 size만큼 받았으면 앞뒤로 더 있을 수 있다", () => {
    const page = initialPage(range(1, 5), 6, range(7, 5), 5);
    expect(page.hasPrev).toBe(true);
    expect(page.hasNext).toBe(true);
  });

  it("size보다 적게 받은 쪽은 끝이다", () => {
    const page = initialPage([1, 2], 3, [], 5);
    expect(page.hasPrev).toBe(false);
    expect(page.hasNext).toBe(false);
  });
});

describe("initialPage: 위아래 side개씩, 모자란 쪽은 다른 쪽으로 채워 늘 같은 줄 수", () => {
  // 위아래를 10개씩 받아 5개씩 보여 준다(지금 글 포함 11줄). 지금 글은 11
  const page = (before: number[], after: number[]) => initialPage(before, 11, after, 10, 5);

  it("목록 가운데면 위 5개, 아래 5개", () => {
    const result = page(range(1, 10), range(12, 10));
    expect(result.items).toEqual([6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16]);
    expect(result.hasPrev).toBe(true);
    expect(result.hasNext).toBe(true);
  });

  it("위가 모자라면(목록 맨 위쪽) 아래를 더 보여 준다", () => {
    const result = page([10], range(12, 10));
    expect(result.items).toEqual([10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20]);
    expect(result.hasPrev).toBe(false);
    expect(result.hasNext).toBe(true);
  });

  it("목록 첫 글이면 아래만 10개", () => {
    const result = page([], range(12, 10));
    expect(result.items).toEqual([11, ...range(12, 10)]);
    expect(result.hasPrev).toBe(false);
    expect(result.hasNext).toBe(true);
  });

  it("아래가 모자라면(목록 끝쪽) 위를 더 보여 준다", () => {
    const result = page(range(1, 10), [12, 13]);
    expect(result.items).toEqual([3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13]);
    expect(result.hasPrev).toBe(true);
    expect(result.hasNext).toBe(false);
  });

  it("아래 글을 받은 만큼 다 보여 줬고 10개보다 적으면 다음이 없다", () => {
    const result = page([10], range(12, 9));
    expect(result.items).toHaveLength(11);
    expect(result.hasNext).toBe(false);
  });

  it("글이 적으면 있는 만큼만", () => {
    const result = page([9, 10], [12, 13, 14]);
    expect(result.items).toEqual([9, 10, 11, 12, 13, 14]);
    expect(result.hasPrev).toBe(false);
    expect(result.hasNext).toBe(false);
  });
});

describe("nextPage", () => {
  const page = initialPage(range(1, 5), 6, range(7, 5), 5);

  it("아래 글로 페이지를 바꾸고 이전 페이지로 돌아갈 수 있다", () => {
    const next = nextPage(page, range(12, 10), 10);
    expect(next.items).toEqual(range(12, 10));
    expect(next.hasPrev).toBe(true);
    expect(next.hasNext).toBe(true);
  });

  it("size보다 적게 받으면 마지막 페이지다", () => {
    expect(nextPage(page, [12, 13], 10).hasNext).toBe(false);
  });

  it("아래 글이 없으면 지금 페이지를 그대로 두고 다음만 끈다", () => {
    const next = nextPage(page, [], 10);
    expect(next.items).toEqual(page.items);
    expect(next.hasPrev).toBe(true);
    expect(next.hasNext).toBe(false);
  });
});

describe("prevPage", () => {
  const page = initialPage(range(11, 5), 16, range(17, 5), 5);

  it("위 글로 페이지를 바꾸고 다음 페이지로 돌아갈 수 있다", () => {
    const prev = prevPage(page, range(1, 10), 10);
    expect(prev.items).toEqual(range(1, 10));
    expect(prev.hasPrev).toBe(true);
    expect(prev.hasNext).toBe(true);
  });

  it("size보다 적게 받으면 첫 페이지다", () => {
    expect(prevPage(page, [9, 10], 10).hasPrev).toBe(false);
  });

  it("위 글이 없으면 지금 페이지를 그대로 두고 이전만 끈다", () => {
    const prev = prevPage(page, [], 10);
    expect(prev.items).toEqual(page.items);
    expect(prev.hasPrev).toBe(false);
    expect(prev.hasNext).toBe(true);
  });
});

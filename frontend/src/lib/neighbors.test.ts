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

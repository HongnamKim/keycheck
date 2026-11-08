import { describe, expect, it } from "vitest";
import { parseSaleDetailQuery, parseSaleListQuery, saleBackHref, saleHref, saleListHref } from "./saleQuery";

describe("parseSaleListQuery", () => {
  it("잘못된 값은 all, 보기는 공지 순", () => {
    expect(parseSaleListQuery({ vendor: "x", category: ["keycap"], view: "grid" })).toEqual({
      vendor: "all",
      category: "all",
      view: "notices",
    });
    expect(parseSaleListQuery({})).toEqual({ vendor: "all", category: "all", view: "notices" });
  });

  it("올바른 값은 그대로", () => {
    expect(parseSaleListQuery({ vendor: "geonworks", category: "parts", view: "schedule" })).toEqual({
      vendor: "geonworks",
      category: "parts",
      view: "schedule",
    });
  });
});

describe("saleListHref", () => {
  it("기본값은 뺀다", () => {
    expect(saleListHref({ vendor: "all", category: "all", view: "notices" })).toBe("/sales");
    expect(saleListHref({ vendor: "geonworks", category: "keycap", view: "notices" })).toBe(
      "/sales?vendor=geonworks&category=keycap",
    );
    expect(saleListHref({ vendor: "all", category: "switch", view: "notices" })).toBe("/sales?category=switch");
  });

  it("판매 일정 순이면 view=schedule을 싣는다", () => {
    expect(saleListHref({ vendor: "all", category: "all", view: "schedule" })).toBe("/sales?view=schedule");
    expect(saleListHref({ vendor: "geonworks", category: "keycap", view: "schedule" })).toBe(
      "/sales?view=schedule&vendor=geonworks&category=keycap",
    );
  });
});

describe("saleHref", () => {
  it("메인에서 열면 쿼리가 없다", () => {
    expect(saleHref(3)).toBe("/sales/3");
    expect(saleHref(3, null)).toBe("/sales/3");
  });

  it("목록에서 열면 list=1과 보던 보기·필터를 싣는다", () => {
    expect(saleHref(3, { vendor: "all", category: "all", view: "notices" })).toBe("/sales/3?list=1");
    expect(saleHref(3, { vendor: "geonworks", category: "all", view: "notices" })).toBe("/sales/3?list=1&vendor=geonworks");
    expect(saleHref(3, { vendor: "geonworks", category: "all", view: "schedule" })).toBe(
      "/sales/3?list=1&view=schedule&vendor=geonworks",
    );
  });
});

describe("parseSaleDetailQuery", () => {
  it("list=1이면 목록 조건, 없으면 null", () => {
    expect(parseSaleDetailQuery({ list: "1", vendor: "geonworks" })).toEqual({
      vendor: "geonworks",
      category: "all",
      view: "notices",
    });
    expect(parseSaleDetailQuery({ list: "1", view: "schedule" })).toEqual({ vendor: "all", category: "all", view: "schedule" });
    expect(parseSaleDetailQuery({ vendor: "geonworks", view: "schedule" })).toBeNull();
  });
});

describe("saleBackHref", () => {
  it("목록에서 왔으면 그 목록, 아니면 메인", () => {
    expect(saleBackHref(null)).toBe("/");
    expect(saleBackHref({ vendor: "geonworks", category: "all", view: "notices" })).toBe("/sales?vendor=geonworks");
    expect(saleBackHref({ vendor: "geonworks", category: "all", view: "schedule" })).toBe(
      "/sales?view=schedule&vendor=geonworks",
    );
  });

  it("목록 → 상세 → 목록으로가 같은 보기·필터로 돌아온다", () => {
    const from = { vendor: "swagkeys", category: "keycap", view: "schedule" } as const;
    const detail = new URL(saleHref(7, from), "https://keycheck.test");
    const back = saleBackHref(parseSaleDetailQuery(Object.fromEntries(detail.searchParams)));
    expect(back).toBe(saleListHref(from));
  });
});

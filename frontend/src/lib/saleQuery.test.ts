import { describe, expect, it } from "vitest";
import { parseSaleDetailQuery, parseSaleListQuery, saleBackHref, saleHref, saleListHref } from "./saleQuery";

describe("parseSaleListQuery", () => {
  it("잘못된 값은 all", () => {
    expect(parseSaleListQuery({ vendor: "x", category: ["keycap"] })).toEqual({ vendor: "all", category: "all" });
    expect(parseSaleListQuery({})).toEqual({ vendor: "all", category: "all" });
  });

  it("올바른 값은 그대로", () => {
    expect(parseSaleListQuery({ vendor: "geonworks", category: "parts" })).toEqual({ vendor: "geonworks", category: "parts" });
  });
});

describe("saleListHref", () => {
  it("기본값은 뺀다", () => {
    expect(saleListHref({ vendor: "all", category: "all" })).toBe("/sales");
    expect(saleListHref({ vendor: "geonworks", category: "keycap" })).toBe("/sales?vendor=geonworks&category=keycap");
    expect(saleListHref({ vendor: "all", category: "switch" })).toBe("/sales?category=switch");
  });
});

describe("saleHref", () => {
  it("메인에서 열면 쿼리가 없다", () => {
    expect(saleHref(3)).toBe("/sales/3");
    expect(saleHref(3, null)).toBe("/sales/3");
  });

  it("목록에서 열면 list=1과 보던 필터를 싣는다", () => {
    expect(saleHref(3, { vendor: "all", category: "all" })).toBe("/sales/3?list=1");
    expect(saleHref(3, { vendor: "geonworks", category: "all" })).toBe("/sales/3?list=1&vendor=geonworks");
  });
});

describe("parseSaleDetailQuery", () => {
  it("list=1이면 목록 조건, 없으면 null", () => {
    expect(parseSaleDetailQuery({ list: "1", vendor: "geonworks" })).toEqual({ vendor: "geonworks", category: "all" });
    expect(parseSaleDetailQuery({ vendor: "geonworks" })).toBeNull();
  });
});

describe("saleBackHref", () => {
  it("목록에서 왔으면 그 목록, 아니면 메인", () => {
    expect(saleBackHref(null)).toBe("/");
    expect(saleBackHref({ vendor: "geonworks", category: "all" })).toBe("/sales?vendor=geonworks");
  });
});

import { describe, expect, it } from "vitest";
import { listHref, parseListQuery, parsePostId, postHref, subOptions, withCategory } from "./query";

describe("parseListQuery", () => {
  it("값이 없으면 기본값(all, recent)", () => {
    expect(parseListQuery({})).toEqual({ category: "all", sort: "recent" });
  });

  it("올바른 값은 그대로 읽는다", () => {
    expect(parseListQuery({ category: "keycap", sort: "views" })).toEqual({ category: "keycap", sort: "views" });
    expect(parseListQuery({ category: "keyboard" }).category).toBe("keyboard");
  });

  it.each([["other"], ["KEYCAP"], [""], [["keycap", "keyboard"]]])("잘못된 category %j → all", (category) => {
    expect(parseListQuery({ category }).category).toBe("all");
  });

  it.each([["popular"], ["VIEWS"], [["views"]]])("잘못된 sort %j → recent", (sort) => {
    expect(parseListQuery({ sort }).sort).toBe("recent");
  });

  it("예전 주소의 page 쿼리는 무시한다", () => {
    expect(parseListQuery({ category: "keycap", page: "2" })).toEqual({ category: "keycap", sort: "recent" });
  });

  it("분류와 맞는 sub는 읽는다", () => {
    expect(parseListQuery({ category: "keyboard", sub: "65" })).toEqual({ category: "keyboard", sub: "65", sort: "recent" });
    expect(parseListQuery({ category: "keycap", sub: "gmk", sort: "views" }).sub).toBe("gmk");
    expect(parseListQuery({ category: "keyboard", sub: "etc" }).sub).toBe("etc");
    expect(parseListQuery({ category: "keycap", sub: "etc" }).sub).toBe("etc");
  });

  it.each<[string, string | string[]]>([
    ["all", "65"],
    ["keyboard", "gmk"],
    ["keycap", "65"],
    ["keyboard", "KB_65"],
    ["keyboard", ""],
    ["keycap", ["gmk"]],
  ])("category %s에 맞지 않는 sub %j는 버린다", (category, sub) => {
    expect(parseListQuery({ category, sub }).sub).toBeUndefined();
  });

  it("잘못된 category면 sub도 버린다", () => {
    expect(parseListQuery({ category: "other", sub: "65" })).toEqual({ category: "all", sort: "recent" });
  });
});

describe("subOptions", () => {
  it("키보드는 배열 9개를 주소 값과 세부 분류 코드로", () => {
    expect(subOptions("keyboard").map((o) => o.sub)).toEqual(["100", "96", "80", "75", "65", "60", "40", "ergo", "etc"]);
    expect(subOptions("keyboard").map((o) => o.code)).toEqual([
      "KB_100",
      "KB_96",
      "KB_80",
      "KB_75",
      "KB_65",
      "KB_60",
      "KB_40",
      "KB_ERGO",
      "KB_ETC",
    ]);
  });

  it("키캡은 제조사 6개", () => {
    expect(subOptions("keycap").map((o) => o.sub)).toEqual(["gmk", "sp", "mw", "kkb", "pbtfans", "etc"]);
    expect(subOptions("keycap").map((o) => o.code)).toEqual(["KC_GMK", "KC_SP", "KC_MW", "KC_KKB", "KC_PBTFANS", "KC_ETC"]);
  });

  it("전체에는 세부 분류가 없다", () => {
    expect(subOptions("all")).toEqual([]);
  });
});

describe("withCategory", () => {
  it("분류를 바꾸면 sub는 비우고 정렬은 둔다", () => {
    expect(withCategory({ category: "keyboard", sub: "65", sort: "views" }, "keycap")).toEqual({
      category: "keycap",
      sort: "views",
    });
  });

  it("같은 분류를 다시 골라도 sub를 비운다(그 분류 전체)", () => {
    expect(withCategory({ category: "keyboard", sub: "65", sort: "recent" }, "keyboard")).toEqual({
      category: "keyboard",
      sort: "recent",
    });
  });
});

describe("listHref", () => {
  it("기본값은 쿼리에서 뺀다", () => {
    expect(listHref({ category: "all", sort: "recent" })).toBe("/");
  });

  it("기본값이 아닌 값만 category, sort 순서로 붙인다", () => {
    expect(listHref({ category: "keycap", sort: "views" })).toBe("/?category=keycap&sort=views");
    expect(listHref({ category: "all", sort: "views" })).toBe("/?sort=views");
    expect(listHref({ category: "keyboard", sort: "recent" })).toBe("/?category=keyboard");
  });

  it("sub는 category와 sort 사이에 붙인다", () => {
    expect(listHref({ category: "keyboard", sub: "65", sort: "views" })).toBe("/?category=keyboard&sub=65&sort=views");
    expect(listHref({ category: "keycap", sub: "pbtfans", sort: "recent" })).toBe("/?category=keycap&sub=pbtfans");
  });
});

describe("postHref", () => {
  it("보던 목록의 필터·정렬을 상세 주소에 싣는다", () => {
    expect(postHref(26, { category: "keycap", sort: "views" })).toBe("/posts/26?category=keycap&sort=views");
  });

  it("세부 분류도 상세 주소에 싣는다", () => {
    expect(postHref(26, { category: "keyboard", sub: "ergo", sort: "recent" })).toBe("/posts/26?category=keyboard&sub=ergo");
  });

  it("기본 목록이면 쿼리 없이 상세 주소만", () => {
    expect(postHref(26, { category: "all", sort: "recent" })).toBe("/posts/26");
  });

  it.each([
    { category: "keyboard", sort: "views" },
    { category: "keycap", sub: "kkb", sort: "views" },
  ] as const)("상세 주소의 쿼리를 다시 읽으면 보던 목록 주소로 돌아간다 %j", (query) => {
    const search = new URL(postHref(7, query), "http://localhost").searchParams;
    expect(listHref(parseListQuery(Object.fromEntries(search)))).toBe(listHref(query));
  });
});

describe("parsePostId", () => {
  it("양의 정수 문자열이면 숫자", () => {
    expect(parsePostId("42")).toBe(42);
  });

  it.each([["0"], ["-1"], ["abc"], ["1.5"], ["1e3"], ["042"], [""], ["99999999999999999999"]])(
    "%j → null",
    (id) => {
      expect(parsePostId(id)).toBeNull();
    },
  );
});

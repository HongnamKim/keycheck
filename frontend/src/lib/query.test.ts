import { describe, expect, it } from "vitest";
import {
  backHref,
  listHref,
  navCurrent,
  parseDetailQuery,
  parseListQuery,
  parsePostId,
  postHref,
  subOptions,
  withCategory,
} from "./query";

describe("parseListQuery", () => {
  it("값이 없으면 기본값(all, recent)", () => {
    expect(parseListQuery("ic", {})).toEqual({ board: "ic", category: "all", sort: "recent" });
  });

  it("게시판은 주소(경로)에서 받은 값을 그대로 쓴다", () => {
    expect(parseListQuery("gb", {})).toEqual({ board: "gb", category: "all", sort: "recent" });
    expect(parseListQuery("gb", { board: "ic", category: "keycap" }).board).toBe("gb");
  });

  it("올바른 값은 그대로 읽는다", () => {
    expect(parseListQuery("ic", { category: "keycap", sort: "views" })).toEqual({ board: "ic", category: "keycap", sort: "views" });
    expect(parseListQuery("ic", { category: "keyboard" }).category).toBe("keyboard");
  });

  it.each([["other"], ["KEYCAP"], [""], [["keycap", "keyboard"]]])("잘못된 category %j → all", (category) => {
    expect(parseListQuery("ic", { category }).category).toBe("all");
  });

  it.each([["popular"], ["VIEWS"], [["views"]]])("잘못된 sort %j → recent", (sort) => {
    expect(parseListQuery("ic", { sort }).sort).toBe("recent");
  });

  it("예전 주소의 page 쿼리는 무시한다", () => {
    expect(parseListQuery("ic", { category: "keycap", page: "2" })).toEqual({ board: "ic", category: "keycap", sort: "recent" });
  });

  it("분류와 맞는 sub는 읽는다", () => {
    expect(parseListQuery("ic", { category: "keyboard", sub: "65" })).toEqual({
      board: "ic",
      category: "keyboard",
      sub: "65",
      sort: "recent",
    });
    expect(parseListQuery("ic", { category: "keycap", sub: "gmk", sort: "views" }).sub).toBe("gmk");
    expect(parseListQuery("ic", { category: "keyboard", sub: "etc" }).sub).toBe("etc");
    expect(parseListQuery("ic", { category: "keycap", sub: "etc" }).sub).toBe("etc");
  });

  it.each<[string, string | string[]]>([
    ["all", "65"],
    ["keyboard", "gmk"],
    ["keycap", "65"],
    ["keyboard", "KB_65"],
    ["keyboard", ""],
    ["keycap", ["gmk"]],
  ])("category %s에 맞지 않는 sub %j는 버린다", (category, sub) => {
    expect(parseListQuery("ic", { category, sub }).sub).toBeUndefined();
  });

  it("잘못된 category면 sub도 버린다", () => {
    expect(parseListQuery("ic", { category: "other", sub: "65" })).toEqual({ board: "ic", category: "all", sort: "recent" });
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
  it("분류를 바꾸면 sub는 비우고 게시판·정렬은 둔다", () => {
    expect(withCategory({ board: "gb", category: "keyboard", sub: "65", sort: "views" }, "keycap")).toEqual({
      board: "gb",
      category: "keycap",
      sort: "views",
    });
  });

  it("같은 분류를 다시 골라도 sub를 비운다(그 분류 전체)", () => {
    expect(withCategory({ board: "ic", category: "keyboard", sub: "65", sort: "recent" }, "keyboard")).toEqual({
      board: "ic",
      category: "keyboard",
      sort: "recent",
    });
  });
});

describe("listHref", () => {
  it("게시판 경로에 기본값을 뺀 쿼리를 붙인다", () => {
    expect(listHref({ board: "ic", category: "all", sort: "recent" })).toBe("/ic");
    expect(listHref({ board: "gb", category: "all", sort: "recent" })).toBe("/gb");
  });

  it("기본값이 아닌 값만 category, sort 순서로 붙인다", () => {
    expect(listHref({ board: "ic", category: "keycap", sort: "views" })).toBe("/ic?category=keycap&sort=views");
    expect(listHref({ board: "gb", category: "all", sort: "views" })).toBe("/gb?sort=views");
    expect(listHref({ board: "ic", category: "keyboard", sort: "recent" })).toBe("/ic?category=keyboard");
  });

  it("sub는 category와 sort 사이에 붙인다", () => {
    expect(listHref({ board: "ic", category: "keyboard", sub: "65", sort: "views" })).toBe(
      "/ic?category=keyboard&sub=65&sort=views",
    );
    expect(listHref({ board: "gb", category: "keycap", sub: "pbtfans", sort: "recent" })).toBe(
      "/gb?category=keycap&sub=pbtfans",
    );
  });
});

describe("postHref", () => {
  it("보던 목록의 게시판·필터·정렬을 상세 주소에 싣는다", () => {
    expect(postHref(26, { board: "gb", category: "keycap", sort: "views" })).toBe(
      "/posts/26?board=gb&category=keycap&sort=views",
    );
  });

  it("세부 분류도 상세 주소에 싣는다", () => {
    expect(postHref(26, { board: "ic", category: "keyboard", sub: "ergo", sort: "recent" })).toBe(
      "/posts/26?board=ic&category=keyboard&sub=ergo",
    );
  });

  it("기본 목록이면 게시판만 싣는다", () => {
    expect(postHref(26, { board: "ic", category: "all", sort: "recent" })).toBe("/posts/26?board=ic");
  });

  it("목록이 아닌 곳(메인)에서 열면 쿼리 없이 상세 주소만", () => {
    expect(postHref(26)).toBe("/posts/26");
    expect(postHref(26, null)).toBe("/posts/26");
  });
});

describe("parseDetailQuery", () => {
  it("board가 없으면 목록에서 온 것이 아니라 null", () => {
    expect(parseDetailQuery({})).toBeNull();
    expect(parseDetailQuery({ category: "keycap", sort: "views" })).toBeNull();
  });

  it.each([["all"], ["IC"], ["etc"], [""], [["ic"]]])("잘못된 board %j → null", (board) => {
    expect(parseDetailQuery({ board, category: "keycap" })).toBeNull();
  });

  it("board가 있으면 그 게시판 목록의 쿼리로 읽는다(잘못된 값은 목록과 같이 기본값)", () => {
    expect(parseDetailQuery({ board: "gb", category: "keyboard", sub: "65", sort: "views" })).toEqual({
      board: "gb",
      category: "keyboard",
      sub: "65",
      sort: "views",
    });
    expect(parseDetailQuery({ board: "ic", category: "other", sub: "65" })).toEqual({
      board: "ic",
      category: "all",
      sort: "recent",
    });
  });

  it.each([
    { board: "ic", category: "all", sort: "recent" },
    { board: "gb", category: "keyboard", sort: "views" },
    { board: "ic", category: "keycap", sub: "kkb", sort: "views" },
  ] as const)("상세 주소의 쿼리를 다시 읽으면 보던 목록 주소로 돌아간다 %j", (query) => {
    const search = new URL(postHref(7, query), "http://localhost").searchParams;
    expect(listHref(parseDetailQuery(Object.fromEntries(search))!)).toBe(listHref(query));
  });
});

describe("backHref", () => {
  it("목록에서 왔으면 그 목록으로", () => {
    expect(backHref({ board: "gb", category: "keycap", sort: "views" })).toBe("/gb?category=keycap&sort=views");
  });

  it("목록에서 오지 않았으면 메인으로", () => {
    expect(backHref(null)).toBe("/");
  });
});

describe("navCurrent", () => {
  it("게시판 목록에 있으면 그 게시판이 지금 페이지", () => {
    expect(navCurrent("ic", "/ic", null)).toBe("page");
    expect(navCurrent("gb", "/gb", "ic")).toBe("page");
    expect(navCurrent("ic", "/gb", null)).toBeUndefined();
  });

  it("목록에서 연 상세면 그 게시판을 표시만 한다(지금 페이지는 아님)", () => {
    expect(navCurrent("gb", "/posts/12", "gb")).toBe("true");
    expect(navCurrent("ic", "/posts/12", "gb")).toBeUndefined();
  });

  it("메인과 메인에서 연 상세는 아무것도 표시하지 않는다", () => {
    expect(navCurrent("ic", "/", null)).toBeUndefined();
    expect(navCurrent("ic", "/posts/12", null)).toBeUndefined();
    expect(navCurrent("ic", "/", "ic")).toBeUndefined();
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

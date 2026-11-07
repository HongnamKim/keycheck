import { describe, expect, it } from "vitest";
import {
  boardLabel,
  categoryLabel,
  formatAgo,
  formatCheckedAt,
  formatDateline,
  formatLastPost,
  formatMonthDay,
  formatPostedAt,
  formatPostedDate,
  formatShortDate,
  formatShortPostedDate,
  formatViews,
  kstDateKey,
  listTitle,
  photoChipLabel,
  postCategoryLabel,
  subcategoryLabel,
} from "./format";

describe("formatViews", () => {
  it.each([
    [0, "0"],
    [999, "999"],
    [1_000, "1,000+"],
    [3_456, "3,400+"],
    [9_999, "9,900+"],
    [10_000, "1만+"],
    [12_345, "1.2만+"],
    [80_000, "8만+"],
    [80_999, "8만+"],
    [123_456, "12.3만+"],
    [12_345_678, "1,234.5만+"],
  ])("%i → %s", (views, expected) => {
    expect(formatViews(views)).toBe(expected);
  });
});

describe("formatAgo", () => {
  const now = new Date("2026-11-02T12:00:00Z");
  const ago = (ms: number) => new Date(now.getTime() - ms);
  const MINUTE = 60_000;
  const HOUR = 60 * MINUTE;
  const DAY = 24 * HOUR;

  it.each([
    [0, "방금"],
    [59_999, "방금"],
    [MINUTE, "1분 전"],
    [59 * MINUTE, "59분 전"],
    [HOUR, "1시간 전"],
    [23 * HOUR + 59 * MINUTE, "23시간 전"],
    [DAY, "1일 전"],
    [400 * DAY, "400일 전"],
    [-5 * MINUTE, "방금"],
  ])("%i ms 전 → %s", (ms, expected) => {
    expect(formatAgo(ago(ms), now)).toBe(expected);
  });
});

describe("formatCheckedAt", () => {
  const now = new Date("2026-11-02T12:00:00Z");
  const ago = (ms: number) => new Date(now.getTime() - ms);
  const MINUTE = 60_000;
  const HOUR = 60 * MINUTE;

  it.each([
    [0, "방금 확인"],
    [59_999, "방금 확인"],
    [MINUTE, "1분 전 확인"],
    [59 * MINUTE, "59분 전 확인"],
    [HOUR, "1시간 전 확인"],
    [23 * HOUR + 59 * MINUTE, "23시간 전 확인"],
    [24 * HOUR, "1일 전 확인"],
    [-5 * MINUTE, "방금 확인"],
  ])("%i ms 전 → %s", (ms, expected) => {
    expect(formatCheckedAt(ago(ms), now)).toBe(expected);
  });
});

describe("formatPostedAt", () => {
  it.each([
    ["2026-10-30T03:00:00Z", "2026.10.30"],
    ["2026-10-30T15:30:00Z", "2026.10.31"],
    ["2026-01-05T00:00:00Z", "2026.01.05"],
  ])("%s → %s (한국 시간)", (iso, expected) => {
    expect(formatPostedAt(iso)).toBe(expected);
  });
});

describe("kstDateKey", () => {
  it.each([
    ["2026-11-06T14:59:59Z", "2026-11-06"],
    ["2026-11-06T15:00:00Z", "2026-11-07"],
    ["2026-12-31T15:00:00Z", "2027-01-01"],
  ])("%s → %s (한국 시간 오늘)", (iso, expected) => {
    expect(kstDateKey(new Date(iso))).toBe(expected);
  });
});

describe("formatDateline", () => {
  it.each([
    ["2026-11-07", "2026.11.07 토"],
    ["2026-11-08", "2026.11.08 일"],
    ["2026-01-05", "2026.01.05 월"],
  ])("%s → %s", (today, expected) => {
    expect(formatDateline(today)).toBe(expected);
  });
});

describe("formatShortDate", () => {
  it.each([
    ["2026-10-31T16:00:00Z", "11/1"],
    ["2026-10-31T14:59:59Z", "10/31"],
    ["2026-01-05T00:00:00Z", "1/5"],
  ])("%s → %s (한국 시간 월/일)", (iso, expected) => {
    expect(formatShortDate(iso)).toBe(expected);
  });
});

describe("formatShortPostedDate", () => {
  it("정확한 작성일이 있으면 한국 시간 월/일", () => {
    expect(formatShortPostedDate("2026-10-31T16:00:00Z", null)).toBe("11/1");
  });

  it("정확한 작성일이 있으면 추정값은 쓰지 않는다", () => {
    expect(formatShortPostedDate("2026-10-30T03:00:00Z", "2026-01-01T00:00:00Z")).toBe("10/30");
  });

  it("추정값만 있으면 쯤을 붙인다", () => {
    expect(formatShortPostedDate(null, "2026-07-14T03:00:00Z")).toBe("7/14쯤");
  });

  it("둘 다 없으면 null", () => {
    expect(formatShortPostedDate(null, null)).toBeNull();
  });
});

describe("formatMonthDay", () => {
  it("날짜 문자열을 월/일로. 앞자리 0은 뺀다", () => {
    expect(formatMonthDay("2026-11-13")).toBe("11/13");
    expect(formatMonthDay("2026-07-06")).toBe("7/6");
  });

  it("withWeekday면 요일을 괄호로 붙인다", () => {
    expect(formatMonthDay("2026-11-13", true)).toBe("11/13(금)");
    expect(formatMonthDay("2026-10-30", true)).toBe("10/30(금)");
    expect(formatMonthDay("2026-11-08", true)).toBe("11/8(일)");
  });
});

describe("photoChipLabel", () => {
  it("세부 분류가 있으면 세부 분류만", () => {
    expect(photoChipLabel("KEYBOARD", "KB_60")).toBe("60%");
    expect(photoChipLabel("KEYCAP", "KC_SP")).toBe("SP");
  });

  it("세부 분류가 그 외이거나 없으면 분류", () => {
    expect(photoChipLabel("KEYCAP", "KC_ETC")).toBe("키캡");
    expect(photoChipLabel("KEYBOARD", "KB_ETC")).toBe("키보드");
    expect(photoChipLabel("KEYBOARD", null)).toBe("키보드");
    expect(photoChipLabel("OTHER", null)).toBe("기타");
  });

  it("미분류면 칩을 그리지 않는다", () => {
    expect(photoChipLabel("UNCLASSIFIED", null)).toBeNull();
  });
});

describe("formatPostedDate", () => {
  it("정확한 작성일이 있으면 그 날짜로 작성", () => {
    expect(formatPostedDate("2026-10-30T15:30:00Z", null)).toEqual({ text: "2026.10.31 작성", estimated: false });
  });

  it("정확한 작성일이 있으면 추정값은 쓰지 않는다", () => {
    expect(formatPostedDate("2026-10-30T03:00:00Z", "2026-01-01T00:00:00Z")).toEqual({
      text: "2026.10.30 작성",
      estimated: false,
    });
  });

  it("추정값만 있으면 쯤을 붙인다", () => {
    expect(formatPostedDate(null, "2026-07-14T03:00:00Z")).toEqual({ text: "2026.07.14쯤 작성", estimated: true });
  });

  it("둘 다 없으면 null", () => {
    expect(formatPostedDate(null, null)).toBeNull();
  });
});

describe("formatLastPost", () => {
  const now = new Date("2026-11-02T12:00:00Z");
  const lastPostAt = "2026-10-30T12:00:00Z";

  it("댓글이 있으면 마지막 댓글을 상대 시간으로", () => {
    expect(formatLastPost(lastPostAt, 3, now)).toBe("마지막 댓글 3일 전");
  });

  it("댓글이 없으면 마지막 게시물이 원글이라 null", () => {
    expect(formatLastPost(lastPostAt, 0, now)).toBeNull();
  });

  it("목록에서 읽은 적 없으면 null", () => {
    expect(formatLastPost(null, 3, now)).toBeNull();
  });
});

describe("categoryLabel", () => {
  it.each([
    ["KEYBOARD", "키보드"],
    ["KEYCAP", "키캡"],
    ["OTHER", "기타"],
    ["UNCLASSIFIED", "미분류"],
  ] as const)("%s → %s", (category, expected) => {
    expect(categoryLabel(category)).toBe(expected);
  });
});

describe("subcategoryLabel", () => {
  it.each([
    ["KB_100", "100%"],
    ["KB_96", "96%·1800"],
    ["KB_80", "80%"],
    ["KB_75", "75%"],
    ["KB_65", "65%"],
    ["KB_60", "60%"],
    ["KB_40", "40% 이하"],
    ["KB_ERGO", "앨리스·스플릿"],
    ["KB_ETC", "그 외"],
    ["KC_GMK", "GMK"],
    ["KC_SP", "SP"],
    ["KC_MW", "MW"],
    ["KC_KKB", "KKB"],
    ["KC_PBTFANS", "PBTfans"],
    ["KC_ETC", "그 외"],
  ] as const)("%s → %s", (code, expected) => {
    expect(subcategoryLabel(code)).toBe(expected);
  });
});

describe("postCategoryLabel", () => {
  it("세부 분류가 있으면 분류 · 세부 분류", () => {
    expect(postCategoryLabel("KEYBOARD", "KB_65")).toBe("키보드 · 65%");
    expect(postCategoryLabel("KEYCAP", "KC_ETC")).toBe("키캡 · 그 외");
  });

  it("세부 분류가 없으면 분류만", () => {
    expect(postCategoryLabel("KEYBOARD", null)).toBe("키보드");
    expect(postCategoryLabel("OTHER", null)).toBe("기타");
  });
});

describe("boardLabel", () => {
  it("게시판 배지 글자. 공동구매는 한국어로", () => {
    expect(boardLabel("IC")).toBe("IC");
    expect(boardLabel("GB")).toBe("공동구매");
  });
});

describe("listTitle", () => {
  it("필터가 없으면 게시판 이름 목록", () => {
    expect(listTitle({ board: "gb", category: "all", sort: "recent" })).toBe("공동구매 목록");
    expect(listTitle({ board: "ic", category: "all", sort: "recent" })).toBe("IC 목록");
  });

  it("분류와 세부 분류를 · 로 잇는다", () => {
    expect(listTitle({ board: "gb", category: "keycap", sort: "recent" })).toBe("공동구매 · 키캡 목록");
    expect(listTitle({ board: "ic", category: "keyboard", sub: "65", sort: "recent" })).toBe("IC · 키보드 · 65% 목록");
    expect(listTitle({ board: "ic", category: "keycap", sub: "etc", sort: "recent" })).toBe("IC · 키캡 · 그 외 목록");
  });

  it("조회순이면 끝에 조회순을 붙인다", () => {
    expect(listTitle({ board: "gb", category: "all", sort: "views" })).toBe("공동구매 · 조회순 목록");
    expect(listTitle({ board: "gb", category: "keyboard", sub: "ergo", sort: "views" })).toBe("공동구매 · 키보드 · 앨리스·스플릿 · 조회순 목록");
  });
});

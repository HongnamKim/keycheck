import { describe, expect, it } from "vitest";
import { datelineCounts } from "./dateline";

/** 줄바꿈 없는 띄어쓰기(시안 &nbsp;) */
const NB = " ";

describe("datelineCounts", () => {
  it("geekhack IC · GB · 국내 판매 순이고, 첫 토막에만 최근 7일을 붙인다", () => {
    expect(datelineCounts({ newIcCount: 12, newGbCount: 5 }, 31)).toEqual([
      { label: `최근${NB}7일 geekhack IC`, count: 12 },
      { label: "GB", count: 5 },
      { label: "국내 판매", count: 31 },
    ]);
  });

  it("0도 숫자로 보여준다", () => {
    expect(datelineCounts({ newIcCount: 0, newGbCount: 0 }, 0)).toEqual([
      { label: `최근${NB}7일 geekhack IC`, count: 0 },
      { label: "GB", count: 0 },
      { label: "국내 판매", count: 0 },
    ]);
  });

  it("국내 판매를 받지 못했으면 그 토막만 빼고 0으로 채우지 않는다", () => {
    expect(datelineCounts({ newIcCount: 12, newGbCount: 5 }, undefined)).toEqual([
      { label: `최근${NB}7일 geekhack IC`, count: 12 },
      { label: "GB", count: 5 },
    ]);
  });

  it("하이라이트를 받지 못했으면 국내 판매만, 최근 7일은 국내 판매 앞에 붙인다", () => {
    expect(datelineCounts(null, 31)).toEqual([{ label: `최근${NB}7일 국내 판매`, count: 31 }]);
  });

  it("둘 다 받지 못했으면 빈 배열(날짜만 보여준다)", () => {
    expect(datelineCounts(null, undefined)).toEqual([]);
  });
});

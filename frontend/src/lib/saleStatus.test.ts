import { describe, expect, it } from "vitest";
import type { SaleCard } from "./api";
import { formatKstTime } from "./format";
import { saleBadge, saleCategoryLabel, saleKindLabel, saleWhenText, vendorLabel } from "./saleStatus";

// 11/8(일) 15:00 KST
const NOW = new Date("2026-11-08T06:00:00Z");

type Schedule = Pick<SaleCard, "phase" | "startDate" | "startTime" | "endDate" | "endTime" | "postedAt">;

function sale(overrides: Partial<Schedule>): Schedule {
  return {
    phase: "OPEN",
    startDate: null,
    startTime: null,
    endDate: null,
    endTime: null,
    postedAt: "2026-11-06T10:30:00Z",
    ...overrides,
  };
}

describe("saleBadge", () => {
  it.each([
    ["시작 시각이 5시간 뒤", { phase: "ANNOUNCE", startDate: "2026-11-08", startTime: "20:30" }, "5시간 뒤 시작", "fill"],
    ["시작 시각이 40분 뒤", { phase: "ANNOUNCE", startDate: "2026-11-08", startTime: "15:40" }, "40분 뒤 시작", "fill"],
    ["시작이 25시간 뒤", { phase: "ANNOUNCE", startDate: "2026-11-09", startTime: "16:00" }, "D-1 시작", "soft"],
    ["시작이 4일 뒤", { phase: "ANNOUNCE", startDate: "2026-11-12", startTime: "21:00" }, "D-4 시작", "soft"],
    ["시작 날짜만, 오늘", { phase: "ANNOUNCE", startDate: "2026-11-08" }, "오늘 시작", "fill"],
    ["시작 날짜만, 4일 뒤", { phase: "ANNOUNCE", startDate: "2026-11-12" }, "D-4 시작", "soft"],
    ["판매 중, 마감 5시간 뒤", { startDate: "2026-11-06", startTime: "19:30", endDate: "2026-11-08", endTime: "20:00" }, "5시간 뒤 마감", "fill"],
    ["판매 중, 마감 41시간 뒤", { startDate: "2026-11-06", startTime: "19:30", endDate: "2026-11-10", endTime: "08:00" }, "D-2 마감", "fill"],
    ["판매 중, 마감이 멀다", { startDate: "2026-11-06", startTime: "19:30", endDate: "2026-11-12", endTime: "23:59" }, "판매 중", "line"],
    ["마감 날짜만, 오늘", { startDate: "2026-11-06", endDate: "2026-11-08" }, "오늘 마감", "fill"],
    ["마감 날짜만, 내일", { startDate: "2026-11-06", endDate: "2026-11-09" }, "D-1 마감", "fill"],
    ["마감 날짜만, 어제", { startDate: "2026-11-06", endDate: "2026-11-07" }, "판매 종료", "soft"],
    ["마감 시각이 지남", { startDate: "2026-11-06", endDate: "2026-11-08", endTime: "14:00" }, "판매 종료", "soft"],
    ["시작·마감 없는 OPEN, 공지 3일 전", { postedAt: "2026-11-05T06:00:00Z" }, "판매 중", "line"],
    ["시작 없는 예고", { phase: "ANNOUNCE" }, "일정 미정", "dash"],
  ] as [string, Partial<Schedule>, string, string][])("%s -> %s", (_, overrides, label, tone) => {
    expect(saleBadge(sale(overrides), NOW)).toEqual({ label, tone });
  });

  it("시작·마감 없는 OPEN은 공지 8일 뒤부터 배지가 없다", () => {
    expect(saleBadge(sale({ postedAt: "2026-10-31T06:00:00Z" }), NOW)).toBeNull();
  });
});

describe("saleKindLabel", () => {
  it.each([
    ["RESTOCK", "ANNOUNCE", "재입고 예정"],
    ["SALE", "OPEN", "판매 시작"],
    ["PREORDER", "CHANGE", "예약판매 일정 변경"],
    ["RAFFLE", "OPEN", "래플"],
    ["DROP", "OPEN", "드랍"],
  ] as const)("%s + %s -> %s", (type, phase, label) => {
    expect(saleKindLabel(type, phase)).toBe(label);
  });
});

describe("saleCategoryLabel", () => {
  it.each([
    ["KEYBOARD", "키보드"],
    ["KEYCAP", "키캡"],
    ["SWITCH", "스위치"],
    ["PARTS", "기타"],
  ] as const)("%s -> %s", (category, label) => {
    expect(saleCategoryLabel(category)).toBe(label);
  });
});

describe("saleWhenText", () => {
  it.each([
    ["미래 시작 시각", { phase: "ANNOUNCE", startDate: "2026-11-08", startTime: "20:30" }, "판매 시작 11/8(일) 20:30"],
    ["미래 시작 날짜만", { phase: "ANNOUNCE", startDate: "2026-11-12" }, "판매 시작 11/12(목)"],
    ["판매 중, 마감 있음", { startDate: "2026-11-06", endDate: "2026-11-10", endTime: "23:59" }, "11/10(화) 23:59까지"],
    ["판매 중, 마감 없음", {}, "11/6(금) 19:30부터"],
    ["끝남", { startDate: "2026-11-05", endDate: "2026-11-07" }, "11/7(토) 마감"],
    ["일정 없음", { phase: "ANNOUNCE" }, "판매 일정 미정"],
  ] as [string, Partial<Schedule>, string][])("%s", (_, overrides, text) => {
    expect(saleWhenText(sale(overrides), NOW)).toBe(text);
  });
});

describe("vendorLabel", () => {
  it("판매처 표시 이름", () => {
    expect(vendorLabel("SWAGKEYS")).toBe("Swagkeys");
    expect(vendorLabel("GEONWORKS")).toBe("GeonWorks");
  });
});

describe("formatKstTime", () => {
  it("한국 시각 HH:mm", () => {
    expect(formatKstTime("2026-11-06T09:52:00Z")).toBe("18:52");
  });
});

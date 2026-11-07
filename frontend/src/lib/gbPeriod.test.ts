import { describe, expect, it } from "vitest";
import { gbDaysLeft, gbPeriodChip } from "./gbPeriod";

describe("gbDaysLeft", () => {
  it("마감일까지 남은 날 수", () => {
    expect(gbDaysLeft("2026-11-13", "2026-11-05")).toBe(8);
    expect(gbDaysLeft("2026-12-01", "2026-11-30")).toBe(1);
  });

  it("마감 당일은 0", () => {
    expect(gbDaysLeft("2026-11-13", "2026-11-13")).toBe(0);
  });

  it("마감이 지났거나 마감일을 모르면 null", () => {
    expect(gbDaysLeft("2026-11-13", "2026-11-14")).toBeNull();
    expect(gbDaysLeft(null, "2026-11-05")).toBeNull();
  });
});

describe("gbPeriodChip", () => {
  it("마감 전이면 기간과 D-n", () => {
    expect(gbPeriodChip("2026-10-30", "2026-11-13", "2026-11-05")).toEqual({
      range: "10/30 → 11/13",
      dday: "D-8",
      ended: false,
    });
  });

  it("마감 당일은 D-DAY", () => {
    expect(gbPeriodChip("2026-10-30", "2026-11-13", "2026-11-13")).toEqual({
      range: "10/30 → 11/13",
      dday: "D-DAY",
      ended: false,
    });
  });

  it("마감이 지났으면 기간 끝에 끝남, D-day 없음", () => {
    expect(gbPeriodChip("2026-10-30", "2026-11-13", "2026-11-14")).toEqual({
      range: "10/30 → 11/13 끝남",
      dday: null,
      ended: true,
    });
  });

  it("시작일을 모르면 마감일만", () => {
    expect(gbPeriodChip(null, "2026-11-13", "2026-11-05")).toEqual({ range: "~ 11/13", dday: "D-8", ended: false });
    expect(gbPeriodChip(null, "2026-11-13", "2026-11-14")).toEqual({ range: "~ 11/13 끝남", dday: null, ended: true });
  });

  it("마감일을 모르면 칩을 그리지 않는다", () => {
    expect(gbPeriodChip("2026-10-30", null, "2026-11-05")).toBeNull();
    expect(gbPeriodChip(null, null, "2026-11-05")).toBeNull();
  });

  it("withWeekday면 날짜마다 요일을 붙인다", () => {
    expect(gbPeriodChip("2026-10-30", "2026-11-13", "2026-11-05", true)).toEqual({
      range: "10/30(금) → 11/13(금)",
      dday: "D-8",
      ended: false,
    });
    expect(gbPeriodChip(null, "2026-11-13", "2026-11-05", true)?.range).toBe("~ 11/13(금)");
    expect(gbPeriodChip("2026-10-30", "2026-11-13", "2026-11-14", true)).toEqual({
      range: "10/30(금) → 11/13(금) 끝남",
      dday: null,
      ended: true,
    });
  });
});

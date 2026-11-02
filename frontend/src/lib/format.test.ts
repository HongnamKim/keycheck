import { describe, expect, it } from "vitest";
import { formatCheckedAt, formatViews } from "./format";

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

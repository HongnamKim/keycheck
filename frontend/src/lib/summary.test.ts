import { describe, expect, it } from "vitest";
import { SUMMARY_PROGRESS_CAP, SUMMARY_WAIT_LIMIT_MS, summaryProgress, summaryWatchState } from "./summary";

describe("summaryProgress", () => {
  it("처음에는 0에서 시작한다", () => {
    expect(summaryProgress(0)).toBe(0);
    expect(summaryProgress(-500)).toBe(0);
  });

  it("처음에 빨리 차고 점점 느려져 15초쯤 90% 근처에 닿는다", () => {
    expect(summaryProgress(5_000)).toBeCloseTo(0.569, 2);
    expect(summaryProgress(10_000)).toBeCloseTo(0.778, 2);
    expect(summaryProgress(15_000)).toBeGreaterThan(0.85);
    expect(summaryProgress(15_000)).toBeLessThan(SUMMARY_PROGRESS_CAP);
  });

  it("요약이 오기 전에는 90%를 넘지 않는다", () => {
    expect(SUMMARY_PROGRESS_CAP).toBe(0.9);
    expect(summaryProgress(60_000)).toBeLessThanOrEqual(SUMMARY_PROGRESS_CAP);
    expect(summaryProgress(60 * 60_000)).toBeLessThanOrEqual(SUMMARY_PROGRESS_CAP);
  });

  it("시간이 갈수록 줄지 않는다", () => {
    let last = 0;
    for (let ms = 0; ms <= 60_000; ms += 250) {
      const value = summaryProgress(ms);
      expect(value).toBeGreaterThanOrEqual(last);
      last = value;
    }
  });
});

describe("summaryWatchState", () => {
  it("1분 안에 PENDING이면 계속 지켜본다", () => {
    expect(summaryWatchState("PENDING", 0)).toBe("watching");
    expect(summaryWatchState("PENDING", 3_000)).toBe("watching");
    expect(summaryWatchState("PENDING", SUMMARY_WAIT_LIMIT_MS - 1)).toBe("watching");
  });

  it("처음 연 때부터 1분이 지나도 PENDING이면 늦어짐", () => {
    expect(SUMMARY_WAIT_LIMIT_MS).toBe(60_000);
    expect(summaryWatchState("PENDING", SUMMARY_WAIT_LIMIT_MS)).toBe("late");
    expect(summaryWatchState("PENDING", 5 * 60_000)).toBe("late");
  });

  it("DONE이면 경과 시간과 상관없이 done", () => {
    expect(summaryWatchState("DONE", 3_000)).toBe("done");
    expect(summaryWatchState("DONE", SUMMARY_WAIT_LIMIT_MS + 1_000)).toBe("done");
  });

  it("FAILED면 경과 시간과 상관없이 failed", () => {
    expect(summaryWatchState("FAILED", 3_000)).toBe("failed");
    expect(summaryWatchState("FAILED", SUMMARY_WAIT_LIMIT_MS + 1_000)).toBe("failed");
  });
});

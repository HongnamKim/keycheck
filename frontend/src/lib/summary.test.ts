import { describe, expect, it } from "vitest";
import { SUMMARY_WAIT_LIMIT_MS, summaryWatchState } from "./summary";

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

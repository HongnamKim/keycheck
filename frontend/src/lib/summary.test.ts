import { describe, expect, it } from "vitest";
import {
  SUMMARY_PROGRESS_CAP,
  SUMMARY_QUEUE_LIMIT_MS,
  SUMMARY_RUN_LIMIT_MS,
  SUMMARY_WAIT_LIMIT_MS,
  summaryBarProgress,
  summaryMessage,
  summaryPhase,
  summaryProgress,
  summaryRunProgress,
  summaryWatchState,
  summaryWatchStateFor,
} from "./summary";

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

describe("summaryPhase", () => {
  it("응답에 summarizingForMs 키가 없으면(옛 백엔드) legacy", () => {
    expect(summaryPhase(undefined)).toBe("legacy");
  });

  it("null이면 차례를 기다리는 중", () => {
    expect(summaryPhase(null)).toBe("queued");
  });

  it("숫자면 요약하는 중", () => {
    expect(summaryPhase(0)).toBe("running");
    expect(summaryPhase(1234)).toBe("running");
  });
});

describe("summaryRunProgress", () => {
  it("요약을 막 시작했으면 0", () => {
    expect(summaryRunProgress(0)).toBe(0);
    expect(summaryRunProgress(-500)).toBe(0);
  });

  it("τ 10초로 차오른다", () => {
    expect(summaryRunProgress(10_000)).toBeCloseTo(0.569, 2);
  });

  it("오래 걸려도 90%를 넘지 않고, 시간이 갈수록 줄지 않는다", () => {
    expect(summaryRunProgress(60 * 60_000)).toBeLessThanOrEqual(SUMMARY_PROGRESS_CAP);
    let last = 0;
    for (let ms = 0; ms <= 120_000; ms += 500) {
      const value = summaryRunProgress(ms);
      expect(value).toBeGreaterThanOrEqual(last);
      last = value;
    }
  });
});

describe("summaryWatchStateFor", () => {
  it("기다리는 중이면 연 뒤 3분까지 지켜보고, 3분이 되면 늦어짐", () => {
    expect(SUMMARY_QUEUE_LIMIT_MS).toBe(180_000);
    expect(summaryWatchStateFor("PENDING", "queued", 179_999, 0)).toBe("watching");
    expect(summaryWatchStateFor("PENDING", "queued", 180_000, 0)).toBe("late");
  });

  it("요약하는 중이면 연 지 오래됐어도 늦어지지 않는다", () => {
    expect(summaryWatchStateFor("PENDING", "running", 600_000, 200_000)).toBe("watching");
  });

  it("서버 기준 요약 시간이 7분이 되면 늦어짐", () => {
    expect(SUMMARY_RUN_LIMIT_MS).toBe(420_000);
    expect(summaryWatchStateFor("PENDING", "running", 0, 420_000)).toBe("late");
  });

  it("옛 백엔드면 지금처럼 연 뒤 1분이 되면 늦어짐", () => {
    expect(summaryWatchStateFor("PENDING", "legacy", 60_000, 0)).toBe("late");
    expect(summaryWatchStateFor("PENDING", "legacy", 59_999, 0)).toBe("watching");
  });

  it("DONE·FAILED는 단계와 시간보다 앞선다", () => {
    expect(summaryWatchStateFor("DONE", "queued", 999_999, 0)).toBe("done");
    expect(summaryWatchStateFor("FAILED", "running", 0, 0)).toBe("failed");
  });
});

describe("summaryBarProgress", () => {
  it("첫 응답 전과 기다리는 중에는 막대를 채우지 않는다", () => {
    expect(summaryBarProgress("queued", 0, 0)).toBe(0);
    expect(summaryBarProgress("queued", 60_000, 60_000)).toBe(0);
  });

  it("요약하는 중이면 화면을 연 시간이 아니라 서버 기준 요약 시간으로 채운다", () => {
    expect(summaryBarProgress("running", 0, 10_000)).toBe(summaryRunProgress(10_000));
    expect(summaryBarProgress("running", 60_000, 0)).toBe(0);
  });

  it("옛 백엔드면 지금처럼 화면을 연 뒤 흐른 시간으로 채운다", () => {
    expect(summaryBarProgress("legacy", 5_000, 0)).toBe(summaryProgress(5_000));
    expect(summaryBarProgress("legacy", 5_000, 0)).toBeCloseTo(0.569, 2);
  });
});

describe("summaryMessage", () => {
  it("첫 응답 전과 기다리는 중에는 차례를 기다린다고 알린다", () => {
    expect(summaryMessage("watching", "queued")).toBe("요약 차례를 기다리고 있어요");
  });

  it("요약하는 중과 옛 백엔드는 요약하는 중이라고 알린다", () => {
    expect(summaryMessage("watching", "running")).toBe("요약하는 중이에요");
    expect(summaryMessage("watching", "legacy")).toBe("요약하는 중이에요");
  });

  it("멈춘 뒤에는 단계와 상관없이 늦어짐·실패 문구다", () => {
    const late = "요약이 늦어지고 있어요. 잠시 뒤 새로고침해 주세요";
    expect(summaryMessage("late", "queued")).toBe(late);
    expect(summaryMessage("late", "running")).toBe(late);
    expect(summaryMessage("late", "legacy")).toBe(late);
    expect(summaryMessage("failed", "queued")).toBe("요약을 만들지 못했어요");
  });

  it("요약이 와서 다시 그리는 동안은 꽉 찬 막대와 같은 문구를 둔다", () => {
    expect(summaryMessage("done", "queued")).toBe("요약하는 중이에요");
    expect(summaryMessage("done", "running")).toBe("요약하는 중이에요");
  });
});

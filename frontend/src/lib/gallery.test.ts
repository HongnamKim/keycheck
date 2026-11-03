import { describe, expect, it } from "vitest";
import { stepIndex, swipeStep } from "./gallery";

describe("stepIndex", () => {
  it("다음·이전으로 한 칸씩 움직인다", () => {
    expect(stepIndex(0, 1, 5)).toBe(1);
    expect(stepIndex(3, -1, 5)).toBe(2);
  });

  it("끝에서 다음은 처음으로, 처음에서 이전은 끝으로 이어진다", () => {
    expect(stepIndex(4, 1, 5)).toBe(0);
    expect(stepIndex(0, -1, 5)).toBe(4);
  });

  it("이미지가 한 장이면 그대로", () => {
    expect(stepIndex(0, 1, 1)).toBe(0);
    expect(stepIndex(0, -1, 1)).toBe(0);
  });
});

describe("swipeStep", () => {
  it("왼쪽으로 밀면 다음(+1), 오른쪽으로 밀면 이전(-1)", () => {
    expect(swipeStep(-80, 10)).toBe(1);
    expect(swipeStep(80, -10)).toBe(-1);
  });

  it("가로 이동이 50px 이상이어야 넘긴다", () => {
    expect(swipeStep(-50, 0)).toBe(1);
    expect(swipeStep(-49, 0)).toBe(0);
    expect(swipeStep(49, 0)).toBe(0);
  });

  it("세로 이동이 가로 이동 이상이면 넘기지 않는다", () => {
    expect(swipeStep(-60, 60)).toBe(0);
    expect(swipeStep(70, -120)).toBe(0);
  });

  it("거의 움직이지 않은 탭은 0", () => {
    expect(swipeStep(0, 0)).toBe(0);
  });
});

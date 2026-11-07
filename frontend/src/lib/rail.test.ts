import { describe, expect, it } from "vitest";
import { railEdges, railProgress } from "./rail";

describe("railProgress", () => {
  it("보이는 폭의 비율이 막대 길이, 스크롤 위치의 비율이 막대 시작", () => {
    expect(railProgress(0, 1000, 250)).toEqual({ start: 0, size: 25 });
    expect(railProgress(375, 1000, 250)).toEqual({ start: 37.5, size: 25 });
    expect(railProgress(750, 1000, 250)).toEqual({ start: 75, size: 25 });
  });

  it("넘칠 것이 없으면 막대가 꽉 찬다", () => {
    expect(railProgress(0, 250, 250)).toEqual({ start: 0, size: 100 });
    expect(railProgress(0, 0, 0)).toEqual({ start: 0, size: 100 });
  });

  it("끝을 넘겨 튕기는 동안에도 막대는 줄 안에 머문다", () => {
    expect(railProgress(-20, 1000, 250)).toEqual({ start: 0, size: 25 });
    expect(railProgress(800, 1000, 250)).toEqual({ start: 75, size: 25 });
  });
});

describe("railEdges", () => {
  it("처음이면 atStart, 끝이면 atEnd", () => {
    expect(railEdges(0, 1000, 250)).toEqual({ atStart: true, atEnd: false });
    expect(railEdges(750, 1000, 250)).toEqual({ atStart: false, atEnd: true });
    expect(railEdges(300, 1000, 250)).toEqual({ atStart: false, atEnd: false });
  });

  it("소수점 스크롤 위치를 위해 1px 여유를 둔다", () => {
    expect(railEdges(1, 1000, 250).atStart).toBe(true);
    expect(railEdges(749, 1000, 250).atEnd).toBe(true);
    expect(railEdges(748, 1000, 250).atEnd).toBe(false);
  });

  it("넘칠 것이 없으면 처음이자 끝", () => {
    expect(railEdges(0, 250, 250)).toEqual({ atStart: true, atEnd: true });
  });
});

import { describe, expect, it } from "vitest";
import robots from "../app/robots";

describe("robots", () => {
  const result = robots();
  const rules = [result.rules].flat();

  it("모든 크롤러를 허용한다", () => {
    expect(rules).toContainEqual({ userAgent: "*", allow: "/" });
  });

  it("AI 학습 크롤러는 전체를 막는다", () => {
    const blocked = rules.filter((rule) => rule.disallow === "/").flatMap((rule) => [rule.userAgent].flat());

    expect(blocked).toEqual(
      expect.arrayContaining([
        "GPTBot",
        "ClaudeBot",
        "anthropic-ai",
        "CCBot",
        "Google-Extended",
        "PerplexityBot",
        "Bytespider",
      ]),
    );
  });

  it("사이트맵은 넣지 않는다", () => {
    expect(result.sitemap).toBeUndefined();
  });
});

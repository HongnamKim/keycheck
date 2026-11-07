import { describe, expect, it } from "vitest";
import robots from "../app/robots";
import { SITE_URL } from "./seo";

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

  it("사이트맵 주소를 절대 주소로 알린다", () => {
    expect(result.sitemap).toBe(`${SITE_URL}/sitemap.xml`);
  });
});

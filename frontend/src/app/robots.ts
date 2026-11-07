import type { MetadataRoute } from "next";

/** AI 학습용 크롤러. 요약은 원문을 정리한 글이라 학습 데이터로 가져가지 못하게 막는다 */
const AI_CRAWLERS = ["GPTBot", "ClaudeBot", "anthropic-ai", "CCBot", "Google-Extended", "PerplexityBot", "Bytespider"];

/** 검색 엔진 등 모든 크롤러를 허용하고 AI 학습 크롤러만 전체를 막는다. 사이트맵은 아직 없다 */
export default function robots(): MetadataRoute.Robots {
  return {
    rules: [
      { userAgent: "*", allow: "/" },
      { userAgent: AI_CRAWLERS, disallow: "/" },
    ],
  };
}

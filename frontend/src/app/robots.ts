import type { MetadataRoute } from "next";
// vitest가 @ 별칭을 몰라 상대 경로로 둔다(robots.test.ts가 이 파일을 직접 부른다)
import { SITE_URL } from "../lib/seo";

/** AI 학습용 크롤러. 요약은 원문을 정리한 글이라 학습 데이터로 가져가지 못하게 막는다 */
const AI_CRAWLERS = ["GPTBot", "ClaudeBot", "anthropic-ai", "CCBot", "Google-Extended", "PerplexityBot", "Bytespider"];

/** 검색 엔진 등 모든 크롤러를 허용하고 AI 학습 크롤러만 전체를 막는다. 사이트맵 주소를 알린다 */
export default function robots(): MetadataRoute.Robots {
  return {
    rules: [
      { userAgent: "*", allow: "/" },
      { userAgent: AI_CRAWLERS, disallow: "/" },
    ],
    sitemap: `${SITE_URL}/sitemap.xml`,
  };
}

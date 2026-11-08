import type { MetadataRoute } from "next";
import { connection } from "next/server";
import { getSaleSitemap, getSitemapPosts } from "@/lib/api";
import { sitemapEntries } from "@/lib/seo";

/**
 * 사이트맵(/sitemap.xml). 요청 때 백엔드에서 요약이 끝난 글 목록을 받아 만든다(빌드가 백엔드에 기대지 않는다).
 * 글 목록이 실패하면 예외를 그대로 던져 500으로 끝낸다. 검색 엔진이 나중에 다시 가져간다.
 * 국내 판매 목록은 실패해도 사이트맵을 깨지 않는다(프론트가 백엔드보다 먼저 배포되는 동안 엔드포인트가 아직 없을 수 있다).
 */
export default async function sitemap(): Promise<MetadataRoute.Sitemap> {
  await connection();
  const [posts, sales] = await Promise.all([
    getSitemapPosts(),
    getSaleSitemap().catch((error: unknown) => {
      console.error("국내 판매 사이트맵을 받지 못해 건너뜀", error);
      return [];
    }),
  ]);
  return sitemapEntries(posts, sales);
}

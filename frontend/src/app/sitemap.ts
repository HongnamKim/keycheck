import type { MetadataRoute } from "next";
import { connection } from "next/server";
import { getSitemapPostIds } from "@/lib/api";
import { sitemapEntries } from "@/lib/seo";

/**
 * 사이트맵(/sitemap.xml). 요청 때 백엔드에서 요약이 끝난 글 목록을 받아 만든다(빌드가 백엔드에 기대지 않는다).
 * 백엔드가 실패하면 예외를 그대로 던져 500으로 끝낸다. 검색 엔진이 나중에 다시 가져간다.
 */
export default async function sitemap(): Promise<MetadataRoute.Sitemap> {
  await connection();
  return sitemapEntries(await getSitemapPostIds());
}

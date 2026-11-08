/** RSS 2.0 피드(/rss.xml) 본문. 네이버 서치어드바이저에 제출한다. 순수 함수만 둔다 */

import type { FeedPost } from "./api";
import { postTitle, SITE_DESCRIPTION, SITE_URL } from "./seo";

/** XML 1.0에 넣을 수 없는 문자(탭·줄바꿈을 뺀 제어문자 등). 원문에서 온 글자에 섞여 있으면 피드 전체가 깨진다 */
const INVALID_XML_CHARS = /[\u0000-\u0008\u000B\u000C\u000E-\u001F\uFFFE\uFFFF]/g;

/** 넣을 수 없는 문자를 지우고 & < > " '를 바꾼다 */
export function escapeXml(text: string): string {
  return text
    .replace(INVALID_XML_CHARS, "")
    .replace(/&/g, "&amp;")
    .replace(/</g, "&lt;")
    .replace(/>/g, "&gt;")
    .replace(/"/g, "&quot;")
    .replace(/'/g, "&apos;");
}

/** RFC 822 날짜(예: Sun, 08 Nov 2026 03:00:00 GMT). 읽을 수 없는 날짜면 null(그대로 넣으면 "Invalid Date"로 피드가 깨진다) */
function rfc822(date: Date): string | null {
  return Number.isNaN(date.getTime()) ? null : date.toUTCString();
}

function itemXml(post: FeedPost): string {
  const url = `${SITE_URL}/posts/${post.id}`;
  // 요약을 마친 시각을 알면 그 시각, 모르면 원문 작성 시각. 둘 다 없으면 넣지 않는다
  const publishedAt = rfc822(new Date(post.summarizedAt ?? post.postedAt ?? Number.NaN));
  return [
    "<item>",
    `<title>${escapeXml(postTitle(post))}</title>`,
    `<link>${escapeXml(url)}</link>`,
    `<guid isPermaLink="true">${escapeXml(url)}</guid>`,
    `<description>${escapeXml(post.summaryLines.join("\n"))}</description>`,
    ...(publishedAt ? [`<pubDate>${publishedAt}</pubDate>`] : []),
    "</item>",
  ].join("\n");
}

/** RSS 2.0 문서. 글 주소는 canonical 절대 주소, 설명은 한국어 요약 전체다. builtAt은 lastBuildDate가 된다 */
export function rssXml(posts: FeedPost[], builtAt: Date): string {
  return [
    '<?xml version="1.0" encoding="UTF-8"?>',
    '<rss version="2.0" xmlns:atom="http://www.w3.org/2005/Atom">',
    "<channel>",
    "<title>키체크</title>",
    `<link>${escapeXml(SITE_URL)}</link>`,
    // 피드 자신의 주소. RSS 2.0 필수는 아니지만 검사기가 권한다
    `<atom:link href="${escapeXml(`${SITE_URL}/rss.xml`)}" rel="self" type="application/rss+xml"/>`,
    `<description>${escapeXml(SITE_DESCRIPTION)}</description>`,
    "<language>ko</language>",
    ...(rfc822(builtAt) ? [`<lastBuildDate>${rfc822(builtAt)}</lastBuildDate>`] : []),
    ...posts.map(itemXml),
    "</channel>",
    "</rss>",
    "",
  ].join("\n");
}

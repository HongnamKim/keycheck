import { describe, expect, it } from "vitest";
import type { FeedPost } from "./api";
import { escapeXml, rssXml } from "./rss";
import { SITE_DESCRIPTION, SITE_URL } from "./seo";

const BUILT_AT = new Date("2026-11-08T03:00:00Z");

function post(overrides: Partial<FeedPost> = {}): FeedPost {
  return {
    id: 5,
    board: "IC",
    title: "[IC] GMK Knight | 2026",
    productName: "GMK Knight",
    category: "KEYCAP",
    summaryLines: ["어두운 기사 테마 키캡이에요", "베이스 키트와 노벨티가 있어요"],
    postedAt: "2026-11-01T00:00:00Z",
    summarizedAt: "2026-11-07T12:30:00Z",
    ...overrides,
  };
}

/** 문서에서 첫 item 블록만 */
function firstItem(xml: string): string {
  return xml.slice(xml.indexOf("<item>"), xml.indexOf("</item>") + "</item>".length);
}

describe("escapeXml", () => {
  it("& < > \" '를 엔터티로 바꾼다", () => {
    expect(escapeXml(`A & B <C> "D" 'E'`)).toBe("A &amp; B &lt;C&gt; &quot;D&quot; &apos;E&apos;");
  });

  it("XML에 넣을 수 없는 제어문자는 지우고 탭·줄바꿈은 둔다", () => {
    expect(escapeXml("a\u0000b\u0008c\u000Bd\u001Fe\uFFFE\uFFFF")).toBe("abcde");
    expect(escapeXml("a\tb\nc\rd")).toBe("a\tb\nc\rd");
  });
});

describe("rssXml", () => {
  it("RSS 2.0 채널에 사이트 이름·주소·설명·언어·만든 시각을 둔다", () => {
    const xml = rssXml([], BUILT_AT);

    expect(xml.startsWith('<?xml version="1.0" encoding="UTF-8"?>\n<rss version="2.0" xmlns:atom="http://www.w3.org/2005/Atom">')).toBe(
      true,
    );
    expect(xml).toContain(`<atom:link href="${SITE_URL}/rss.xml" rel="self" type="application/rss+xml"/>`);
    expect(xml).toContain("<title>키체크</title>");
    expect(xml).toContain(`<link>${SITE_URL}</link>`);
    expect(xml).toContain(`<description>${SITE_DESCRIPTION}</description>`);
    expect(xml).toContain("<language>ko</language>");
    expect(xml).toContain("<lastBuildDate>Sun, 08 Nov 2026 03:00:00 GMT</lastBuildDate>");
    expect(xml).not.toContain("<item>");
  });

  it("글 제목은 글 상세 제목과 같고, 주소와 guid는 canonical 절대 주소다", () => {
    const item = firstItem(rssXml([post()], BUILT_AT));

    expect(item).toContain("<title>GMK Knight 키캡 IC</title>");
    expect(item).toContain(`<link>${SITE_URL}/posts/5</link>`);
    expect(item).toContain(`<guid isPermaLink="true">${SITE_URL}/posts/5</guid>`);
  });

  it("설명은 요약 줄 전체를 줄바꿈으로 잇는다", () => {
    const item = firstItem(rssXml([post()], BUILT_AT));

    expect(item).toContain("<description>어두운 기사 테마 키캡이에요\n베이스 키트와 노벨티가 있어요</description>");
  });

  it("pubDate는 요약을 마친 시각, 모르면 작성 시각이고, 둘 다 없으면 넣지 않는다", () => {
    expect(firstItem(rssXml([post()], BUILT_AT))).toContain("<pubDate>Sat, 07 Nov 2026 12:30:00 GMT</pubDate>");
    expect(firstItem(rssXml([post({ summarizedAt: null })], BUILT_AT))).toContain(
      "<pubDate>Sun, 01 Nov 2026 00:00:00 GMT</pubDate>",
    );
    expect(firstItem(rssXml([post({ summarizedAt: null, postedAt: null })], BUILT_AT))).not.toContain("<pubDate>");
    // 읽을 수 없는 날짜는 "Invalid Date"로 넣지 않고 뺀다
    expect(firstItem(rssXml([post({ summarizedAt: "not-a-date" })], BUILT_AT))).not.toContain("<pubDate>");
  });

  it("제목과 요약의 특수문자는 이스케이프하고 제어문자는 지운다", () => {
    const item = firstItem(
      rssXml([post({ productName: "Tom & Jerry <60%>", summaryLines: ["\"따옴표\"와 '작은따옴표'\u0007"] })], BUILT_AT),
    );

    expect(item).toContain("<title>Tom &amp; Jerry &lt;60%&gt; 키캡 IC</title>");
    expect(item).toContain("<description>&quot;따옴표&quot;와 &apos;작은따옴표&apos;</description>");
  });

  it("글은 받은 순서대로 담는다", () => {
    const xml = rssXml([post({ id: 30 }), post({ id: 12 })], BUILT_AT);

    expect(xml.indexOf("/posts/30")).toBeLessThan(xml.indexOf("/posts/12"));
  });
});

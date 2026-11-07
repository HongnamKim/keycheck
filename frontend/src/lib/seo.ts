/** 검색 노출·링크 미리보기용 메타데이터(설계 3.3). 순수 함수만 둔다 */

import type { Metadata, MetadataRoute } from "next";
import type { PostDetail } from "./api";
import { boardLabel, displayTitle } from "./format";

/** 사이트 주소. 메타데이터의 상대 주소(OG 이미지·canonical) 기준이고, 사이트맵·robots의 절대 주소에 쓴다. 운영 주소가 기본이다 */
export const SITE_URL = process.env.NEXT_PUBLIC_SITE_URL ?? "https://keycheck.kr";

/** 글 설명(요약 첫 줄)의 최대 글자 수. 넘으면 자르고 "…"를 붙인다 */
const DESCRIPTION_MAX = 120;

/** 미리보기 카드 사진. Cloudflare 이미지 변환으로 카드 크기에 맞춘 JPEG를 받는다(카드 도구와 카카오가 WebP를 못 읽는다) */
const OG_PHOTO_TRANSFORM = "/cdn-cgi/image/width=1200,height=630,fit=cover,format=jpeg";

type OgPost = Pick<PostDetail, "board" | "title" | "productName" | "representativeLargeUrl" | "representativeThumbUrl">;

function truncate(text: string, max: number): string {
  // 글자 단위로 자른다(이모지 등 두 칸짜리 문자를 반으로 가르지 않게)
  const chars = Array.from(text);
  return chars.length > max ? `${chars.slice(0, max).join("").trimEnd()}…` : text;
}

/**
 * 카드에 깔 사진 주소. 대표 이미지의 큰 축소본(없으면 작은 축소본)을 같은 도메인의 변환 주소로 바꾼다.
 * 축소본이 없으면 null이고, 원본(남의 서버)은 쓰지 않는다.
 */
export function ogPhotoUrl(post: OgPost): string | null {
  const source = post.representativeLargeUrl || post.representativeThumbUrl;
  if (!source || !URL.canParse(source)) return null;
  const url = new URL(source);
  return `${url.origin}${OG_PHOTO_TRANSFORM}${url.pathname}`;
}

/**
 * 카드 도구(satori)가 그릴 수 있는 사진 형식이면 그 형식, 아니면 null. WebP는 빈 화면으로 그려지고, 이미지 변환이 꺼져 있거나
 * 한도를 넘으면 오류 페이지나 원래 WebP가 온다.
 */
export function cardPhotoType(contentType: string | null): "image/jpeg" | "image/png" | null {
  const type = contentType?.split(";")[0].trim().toLowerCase();
  return type === "image/jpeg" || type === "image/png" ? type : null;
}

/** 오래 캐시해도 되는 카드인지. 넣으려던 사진과 사이트 글꼴을 모두 받아 끝까지 그린 카드만 그렇다 */
export function isCompleteCard(photoWanted: boolean, photoLoaded: boolean, fontsLoaded: boolean): boolean {
  return fontsLoaded && (!photoWanted || photoLoaded);
}

/**
 * 카드 주소에 붙이는 짧은 버전. 사진 주소, 이름, 게시판이 바뀌면 바뀐다.
 * 카카오 등은 이미지 주소로 캐시하므로 내용이 바뀌면 주소를 바꿔 새로 가져가게 한다.
 */
export function ogCardVersion(post: OgPost): string {
  const input = [ogPhotoUrl(post) ?? "", displayTitle(post.productName, post.title), post.board].join("\n");
  // FNV-1a 32비트. 보안용이 아니라 바뀜만 알면 된다
  let hash = 0x811c9dc5;
  for (let i = 0; i < input.length; i++) {
    hash ^= input.charCodeAt(i);
    hash = Math.imul(hash, 0x01000193);
  }
  return (hash >>> 0).toString(36);
}

/** 글 상세의 메타데이터. 원문을 읽었고 요약이 끝난 글만 검색에 넣는다 */
export function postMetadata(post: PostDetail): Metadata {
  const name = displayTitle(post.productName, post.title);
  const board = boardLabel(post.board);
  const title = `${name} ${board}`;
  const hasSummary = post.summaryStatus === "DONE" && post.summaryLines.length > 0;
  const description = hasSummary
    ? truncate(post.summaryLines[0], DESCRIPTION_MAX)
    : `geekhack ${board} 글 "${name}"의 한국어 요약과 원문 링크를 키체크에서 확인해 보세요`;
  const path = `/posts/${post.id}`;

  return {
    title,
    description,
    // 목록 조건 쿼리(?board= 등)가 붙은 주소도 모두 이 주소로 모은다
    alternates: { canonical: path },
    ...(post.detailLoaded && hasSummary ? {} : { robots: { index: false, follow: true } }),
    // 레이아웃의 openGraph를 통째로 바꾸므로 사이트 이름·언어도 다시 둔다. twitter 카드는 이 값에서 채워진다
    openGraph: {
      type: "article",
      url: path,
      siteName: "키체크",
      locale: "ko_KR",
      // 사이트 이름과 겹치지 않게 " · 키체크" 없이
      title,
      description,
      publishedTime: post.postedAt ?? undefined,
      images: [{ url: `${path}/og.png?v=${ogCardVersion(post)}`, width: 1200, height: 630, alt: name }],
    },
  };
}

/** 사이트맵 항목. 메인과 두 목록, 요약이 끝난 글을 절대 주소로 담는다(목록 필터 주소는 canonical과 같이 뺀다) */
export function sitemapEntries(postIds: number[]): MetadataRoute.Sitemap {
  return [
    { url: SITE_URL },
    { url: `${SITE_URL}/ic` },
    { url: `${SITE_URL}/gb` },
    ...postIds.map((id) => ({ url: `${SITE_URL}/posts/${id}` })),
  ];
}

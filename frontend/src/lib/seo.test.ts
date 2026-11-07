import { describe, expect, it } from "vitest";
import type { PostDetail } from "./api";
import { cardPhotoType, isCompleteCard, ogCardVersion, ogPhotoUrl, postMetadata, SITE_URL, sitemapEntries } from "./seo";

const LARGE = "https://img.test/thumbs/5-1280.webp";
const THUMB = "https://img.test/thumbs/5.webp";

function post(overrides: Partial<PostDetail> = {}): PostDetail {
  return {
    id: 5,
    board: "GB",
    title: "[GB] Space66% Spaceforge | 11/1 - 11/30",
    productName: "Space66% Spaceforge",
    author: "글쓴이",
    category: "KEYBOARD",
    subcategory: "KB_65",
    representativeImageUrl: "https://i.postimg.cc/a/b.jpg",
    representativeThumbUrl: THUMB,
    representativeLargeUrl: LARGE,
    images: [],
    summaryStatus: "DONE",
    summaryLines: ["65% 알루미늄 키보드 공동구매예요", "둘째 줄"],
    specs: [],
    vendors: [],
    originalUrl: "https://geekhack.org/index.php?topic=5.0",
    views: 100,
    replies: 1,
    statsCheckedAt: "2026-11-05T00:00:00Z",
    postedAt: "2026-11-01T00:00:00Z",
    estimatedPostedAt: null,
    lastPostAt: null,
    detailLoaded: true,
    ...overrides,
  };
}

describe("postMetadata", () => {
  it("제목은 상품 이름과 게시판이다(사이트 이름은 레이아웃 템플릿이 붙인다)", () => {
    expect(postMetadata(post()).title).toBe("Space66% Spaceforge 공동구매");
    expect(postMetadata(post({ board: "IC", productName: null, title: "[IC] Nightshift | 2026" })).title).toBe(
      "Nightshift IC",
    );
  });

  it("요약이 끝났으면 설명은 요약 첫 줄이다", () => {
    expect(postMetadata(post()).description).toBe("65% 알루미늄 키보드 공동구매예요");
  });

  it("요약 첫 줄이 120자를 넘으면 120자에서 자르고 …를 붙인다", () => {
    const long = "가".repeat(130);
    const exact = "나".repeat(120);

    expect(postMetadata(post({ summaryLines: [long] })).description).toBe(`${"가".repeat(120)}…`);
    expect(postMetadata(post({ summaryLines: [exact] })).description).toBe(exact);
  });

  it.each<[string, Partial<PostDetail>]>([
    ["요약 대기", { summaryStatus: "PENDING", summaryLines: [] }],
    ["요약 실패", { summaryStatus: "FAILED", summaryLines: [] }],
    ["요약 줄 없음", { summaryStatus: "DONE", summaryLines: [] }],
  ])("%s면 설명은 기본 문구다", (_, overrides) => {
    expect(postMetadata(post(overrides)).description).toBe(
      'geekhack 공동구매 글 "Space66% Spaceforge"의 한국어 요약과 원문 링크를 키체크에서 확인해 보세요',
    );
  });

  it("canonical은 목록 조건 쿼리 없는 상세 주소다", () => {
    expect(postMetadata(post()).alternates).toEqual({ canonical: "/posts/5" });
  });

  it("원문을 읽었고 요약이 끝나 요약 줄이 있으면 색인을 막지 않는다", () => {
    expect(postMetadata(post()).robots).toBeUndefined();
  });

  it.each<[string, Partial<PostDetail>]>([
    ["틀 글", { detailLoaded: false, summaryStatus: "PENDING", summaryLines: [] }],
    ["요약 대기", { summaryStatus: "PENDING", summaryLines: [] }],
    ["요약 실패", { summaryStatus: "FAILED", summaryLines: [] }],
    ["요약 줄 없음", { summaryStatus: "DONE", summaryLines: [] }],
  ])("%s은 색인하지 않고 링크는 따라가게 한다", (_, overrides) => {
    expect(postMetadata(post(overrides)).robots).toEqual({ index: false, follow: true });
  });

  it("openGraph는 글 카드와 사이트 이름 없는 제목을 둔다", () => {
    const metadata = postMetadata(post());

    expect(metadata.openGraph).toEqual({
      type: "article",
      url: "/posts/5",
      siteName: "키체크",
      locale: "ko_KR",
      title: "Space66% Spaceforge 공동구매",
      description: "65% 알루미늄 키보드 공동구매예요",
      publishedTime: "2026-11-01T00:00:00Z",
      images: [
        {
          url: `/posts/5/og.png?v=${ogCardVersion(post())}`,
          width: 1200,
          height: 630,
          alt: "Space66% Spaceforge",
        },
      ],
    });
  });

  it("작성일을 모르면 publishedTime을 넣지 않는다", () => {
    const openGraph = postMetadata(post({ postedAt: null })).openGraph as { publishedTime?: string };

    expect(openGraph.publishedTime).toBeUndefined();
  });
});

describe("ogPhotoUrl", () => {
  it("큰 축소본을 같은 도메인의 1200×630 JPEG 변환 주소로 바꾼다", () => {
    expect(ogPhotoUrl(post())).toBe(
      "https://img.test/cdn-cgi/image/width=1200,height=630,fit=cover,format=jpeg/thumbs/5-1280.webp",
    );
  });

  it("큰 축소본이 없으면 작은 축소본을 쓴다", () => {
    expect(ogPhotoUrl(post({ representativeLargeUrl: null }))).toBe(
      "https://img.test/cdn-cgi/image/width=1200,height=630,fit=cover,format=jpeg/thumbs/5.webp",
    );
  });

  it("축소본이 없으면 원본이 있어도 null이다", () => {
    expect(ogPhotoUrl(post({ representativeLargeUrl: null, representativeThumbUrl: null }))).toBeNull();
    expect(ogPhotoUrl(post({ representativeLargeUrl: "", representativeThumbUrl: "" }))).toBeNull();
  });
});

describe("ogCardVersion", () => {
  it("같은 글이면 같은 값이다", () => {
    expect(ogCardVersion(post())).toBe(ogCardVersion(post({ views: 999, summaryLines: [] })));
  });

  it("사진, 이름, 게시판이 바뀌면 값이 바뀐다", () => {
    const base = ogCardVersion(post());

    expect(ogCardVersion(post({ representativeLargeUrl: "https://img.test/thumbs/6-1280.webp" }))).not.toBe(base);
    expect(ogCardVersion(post({ productName: "다른 이름" }))).not.toBe(base);
    expect(ogCardVersion(post({ board: "IC" }))).not.toBe(base);
  });

  it("주소에 그대로 넣을 수 있는 짧은 글자다", () => {
    expect(ogCardVersion(post())).toMatch(/^[0-9a-z]{1,7}$/);
  });
});

describe("sitemapEntries", () => {
  it("메인과 두 목록을 먼저, 글은 받은 순서대로 절대 주소로 담는다", () => {
    expect(sitemapEntries([30, 12])).toEqual([
      { url: SITE_URL },
      { url: `${SITE_URL}/ic` },
      { url: `${SITE_URL}/gb` },
      { url: `${SITE_URL}/posts/30` },
      { url: `${SITE_URL}/posts/12` },
    ]);
  });

  it("요약이 끝난 글이 없어도 메인과 두 목록은 담는다", () => {
    expect(sitemapEntries([]).map((entry) => entry.url)).toEqual([SITE_URL, `${SITE_URL}/ic`, `${SITE_URL}/gb`]);
  });

});

describe("cardPhotoType", () => {
  it("JPEG·PNG만 카드 사진으로 쓴다", () => {
    expect(cardPhotoType("image/jpeg")).toBe("image/jpeg");
    expect(cardPhotoType("image/png; charset=binary")).toBe("image/png");
    expect(cardPhotoType("IMAGE/JPEG")).toBe("image/jpeg");
  });

  it("WebP나 오류 페이지, 형식이 없는 응답은 쓰지 않는다", () => {
    expect(cardPhotoType("image/webp")).toBeNull();
    expect(cardPhotoType("text/html; charset=UTF-8")).toBeNull();
    expect(cardPhotoType(null)).toBeNull();
  });
});

describe("isCompleteCard", () => {
  it("사진과 글꼴을 모두 받았거나, 사진이 없는 글에서 글꼴을 받았으면 끝까지 그린 카드다", () => {
    expect(isCompleteCard(true, true, true)).toBe(true);
    expect(isCompleteCard(false, false, true)).toBe(true);
  });

  it("넣으려던 사진이나 글꼴을 받지 못했으면 오래 캐시하지 않는다", () => {
    expect(isCompleteCard(true, false, true)).toBe(false);
    expect(isCompleteCard(true, true, false)).toBe(false);
    expect(isCompleteCard(false, false, false)).toBe(false);
  });
});

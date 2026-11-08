import { describe, expect, it } from "vitest";
import type { PostDetail, SaleDetail } from "./api";
import {
  breadcrumbJsonLd,
  cardPhotoType,
  isCompleteCard,
  jsonLdHtml,
  ogCardVersion,
  ogPhotoUrl,
  postMetadata,
  saleMetadata,
  postTitle,
  SITE_CARD_ALT,
  SITE_URL,
  siteOpenGraph,
  sitemapEntries,
} from "./seo";

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

describe("postTitle", () => {
  it("상품 이름, 한국어 분류어(키보드·키캡), 게시판 순서다", () => {
    expect(postTitle(post())).toBe("Space66% Spaceforge 키보드 공동구매");
    expect(postTitle(post({ board: "IC", category: "KEYCAP", productName: "GMK Knight" }))).toBe("GMK Knight 키캡 IC");
    // 이름에 이미 분류가 들어 있으면 분류어를 겹쳐 붙이지 않는다
    expect(postTitle(post({ board: "IC", category: "KEYBOARD", productName: "RHINO60 Custom Keyboard" }))).toBe(
      "RHINO60 Custom Keyboard IC",
    );
    expect(postTitle(post({ board: "GB", category: "KEYCAP", productName: "Mock Keycaps" }))).toBe("Mock Keycaps 공동구매");
  });

  it("기타·미분류 글은 분류어를 넣지 않는다", () => {
    expect(postTitle(post({ category: "OTHER" }))).toBe("Space66% Spaceforge 공동구매");
    expect(
      postTitle(post({ board: "IC", category: "UNCLASSIFIED", productName: null, title: "[IC] Nightshift | 2026" })),
    ).toBe("Nightshift IC");
  });
});

describe("postMetadata", () => {
  it("제목은 상품 이름, 분류어, 게시판이다(사이트 이름은 레이아웃 템플릿이 붙인다)", () => {
    expect(postMetadata(post()).title).toBe("Space66% Spaceforge 키보드 공동구매");
    expect(
      postMetadata(post({ board: "IC", category: "UNCLASSIFIED", productName: null, title: "[IC] Nightshift | 2026" }))
        .title,
    ).toBe("Nightshift IC");
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
      title: "Space66% Spaceforge 키보드 공동구매",
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

  it("분류가 바뀌어도(제목의 분류어) 값은 그대로다", () => {
    expect(ogCardVersion(post({ category: "KEYCAP" }))).toBe(ogCardVersion(post()));
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
  it("메인과 두 목록, 국내 판매 목록을 먼저, 글과 판매 글은 받은 순서대로 절대 주소로 담는다", () => {
    expect(
      sitemapEntries(
        [
          { id: 30, lastModified: null },
          { id: 12, lastModified: null },
        ],
        [{ id: 3, lastModified: null }],
      ),
    ).toEqual([
      { url: SITE_URL },
      { url: `${SITE_URL}/ic` },
      { url: `${SITE_URL}/gb` },
      { url: `${SITE_URL}/sales` },
      { url: `${SITE_URL}/posts/30` },
      { url: `${SITE_URL}/posts/12` },
      { url: `${SITE_URL}/sales/3` },
    ]);
  });

  it("요약을 마친 시각을 아는 글만 lastModified를 넣는다", () => {
    expect(
      sitemapEntries(
        [
          { id: 30, lastModified: "2026-11-07T12:30:00Z" },
          { id: 12, lastModified: null },
        ],
        [],
      ).slice(4),
    ).toEqual([{ url: `${SITE_URL}/posts/30`, lastModified: "2026-11-07T12:30:00Z" }, { url: `${SITE_URL}/posts/12` }]);
  });

  it("판매 글도 lastModified를 알 때만 넣는다", () => {
    expect(sitemapEntries([], [{ id: 3, lastModified: "2026-11-08T00:00:00Z" }]).slice(4)).toEqual([
      { url: `${SITE_URL}/sales/3`, lastModified: "2026-11-08T00:00:00Z" },
    ]);
  });

  it("글이 없어도 메인과 두 목록, 판매 목록은 담는다", () => {
    expect(sitemapEntries([], []).map((entry) => entry.url)).toEqual([
      SITE_URL,
      `${SITE_URL}/ic`,
      `${SITE_URL}/gb`,
      `${SITE_URL}/sales`,
    ]);
  });
});

describe("siteOpenGraph", () => {
  it("사이트 이름·언어·주소와 사이트 기본 카드를 둔다(레이아웃 openGraph를 바꿔도 카드가 빠지지 않게)", () => {
    expect(siteOpenGraph("/ic")).toEqual({
      type: "website",
      url: "/ic",
      siteName: "키체크",
      locale: "ko_KR",
      images: [{ url: "/opengraph-image", width: 1200, height: 630, alt: SITE_CARD_ALT, type: "image/png" }],
    });
  });

  it("제목·설명은 두지 않아 페이지의 title·description을 물려받는다", () => {
    const openGraph = siteOpenGraph("/") as { title?: string; description?: string };

    expect(openGraph.title).toBeUndefined();
    expect(openGraph.description).toBeUndefined();
  });
});

describe("breadcrumbJsonLd", () => {
  it("글은 키체크 › 게시판 목록 › 상품 이름이고, 마지막 항목은 주소를 두지 않는다", () => {
    expect(breadcrumbJsonLd("GB", "Space66% Spaceforge")).toEqual({
      "@context": "https://schema.org",
      "@type": "BreadcrumbList",
      itemListElement: [
        { "@type": "ListItem", position: 1, name: "키체크", item: SITE_URL },
        { "@type": "ListItem", position: 2, name: "공동구매", item: `${SITE_URL}/gb` },
        { "@type": "ListItem", position: 3, name: "Space66% Spaceforge" },
      ],
    });
  });

  it("목록은 키체크 › 게시판이다", () => {
    expect(breadcrumbJsonLd("IC").itemListElement).toEqual([
      { "@type": "ListItem", position: 1, name: "키체크", item: SITE_URL },
      { "@type": "ListItem", position: 2, name: "IC" },
    ]);
  });
});

describe("jsonLdHtml", () => {
  it("<를 바꿔 문자열이 script 태그를 닫지 못하게 한다", () => {
    const html = jsonLdHtml({ name: "</script><script>alert(1)</script>" });

    expect(html).not.toContain("<");
    expect(JSON.parse(html)).toEqual({ name: "</script><script>alert(1)</script>" });
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

function sale(overrides: Partial<SaleDetail> = {}): SaleDetail {
  return {
    id: 7,
    vendor: "SWAGKEYS",
    productName: "Neo80 Cu",
    category: "KEYBOARD",
    saleType: "RESTOCK",
    phase: "OPEN",
    startDate: "2026-11-06",
    startTime: "19:30",
    endDate: null,
    endTime: null,
    summaryFirstLine: null,
    thumbUrl: null,
    largeUrl: null,
    imageUrl: null,
    postedAt: "2026-11-06T09:42:00Z",
    summaryLines: ["Neo80 Cu 6가지 색 조합이 다시 들어왔어요."],
    shippingNote: null,
    links: [],
    images: [],
    ...overrides,
  };
}

describe("saleMetadata", () => {
  it("제목은 상품 이름, canonical은 판매 상세 주소다", () => {
    const meta = saleMetadata(sale());

    expect(meta.title).toBe("Neo80 Cu");
    expect(meta.alternates).toEqual({ canonical: "/sales/7" });
  });

  it("설명은 요약 첫 줄이고, 120자를 넘으면 자르고 …를 붙인다", () => {
    expect(saleMetadata(sale()).description).toBe("Neo80 Cu 6가지 색 조합이 다시 들어왔어요.");
    expect(saleMetadata(sale({ summaryLines: ["가".repeat(130)] })).description).toBe(`${"가".repeat(120)}…`);
    expect(saleMetadata(sale({ summaryLines: ["나".repeat(120)] })).description).toBe("나".repeat(120));
  });

  it("요약 줄이 없으면 기본 문구다", () => {
    expect(saleMetadata(sale({ summaryLines: [] })).description).toBe(
      "Swagkeys 디스코드 공지에서 정리한 국내 판매 소식 \"Neo80 Cu\"를 키체크에서 확인해 보세요",
    );
  });

  it("openGraph는 사이트 기본 카드에 판매 상세 주소를 둔다", () => {
    expect(saleMetadata(sale()).openGraph).toEqual(siteOpenGraph("/sales/7"));
  });
});

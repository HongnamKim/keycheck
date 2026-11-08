import { describe, expect, it } from "vitest";
import type { SaleCard, SalePick } from "./api";
import { homePickScript, homeVariants, pickLedgerWhen, wideSaleImage } from "./homePick";

const HUMAN_UA =
  "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Safari/537.36";

/** 인라인 스크립트를 가짜 document·navigator·Math로 실행하고, 스크립트 바로 앞 요소의 data-pick을 돌려준다 */
function runScript(ua: string, random: number): string {
  const attrs: Record<string, string> = { "data-pick": "gk" };
  const wrapper = {
    setAttribute: (name: string, value: string) => {
      attrs[name] = value;
    },
  };
  const document = { currentScript: { previousElementSibling: wrapper } };
  const navigator = { userAgent: ua };
  const fakeMath = { random: () => random };
  new Function("document", "navigator", "Math", homePickScript())(document, navigator, fakeMath);
  return attrs["data-pick"];
}

describe("homePickScript", () => {
  it("무작위 값이 0.5보다 작으면 국내 판매(kr)를 고른다", () => {
    expect(runScript(HUMAN_UA, 0.3)).toBe("kr");
  });

  it("무작위 값이 0.5 이상이면 geekhack(gk) 그대로 둔다", () => {
    expect(runScript(HUMAN_UA, 0.7)).toBe("gk");
    expect(runScript(HUMAN_UA, 0.5)).toBe("gk");
  });

  it("검색·미리보기 봇이면 고르지 않는다", () => {
    expect(runScript("Mozilla/5.0 (compatible; Googlebot/2.1; +http://www.google.com/bot.html)", 0.1)).toBe("gk");
    expect(runScript("Mozilla/5.0 (compatible; Yeti/1.1; +https://naver.me/spd)", 0.1)).toBe("gk");
    // Next 기본 봇 목록(HTML_LIMITED_BOT_UA_RE)에만 있는 UA
    expect(runScript("WhatsApp/2.23.20.0 A", 0.1)).toBe("gk");
  });

  it("UA가 비면 고르지 않는다", () => {
    expect(runScript("", 0.1)).toBe("gk");
  });

  it("앞 요소가 없으면 아무것도 하지 않는다", () => {
    const document = { currentScript: { previousElementSibling: null } };
    const run = () =>
      new Function("document", "navigator", "Math", homePickScript())(document, { userAgent: HUMAN_UA }, { random: () => 0.1 });
    expect(run).not.toThrow();
  });
});

function sale(id: number, extra: Partial<SaleCard> = {}): SaleCard {
  return {
    id,
    noticeId: 100 + id,
    vendor: "SWAGKEYS",
    productName: `판매 ${id}`,
    category: "KEYCAP",
    saleType: "DROP",
    phase: "ANNOUNCE",
    startDate: null,
    startTime: null,
    endDate: null,
    endTime: null,
    summaryFirstLine: null,
    thumbUrl: "https://img.test/s-640.webp",
    largeUrl: "https://img.test/s-1280.webp",
    imageUrl: "https://img.test/s-2048.webp",
    postedAt: "2026-11-06T09:52:00Z",
    ...extra,
  };
}

const p1: SalePick = { reason: "UPCOMING", sale: sale(1) };
const p2: SalePick = { reason: "RECENT", sale: sale(2) };

describe("homeVariants", () => {
  it("벤토 배치에 후보가 둘이면 큰 칸은 1순위, 띠는 geekhack 쪽 1순위·국내 판매 쪽 2순위", () => {
    expect(homeVariants([p1, p2], "bento")).toEqual({ krFeature: p1, bandGk: p1, bandKr: p2 });
  });

  it("후보가 하나면 국내 판매 쪽 띠는 없다", () => {
    expect(homeVariants([p1], "bento")).toEqual({ krFeature: p1, bandGk: p1, bandKr: null });
  });

  it("후보가 없으면 모두 없다", () => {
    expect(homeVariants([], "bento")).toEqual({ krFeature: null, bandGk: null, bandKr: null });
  });

  it("격자 배치(칸 부족)면 큰 칸을 바꾸지 않고 띠에 1순위", () => {
    expect(homeVariants([p1, p2], "grid")).toEqual({ krFeature: null, bandGk: p1, bandKr: null });
  });
});

describe("pickLedgerWhen", () => {
  const now = new Date("2026-11-08T03:00:00Z");

  it("곧 시작이면 판매 시작 날짜와 시각", () => {
    const pick: SalePick = { reason: "UPCOMING", sale: sale(1, { startDate: "2026-11-08", startTime: "20:00" }) };
    expect(pickLedgerWhen(pick, now)).toEqual({ key: "판매 시작", value: "11/8(일) 20:00" });
  });

  it("곧 마감이면 마감 날짜, 시각을 모르면 날짜만", () => {
    const pick: SalePick = {
      reason: "CLOSING",
      sale: sale(1, { phase: "OPEN", startDate: "2026-11-01", endDate: "2026-11-10" }),
    };
    expect(pickLedgerWhen(pick, now)).toEqual({ key: "마감", value: "11/10(화)" });
  });

  it("시작 날짜를 모르면 판매 시각 줄로", () => {
    const pick: SalePick = { reason: "RECENT", sale: sale(1) };
    expect(pickLedgerWhen(pick, now)).toEqual({ key: "판매 시각", value: "판매 일정 미정" });
  });
});

describe("wideSaleImage", () => {
  it("2048 보관본을 먼저, 못 읽으면 1280", () => {
    expect(wideSaleImage(sale(1))).toEqual({ src: "https://img.test/s-2048.webp", fallback: "https://img.test/s-1280.webp" });
  });

  it("보관본이 없으면 1280만, 둘 다 없으면 null", () => {
    expect(wideSaleImage(sale(1, { imageUrl: null }))).toEqual({ src: "https://img.test/s-1280.webp", fallback: null });
    expect(wideSaleImage(sale(1, { imageUrl: null, largeUrl: null }))).toBeNull();
  });
});

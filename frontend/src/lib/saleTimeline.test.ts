import { describe, expect, it } from "vitest";
import type { SaleCard } from "./api";
import { dayLabel, groupByNotice, scheduleBuckets } from "./saleTimeline";

// 11/8(일) 15:00 KST
const NOW = new Date("2026-11-08T06:00:00Z");

function card(id: number, overrides: Partial<SaleCard> = {}): SaleCard {
  return {
    id,
    noticeId: 1,
    vendor: "SWAGKEYS",
    productName: `상품 ${id}`,
    category: "KEYBOARD",
    saleType: "SALE",
    phase: "OPEN",
    startDate: null,
    startTime: null,
    endDate: null,
    endTime: null,
    summaryFirstLine: null,
    thumbUrl: null,
    largeUrl: null,
    imageUrl: null,
    // 11/7(토) 18:00 KST
    postedAt: "2026-11-07T09:00:00Z",
    ...overrides,
  };
}

const ids = (items: SaleCard[]) => items.map((s) => s.id);

describe("groupByNotice", () => {
  it("같은 날 두 공지를 순서대로 묶고, 다른 날은 따로 둔다", () => {
    const a = card(1, { noticeId: 20, postedAt: "2026-11-06T09:42:00Z" });
    const b = card(2, { noticeId: 20, postedAt: "2026-11-06T09:42:00Z" });
    const c = card(3, { noticeId: 19, vendor: "GEONWORKS", postedAt: "2026-11-06T06:31:00Z" });
    const d = card(4, { noticeId: 18, postedAt: "2026-11-05T09:02:00Z" });

    expect(groupByNotice([a, b, c, d])).toEqual([
      {
        day: "2026-11-06",
        notices: [
          { noticeId: 20, vendor: "SWAGKEYS", postedAt: "2026-11-06T09:42:00Z", items: [a, b] },
          { noticeId: 19, vendor: "GEONWORKS", postedAt: "2026-11-06T06:31:00Z", items: [c] },
        ],
      },
      { day: "2026-11-05", notices: [{ noticeId: 18, vendor: "SWAGKEYS", postedAt: "2026-11-05T09:02:00Z", items: [d] }] },
    ]);
  });

  it("noticeId가 없는 다른 공지는 같은 날이어도 따로 묶는다", () => {
    const base = { noticeId: undefined as unknown as number };
    const a = card(1, { ...base, vendor: "SWAGKEYS", postedAt: "2026-11-06T09:00:00Z" });
    const b = card(2, { ...base, vendor: "GEONWORKS", postedAt: "2026-11-06T08:00:00Z" });
    const c = card(3, { ...base, vendor: "GEONWORKS", postedAt: "2026-11-06T08:00:00Z" });

    const notices = groupByNotice([a, b, c])[0].notices;
    expect(notices.map((n) => ids(n.items))).toEqual([[1], [2, 3]]);
  });

  it("페이지를 이어 붙여도 같은 공지는 한 묶음", () => {
    const page1 = [card(1, { noticeId: 20 }), card(2, { noticeId: 20 })];
    const page2 = [card(3, { noticeId: 20 }), card(4, { noticeId: 19 })];

    const days = groupByNotice([...page1, ...page2]);
    expect(days).toHaveLength(1);
    expect(days[0].notices.map((n) => [n.noticeId, ids(n.items)])).toEqual([
      [20, [1, 2, 3]],
      [19, [4]],
    ]);
  });

  it("날짜는 한국 시각으로 나눈다", () => {
    // 11/5 15:30 UTC = 11/6 00:30 KST
    expect(groupByNotice([card(1, { postedAt: "2026-11-05T15:30:00Z" })])[0].day).toBe("2026-11-06");
  });

  it("빈 목록은 빈 배열", () => {
    expect(groupByNotice([])).toEqual([]);
  });

  it("noticeId가 없는 옛 저장본도 깨지지 않는다", () => {
    const old = { ...card(1), noticeId: undefined } as unknown as SaleCard;
    expect(groupByNotice([old])[0].notices[0].items).toEqual([old]);
  });
});

describe("dayLabel", () => {
  it.each([
    ["2026-11-08", "2026-11-08", "오늘"],
    ["2026-11-07", "2026-11-08", "어제"],
    ["2026-10-31", "2026-11-01", "어제"],
    ["2026-11-06", "2026-11-08", "11월 6일 금요일"],
    ["2026-10-28", "2026-11-08", "10월 28일 수요일"],
  ])("%s (오늘 %s) -> %s", (day, today, label) => {
    expect(dayLabel(day, today)).toBe(label);
  });
});

describe("scheduleBuckets", () => {
  const keyOf = (item: SaleCard) => scheduleBuckets([item], NOW)[0]?.key ?? null;

  it.each([
    // 곧 마감: 판매 중이고 7일 안에 마감
    ["마감이 7일 1분 전", { startDate: "2026-11-06", endDate: "2026-11-15", endTime: "14:59" }, "closing"],
    ["마감이 딱 7일 뒤", { startDate: "2026-11-06", endDate: "2026-11-15", endTime: "15:00" }, "closing"],
    ["마감이 7일 1분 뒤", { startDate: "2026-11-06", endDate: "2026-11-15", endTime: "15:01" }, "onSale"],
    ["마감 날짜만, 6일 뒤(그날 23:59:59)", { endDate: "2026-11-14" }, "closing"],
    ["마감 날짜만, 7일 뒤(그날 23:59:59)", { endDate: "2026-11-15" }, "onSale"],
    // 시작 예정: 한국 달력 날짜로
    ["오늘 20:30 시작", { phase: "ANNOUNCE", startDate: "2026-11-08", startTime: "20:30" }, "today"],
    ["시작 날짜만, 오늘", { phase: "ANNOUNCE", startDate: "2026-11-08" }, "today"],
    ["내일 시작", { phase: "ANNOUNCE", startDate: "2026-11-09", startTime: "01:00" }, "tomorrow"],
    ["6일 뒤 시작", { phase: "ANNOUNCE", startDate: "2026-11-14", startTime: "20:00" }, "week"],
    ["7일 뒤 시작", { phase: "ANNOUNCE", startDate: "2026-11-15" }, "later"],
    ["시작 전이면 마감이 가까워도 시작 구간", { startDate: "2026-11-09", startTime: "20:00", endDate: "2026-11-10" }, "tomorrow"],
    // 판매 중
    ["시작이 지난 예고(마감 없음)", { phase: "ANNOUNCE", startDate: "2026-11-06", startTime: "19:30" }, "onSale"],
    ["오늘 시작 시각이 지남", { phase: "ANNOUNCE", startDate: "2026-11-08", startTime: "14:00" }, "onSale"],
    ["시작·마감 없는 OPEN, 공지 3일 전", { postedAt: "2026-11-05T06:00:00Z" }, "onSale"],
    ["시작 없는 일정 변경", { phase: "CHANGE" }, "onSale"],
    // 일정 미정
    ["시작 없는 예고", { phase: "ANNOUNCE" }, "tbd"],
  ] as [string, Partial<SaleCard>, string][])("%s -> %s", (_, overrides, key) => {
    expect(keyOf(card(1, overrides))).toBe(key);
  });

  it.each([
    ["마감 시각이 지남", { startDate: "2026-11-06", endDate: "2026-11-08", endTime: "14:00" }],
    ["마감이 지금과 같음", { startDate: "2026-11-06", endDate: "2026-11-08", endTime: "15:00" }],
    ["마감 날짜만, 어제", { startDate: "2026-11-06", endDate: "2026-11-07" }],
    ["마감 없이 시작 뒤 7일이 지남", { startDate: "2026-11-01", startTime: "14:00" }],
    ["시작·마감 없는 OPEN, 공지 8일 전", { postedAt: "2026-10-31T06:00:00Z" }],
    ["시작 없는 예고, 공지 31일 전", { phase: "ANNOUNCE", postedAt: "2026-10-08T06:00:00Z" }],
  ] as [string, Partial<SaleCard>][])("끝난 건은 뺀다: %s", (_, overrides) => {
    expect(scheduleBuckets([card(1, overrides)], NOW)).toEqual([]);
  });

  it("구간 순서와 라벨, 빈 구간은 뺀다", () => {
    const items = [
      card(1, { phase: "ANNOUNCE" }),
      card(2, {}),
      card(3, { phase: "ANNOUNCE", startDate: "2026-11-20" }),
      card(4, { phase: "ANNOUNCE", startDate: "2026-11-09" }),
      card(5, { startDate: "2026-11-06", endDate: "2026-11-09" }),
    ];
    expect(scheduleBuckets(items, NOW).map((b) => [b.key, b.label, ids(b.items)])).toEqual([
      ["closing", "곧 마감", [5]],
      ["tomorrow", "내일", [4]],
      ["later", "다음 주 이후", [3]],
      ["onSale", "판매 중", [2]],
      ["tbd", "일정 미정", [1]],
    ]);
    expect(
      scheduleBuckets([card(6, { phase: "ANNOUNCE", startDate: "2026-11-08", startTime: "20:00" }), card(7, { phase: "ANNOUNCE", startDate: "2026-11-12" })], NOW).map(
        (b) => b.label,
      ),
    ).toEqual(["오늘", "이번 주"]);
  });

  it("곧 마감은 마감 이른 순", () => {
    const items = [
      card(1, { endDate: "2026-11-12" }),
      card(2, { endDate: "2026-11-09", endTime: "23:59" }),
      card(3, { endDate: "2026-11-09" }),
    ];
    expect(ids(scheduleBuckets(items, NOW)[0].items)).toEqual([2, 3, 1]);
  });

  it("시작 구간은 시작 이른 순, 날짜만 아는 시작은 그날 시각 아는 건 뒤", () => {
    const items = [
      card(1, { phase: "ANNOUNCE", startDate: "2026-11-12" }),
      card(2, { phase: "ANNOUNCE", startDate: "2026-11-12", startTime: "20:00" }),
      card(3, { phase: "ANNOUNCE", startDate: "2026-11-11", startTime: "21:00" }),
    ];
    expect(ids(scheduleBuckets(items, NOW)[0].items)).toEqual([3, 2, 1]);
  });

  it("판매 중은 시작(없으면 공지) 이른 순, 일정 미정은 공지 이른 순", () => {
    const items = [
      card(1, { startDate: "2026-11-06", startTime: "19:30" }),
      card(2, { postedAt: "2026-11-05T06:00:00Z" }),
      card(3, { postedAt: "2026-11-07T06:00:00Z" }),
      card(4, { phase: "ANNOUNCE", postedAt: "2026-11-07T06:00:00Z" }),
      card(5, { phase: "ANNOUNCE", postedAt: "2026-11-01T06:00:00Z" }),
    ];
    const buckets = scheduleBuckets(items, NOW);
    expect(buckets.map((b) => [b.key, ids(b.items)])).toEqual([
      ["onSale", [2, 1, 3]],
      ["tbd", [5, 4]],
    ]);
  });
});

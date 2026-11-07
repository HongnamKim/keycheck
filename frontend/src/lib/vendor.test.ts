import { describe, expect, it } from "vitest";
import type { SpecItem, Vendor } from "./api";
import { isVendorSpec, vendorLine, vendorLink, vendorSpecRows } from "./vendor";

describe("vendorLine", () => {
  it("한국 벤더를 앞에 링크와 함께 두고, 나머지는 이름(지역)을 쉼표로 잇는다", () => {
    const vendors: Vendor[] = [
      { name: "A", region: "US", url: "https://a" },
      { name: "B", region: "KR", url: "https://b" },
      { name: "C", region: "KR", url: null },
      { name: "D", region: null, url: null },
    ];

    expect(vendorLine(vendors)).toEqual({
      korean: [
        { name: "B", href: "https://b" },
        { name: "C", href: null },
      ],
      others: "A(US), D",
    });
  });

  it("한국 벤더의 주소가 http/https가 아니면 링크 없이 이름만 둔다", () => {
    expect(vendorLine([{ name: "B", region: "KR", url: "javascript:alert(1)" }])).toEqual({
      korean: [{ name: "B", href: null }],
      others: "",
    });
  });

  it("한국 벤더가 없으면 korean은 비고 모두 others로 간다", () => {
    expect(
      vendorLine([
        { name: "NovelKeys", region: "US", url: null },
        { name: "Zion", region: "EU", url: null },
      ]),
    ).toEqual({ korean: [], others: "NovelKeys(US), Zion(EU)" });
  });
});

describe("vendorLink", () => {
  it.each([
    ["https://x.y/z", "https://x.y/z"],
    ["http://x.y/z", "http://x.y/z"],
  ])("http/https 주소 %s는 그대로 쓴다", (url, expected) => {
    expect(vendorLink(url)).toBe(expected);
  });

  it.each([["javascript:alert(1)"], ["JavaScript:alert(1)"], ["mailto:a@b.c"], ["/products/x"], ["x.y/z"], [""]])(
    "http/https가 아닌 %s는 null",
    (url) => {
      expect(vendorLink(url)).toBeNull();
    },
  );

  it("주소가 없으면 null", () => {
    expect(vendorLink(null)).toBeNull();
  });
});

describe("isVendorSpec", () => {
  it.each([
    ["벤더", true],
    ["지역별 벤더", true],
    ["가격", false],
  ])("%s → %s", (label, expected) => {
    expect(isVendorSpec(label)).toBe(expected);
  });
});

describe("vendorSpecRows", () => {
  const vendors: Vendor[] = [{ name: "Swagkeys", region: "KR", url: null }];
  const price: SpecItem = { label: "가격", value: "$100" };
  const vendor: SpecItem = { label: "벤더", value: "Swagkeys(KR), NovelKeys(US) 등" };
  const regional: SpecItem = { label: "지역별 벤더", value: "KR: Swagkeys" };
  const date: SpecItem = { label: "일정", value: "11/1~11/30" };

  it("벤더 목록이 비면 AI 스펙표를 그대로 두고(벤더 줄 포함) 새 벤더 줄을 두지 않는다", () => {
    expect(vendorSpecRows([price, vendor, date], [])).toEqual({ specs: [price, vendor, date], vendorRowAt: null });
  });

  it("AI 벤더 줄을 빼고 그 자리에 새 벤더 줄을 둔다. 뒤 줄은 그대로 남는다", () => {
    expect(vendorSpecRows([price, vendor, date], vendors)).toEqual({ specs: [price, date], vendorRowAt: 1 });
  });

  it("벤더 줄이 여럿이면 모두 빼고 첫 벤더 줄 자리에 둔다", () => {
    expect(vendorSpecRows([price, vendor, date, regional], vendors)).toEqual({ specs: [price, date], vendorRowAt: 1 });
  });

  it("AI 벤더 줄이 없으면 끝에 둔다", () => {
    expect(vendorSpecRows([price, date], vendors)).toEqual({ specs: [price, date], vendorRowAt: 2 });
  });

  it("스펙표가 비어도 벤더가 있으면 새 벤더 줄 하나를 둔다", () => {
    expect(vendorSpecRows([], vendors)).toEqual({ specs: [], vendorRowAt: 0 });
  });
});

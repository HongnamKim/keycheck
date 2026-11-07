import type { SpecItem, Vendor } from "./api";

/** 한국 벤더의 지역 코드. 백엔드가 대문자로 맞춰 준다 */
const KOREA = "KR";

/** AI 스펙표에서 벤더를 적은 줄(라벨에 "벤더"가 들어감). 구조화된 벤더 목록이 있으면 상세가 이 줄을 숨긴다 */
export function isVendorSpec(label: string): boolean {
  return label.includes("벤더");
}

/**
 * 상세 스펙표 줄과 새 벤더 줄을 둘 자리. 벤더 목록이 있으면 AI 스펙표의 벤더 줄을 모두 빼고, 첫 벤더 줄이 있던 자리(없으면 끝)를
 * vendorRowAt으로 준다. 벤더 목록이 비면 스펙표를 그대로 두고(AI 벤더 줄을 그대로 보여준다) vendorRowAt은 null이다.
 */
export function vendorSpecRows(specs: SpecItem[], vendors: Vendor[]): { specs: SpecItem[]; vendorRowAt: number | null } {
  if (vendors.length === 0) return { specs, vendorRowAt: null };
  // 첫 벤더 줄 앞의 줄은 빠지지 않으므로 원래 자리가 곧 남은 줄에서의 자리다
  const first = specs.findIndex((spec) => isVendorSpec(spec.label));
  const rest = specs.filter((spec) => !isVendorSpec(spec.label));
  return { specs: rest, vendorRowAt: first < 0 ? rest.length : first };
}

/** 벤더 판매 페이지로 걸 주소. http/https 주소만 그대로 쓰고, 그 밖(javascript:, mailto:, 상대 주소 등)이나 없으면 null */
export function vendorLink(url: string | null): string | null {
  if (!url) return null;
  try {
    const { protocol } = new URL(url);
    return protocol === "http:" || protocol === "https:" ? url : null;
  } catch {
    return null;
  }
}

/**
 * 상세 "벤더" 줄. korean은 한국 벤더(굵게, href가 있으면 새 탭 링크), others는 나머지를 "이름(지역)"(지역을 모르면 이름만)으로
 * ", "로 이은 글자. 둘 다 받은 순서 그대로다.
 */
export function vendorLine(vendors: Vendor[]): { korean: { name: string; href: string | null }[]; others: string } {
  const korean = vendors.filter((v) => v.region === KOREA).map((v) => ({ name: v.name, href: vendorLink(v.url) }));
  const others = vendors
    .filter((v) => v.region !== KOREA)
    .map((v) => (v.region ? `${v.name}(${v.region})` : v.name))
    .join(", ");
  return { korean, others };
}

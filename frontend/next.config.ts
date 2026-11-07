import type { NextConfig } from "next";
import { HTML_LIMITED_BOT_UA_RE } from "next/dist/shared/lib/router/utils/html-bots";

// 백엔드 주소는 빌드할 때 코드에 박힌다. Vercel 운영 배포에서 빠뜨리면 빌드는 끝나도 화면의 모든 데이터 호출이 localhost로 가 실패하므로 빌드를 멈춘다.
// 미리보기(Preview) 배포는 막지 않는다(값이 없으면 화면이 "불러오지 못했어요"로 뜬다)
if (process.env.VERCEL_ENV === "production" && !process.env.NEXT_PUBLIC_API_BASE_URL) {
  throw new Error("NEXT_PUBLIC_API_BASE_URL이 없다. Vercel 환경변수에 백엔드 주소(https://api.keycheck.kr)를 넣고 다시 빌드한다");
}

const nextConfig: NextConfig = {
  /* config options here */
  cacheComponents: true,
  partialPrefetching: true,
  // 메타데이터를 <head>에 받아야 하는 봇(Next 기본 목록)에 다음(Daumoa·Daum/), 텔레그램, 카카오 미리보기를 더한다.
  // 설정하면 기본 목록을 통째로 바꾸므로 기본 목록 뒤에 붙인다. Next는 source만 읽어 대소문자 구분 없이 다시 만든다
  htmlLimitedBots: new RegExp(`${HTML_LIMITED_BOT_UA_RE.source}|Daumoa|Daum\\/|TelegramBot|kakaotalk-scrap`, "i"),
  images: {
    // 이미지는 백엔드가 만든 축소본(R2)이나 원본을 브라우저가 직접 읽는다. Vercel 이미지 변환을 쓰지 않는다(무료 한도 월 5,000건)
    unoptimized: true,
  },
  // 예전 목록 주소(/?category=keycap 등)는 IC 목록이었다. 쿼리는 그대로 /ic로 넘어간다.
  async redirects() {
    return ["category", "sub", "sort", "page"].map((key) => ({
      source: "/",
      has: [{ type: "query" as const, key }],
      destination: "/ic",
      permanent: false,
    }));
  },
};

export default nextConfig;

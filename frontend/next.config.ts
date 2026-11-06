import type { NextConfig } from "next";

const nextConfig: NextConfig = {
  /* config options here */
  cacheComponents: true,
  partialPrefetching: true,
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

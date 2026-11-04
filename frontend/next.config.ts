import type { NextConfig } from "next";

const nextConfig: NextConfig = {
  /* config options here */
  cacheComponents: true,
  partialPrefetching: true,
  images: {
    // 대표 이미지는 글마다 호스트가 제각각인 외부 원본이라 https 호스트를 모두 허용한다.
    // 사설 IP로 가는 주소는 Next가 기본으로 막는다.
    remotePatterns: [{ protocol: "https", hostname: "**" }],
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

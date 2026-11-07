import type { MetadataRoute } from "next";
import { SITE_BG, SITE_DESCRIPTION } from "@/lib/seo";

/** 안드로이드 "홈 화면에 추가" 등에서 쓰는 사이트 정보. 앱처럼 따로 띄우지 않고 브라우저로 연다 */
export default function manifest(): MetadataRoute.Manifest {
  return {
    name: "키체크",
    short_name: "키체크",
    description: SITE_DESCRIPTION,
    lang: "ko",
    start_url: "/",
    display: "browser",
    background_color: SITE_BG,
    theme_color: SITE_BG,
    icons: [
      { src: "/icon-192.png", sizes: "192x192", type: "image/png" },
      { src: "/icon-512.png", sizes: "512x512", type: "image/png" },
    ],
  };
}

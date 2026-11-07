import { siteCardImage, SITE_TAGLINE } from "@/lib/ogCard";

/**
 * 사이트 기본 미리보기 카드. 빌드 때 한 번 그린다.
 * 레이아웃에 붙어 메인·목록이 물려받고, 글 상세는 openGraph.images를 따로 둬 자기 카드(posts/[id]/og.png)를 쓴다.
 */
export const alt = `키체크 · ${SITE_TAGLINE}`;
export const size = { width: 1200, height: 630 };
export const contentType = "image/png";

export default function Image() {
  return siteCardImage();
}

import { Analytics } from "@vercel/analytics/next";
import type { Metadata } from "next";
import { IBM_Plex_Mono, IBM_Plex_Sans_KR } from "next/font/google";
import Link from "next/link";
import { Suspense } from "react";
import BoardNav, { BoardNavLinks } from "@/components/BoardNav";
import { SITE_URL } from "@/lib/seo";
import "./globals.css";
import styles from "./layout.module.css";

const plexSans = IBM_Plex_Sans_KR({
  variable: "--font-sans",
  weight: ["400", "500", "600", "700"],
  subsets: ["latin"],
  // 한글 글꼴은 글자 범위별로 잘게 나뉘어 미리 받기를 켜면 <head>에 파일 238개가 걸린다(브라우저가 다 받고, 수집기는 제목을 35KB 뒤에서야 본다).
  // 끄면 화면에 나온 글자 범위의 파일만 받는다
  preload: false,
});

const plexMono = IBM_Plex_Mono({
  variable: "--font-mono",
  weight: ["400", "500", "600"],
  subsets: ["latin"],
});

const description = "geekhack의 키보드·키캡 IC와 공동구매 글을 한국어 요약으로 먼저 훑어보세요";

export const metadata: Metadata = {
  // OG 이미지·canonical 같은 상대 주소의 기준 주소. 운영 주소가 기본이다
  metadataBase: new URL(SITE_URL),
  title: { default: "키체크", template: "%s · 키체크" },
  description,
  // 링크를 공유했을 때 보이는 기본 카드. 제목·설명은 두지 않아 페이지의 title·description을 물려받는다.
  // 이미지는 app/opengraph-image(사이트 기본 카드)가 붙고, 글 상세는 openGraph를 따로 둬 자기 카드를 쓴다
  openGraph: { siteName: "키체크", locale: "ko_KR", type: "website" },
  // 네이버 서치어드바이저 소유 확인(공개 값). 구글 서치 콘솔은 DNS TXT 레코드로 확인한다
  verification: { other: { "naver-site-verification": "d91b66acf1b3eed4c7149d0f1f5a0f79b6a6520c" } },
};

export default function RootLayout({ children }: LayoutProps<"/">) {
  return (
    <html lang="ko" className={`${plexSans.variable} ${plexMono.variable}`}>
      <body>
        {/* 화면 좌우 끝까지 채우는 띠. 오른쪽은 비워 둔다(나중에 로그인 자리) */}
        <header className={styles.header}>
          <div className={styles.headerInner}>
            <Link href="/" className={styles.logo} aria-label="키체크 메인으로">
              키체크
            </Link>
            {/* 지금 주소를 읽는 메뉴라 정적 셸에서는 표시 없는 메뉴를 먼저 그린다 */}
            <Suspense fallback={<BoardNavLinks />}>
              <BoardNav />
            </Suspense>
          </div>
        </header>
        <main className={styles.main}>{children}</main>
        {/* Vercel Web Analytics. 개발 모드는 보내지 않고 콘솔에만 찍고, Vercel 밖의 운영 빌드는 수집 스크립트(/_vercel/insights)가 없어 보내지 않는다 */}
        <Analytics />
      </body>
    </html>
  );
}

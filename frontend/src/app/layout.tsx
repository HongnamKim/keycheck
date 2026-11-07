import { Analytics } from "@vercel/analytics/next";
import type { Metadata } from "next";
import { IBM_Plex_Mono, IBM_Plex_Sans_KR } from "next/font/google";
import Link from "next/link";
import { Suspense } from "react";
import BoardNav, { BoardNavLinks } from "@/components/BoardNav";
import "./globals.css";
import styles from "./layout.module.css";

const plexSans = IBM_Plex_Sans_KR({
  variable: "--font-sans",
  weight: ["400", "500", "600", "700"],
  subsets: ["latin"],
});

const plexMono = IBM_Plex_Mono({
  variable: "--font-mono",
  weight: ["400", "500", "600"],
  subsets: ["latin"],
});

const description = "geekhack의 키보드·키캡 IC와 공동구매 글을 한국어 요약으로 먼저 훑어보세요";

export const metadata: Metadata = {
  // OG 이미지·canonical 같은 상대 주소의 기준 주소. 운영 주소가 기본이다
  metadataBase: new URL(process.env.NEXT_PUBLIC_SITE_URL ?? "https://keycheck.kr"),
  title: { default: "키체크", template: "%s · 키체크" },
  description,
  // 링크를 공유했을 때 보이는 기본 카드. 페이지가 openGraph를 따로 두지 않으면 이 값을 쓴다
  openGraph: { title: "키체크", description, siteName: "키체크", locale: "ko_KR" },
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

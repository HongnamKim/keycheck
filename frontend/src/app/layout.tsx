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
  weight: ["400", "500"],
  subsets: ["latin"],
});

export const metadata: Metadata = {
  title: { default: "키체크", template: "%s · 키체크" },
  description: "geekhack의 키보드·키캡 IC와 공동구매 글을 한국어 요약으로 먼저 훑어보세요",
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
      </body>
    </html>
  );
}

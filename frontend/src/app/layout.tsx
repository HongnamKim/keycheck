import type { Metadata } from "next";
import { IBM_Plex_Mono, IBM_Plex_Sans_KR } from "next/font/google";
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
  title: "키체크",
  description: "해외 키보드·키캡 인터레스트 체크를 한국어로 빠르게",
};

export default function RootLayout({ children }: LayoutProps<"/">) {
  return (
    <html lang="ko" className={`${plexSans.variable} ${plexMono.variable}`}>
      <body>
        <div className={styles.shell}>
          <header className={styles.header}>
            <div className={styles.titles}>
              <h1 className={styles.title}>키체크</h1>
              <p className={styles.subtitle}>해외 키보드·키캡 인터레스트 체크를 한국어로 빠르게</p>
            </div>
            <p className={styles.source}>geekhack Interest Checks</p>
          </header>
          <main>{children}</main>
        </div>
      </body>
    </html>
  );
}

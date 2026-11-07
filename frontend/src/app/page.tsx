import type { Metadata } from "next";
import { Suspense } from "react";
import BoardRail from "@/components/home/BoardRail";
import Dateline, { DatelineFallback } from "@/components/home/Dateline";
import HighlightBento, { HighlightBentoFallback } from "@/components/home/HighlightBento";
import { SITE_URL } from "@/lib/seo";
import styles from "./page.module.css";

export const metadata: Metadata = {
  title: { absolute: "키체크 · 키보드·키캡 IC·공동구매 한국어 요약" },
  alternates: { canonical: "/" },
};

/** 검색 결과의 사이트 이름(WebSite 구조화 데이터) */
const WEBSITE_JSON_LD = {
  "@context": "https://schema.org",
  "@type": "WebSite",
  name: "키체크",
  alternateName: "keycheck",
  url: SITE_URL,
};

/**
 * 메인. 날짜 줄 → 하이라이트(벤토) → IC 가로 줄 → 공동구매 가로 줄.
 * 머리 글은 바로 그리고, 날짜 줄·하이라이트·두 줄은 각자 받아 한쪽이 느려도 다른 칸을 막지 않는다.
 */
export default function Home() {
  return (
    <div className={styles.home}>
      <script
        type="application/ld+json"
        // <를 바꿔 글 안의 문자열이 script 태그를 닫지 못하게 한다
        dangerouslySetInnerHTML={{ __html: JSON.stringify(WEBSITE_JSON_LD).replace(/</g, "\\u003c") }}
      />
      <Suspense fallback={<DatelineFallback />}>
        <Dateline />
      </Suspense>

      <section className={styles.hl} aria-labelledby="home-hl-title">
        <div className={styles.hlHead}>
          <div>
            <p className={styles.kicker}>HIGHLIGHT</p>
            <h1 id="home-hl-title" className={styles.hlTitle}>
              요즘 키보드·키캡,
              <br />
              사진으로 먼저 봐요
            </h1>
          </div>
          <p className={styles.hlLead}>
            geekhack IC·공동구매 글에서 지금 볼 만한 글을 골랐어요. 아래로 내리면 게시판별 목록이 이어져요.
          </p>
        </div>

        <Suspense fallback={<HighlightBentoFallback />}>
          <HighlightBento />
        </Suspense>

        <nav className={styles.jump} aria-label="목록 바로 가기">
          <span className={styles.jumpCue}>↓ 아래로 이어져요</span>
          <a href="#home-ic" className={styles.jumpLink}>
            IC
          </a>
          <a href="#home-gb" className={styles.jumpLink}>
            공동구매
          </a>
        </nav>
      </section>

      <BoardRail board="ic" />
      <BoardRail board="gb" />
    </div>
  );
}

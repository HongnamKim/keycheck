import "server-only";

/** 미리보기 카드(1200×630 PNG)의 공통 모양. 사이트 기본 카드와 글 카드가 함께 쓴다. 색은 globals.css 값을 따른다 */

import { ImageResponse } from "next/og";
import type { Board } from "./api";
import { boardLabel } from "./format";
import { loadOgFonts } from "./ogFont";
import { SITE_TAGLINE } from "./seo";

export const OG_COLORS = {
  bg: "#f3f4f1",
  text: "#16181a",
  body: "#2e3236",
  accent: "#b93a0b",
  photoBg: "#2a2d30",
} as const;

export const OG_FONT_FAMILY = "IBM Plex Sans KR";

/** 카드 맨 아래 출처 줄 */
export const OG_SITE_LINE = "키체크 · keycheck.kr";

/** 게시판 배지. 공동구매는 강조색으로 채우고, IC는 사진 위면 흰 바탕, 밝은 바탕이면 테두리만 둔다(사이트 배지와 같은 규칙) */
export function OgBoardBadge({ board, onPhoto }: { board: Board; onPhoto: boolean }) {
  const gb = board === "GB";
  return (
    <div
      style={{
        display: "flex",
        alignItems: "center",
        height: 48,
        padding: "0 22px",
        borderRadius: 999,
        border: `2px solid ${gb ? OG_COLORS.accent : onPhoto ? "#ffffff" : OG_COLORS.text}`,
        background: gb ? OG_COLORS.accent : onPhoto ? "#ffffff" : "transparent",
        color: gb ? "#ffffff" : OG_COLORS.text,
        fontSize: 26,
        fontWeight: 700,
      }}
    >
      {boardLabel(board)}
    </div>
  );
}

/**
 * 사이트 기본 카드. 메인·목록이 쓰고(app/opengraph-image), 글 카드를 그릴 수 없을 때(없는 글, 백엔드 오류)도 쓴다.
 * 시안처럼 밝은 바탕에 굵은 이름과 헤더의 진한 선을 둔다.
 */
export async function siteCardImage(): Promise<ImageResponse> {
  const fonts = await loadOgFonts(`키체크${SITE_TAGLINE}keycheck.kr${boardLabel("IC")}${boardLabel("GB")}`);
  return new ImageResponse(
    (
      <div
        style={{
          width: "100%",
          height: "100%",
          display: "flex",
          flexDirection: "column",
          justifyContent: "space-between",
          padding: "84px 88px 72px",
          background: OG_COLORS.bg,
          color: OG_COLORS.text,
          fontFamily: OG_FONT_FAMILY,
        }}
      >
        <div style={{ display: "flex", flexDirection: "column" }}>
          <div style={{ fontSize: 148, fontWeight: 700, lineHeight: 1.05, letterSpacing: -6 }}>키체크</div>
          <div style={{ marginTop: 36, fontSize: 48, fontWeight: 500, lineHeight: 1.35, color: OG_COLORS.body }}>
            {SITE_TAGLINE}
          </div>
        </div>
        <div
          style={{
            display: "flex",
            alignItems: "center",
            justifyContent: "space-between",
            paddingTop: 32,
            borderTop: `3px solid ${OG_COLORS.text}`,
          }}
        >
          <div style={{ fontSize: 34, fontWeight: 700, color: OG_COLORS.accent }}>keycheck.kr</div>
          <div style={{ display: "flex", gap: 14 }}>
            <OgBoardBadge board="IC" onPhoto={false} />
            <OgBoardBadge board="GB" onPhoto={false} />
          </div>
        </div>
      </div>
    ),
    { width: 1200, height: 630, fonts },
  );
}

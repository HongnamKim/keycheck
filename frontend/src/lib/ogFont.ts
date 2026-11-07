import "server-only";

/**
 * 미리보기 카드(next/og)에 쓰는 사이트 글꼴 IBM Plex Sans KR. 카드에 들어갈 글자만 Google Fonts에서 받는다.
 * 받지 못하면 next/og 기본 글꼴로 그린다(카드를 오류로 끝내지 않는다).
 */

import { cacheLife } from "next/cache";

type OgFont = { name: string; data: ArrayBuffer; weight: 500 | 700; style: "normal" };

const FAMILY = "IBM Plex Sans KR";
/** 이 브라우저로 물어야 Google Fonts가 TTF 주소를 준다. 카드 도구(satori)는 WOFF2를 읽지 못한다 */
const TTF_USER_AGENT =
  "Mozilla/5.0 (Macintosh; U; Intel Mac OS X 10_6_8; de-at) AppleWebKit/533.21.1 (KHTML, like Gecko) Version/5.0.5 Safari/533.21.1";
const FONT_TIMEOUT_MS = 5000;

/**
 * 한 굵기의 글꼴 부분집합. 같은 글자 묶음은 서버에 남겨 다시 받지 않는다(빌드 때 그리는 기본 카드도 이 캐시로 미리 그려진다).
 * 실패하면 null이고 저장하지 않는다('use cache' 안에서 던지면 빌드가 실패한다).
 */
async function fetchFont(weight: 500 | 700, text: string): Promise<ArrayBuffer | null> {
  "use cache";
  try {
    const query = new URLSearchParams({ family: `${FAMILY}:wght@${weight}`, text });
    const cssRes = await fetch(`https://fonts.googleapis.com/css2?${query}`, {
      headers: { "User-Agent": TTF_USER_AGENT },
      signal: AbortSignal.timeout(FONT_TIMEOUT_MS),
    });
    if (!cssRes.ok) throw new Error(`글꼴 CSS 받기 실패: ${cssRes.status}`);
    const src = (await cssRes.text()).match(/src: url\((.+?)\) format\('(?:opentype|truetype)'\)/)?.[1];
    if (!src) throw new Error("글꼴 CSS에 TTF 주소가 없다");
    const fontRes = await fetch(src, { signal: AbortSignal.timeout(FONT_TIMEOUT_MS) });
    if (!fontRes.ok) throw new Error(`글꼴 받기 실패: ${fontRes.status}`);
    const data = await fontRes.arrayBuffer();
    cacheLife("max");
    return data;
  } catch (err) {
    console.error(err);
    cacheLife({ stale: 0, revalidate: 0, expire: 0 });
    return null;
  }
}

/** text는 카드에 그릴 글자 전부. 받지 못하면 undefined라 ImageResponse는 기본 글꼴을 쓴다 */
export async function loadOgFonts(text: string): Promise<OgFont[] | undefined> {
  const [medium, bold] = await Promise.all([fetchFont(500, text), fetchFont(700, text)]);
  if (!medium || !bold) return undefined;
  return [
    { name: FAMILY, data: medium, weight: 500, style: "normal" },
    { name: FAMILY, data: bold, weight: 700, style: "normal" },
  ];
}

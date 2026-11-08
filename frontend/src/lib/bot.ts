// 서버에서만 import한다(Next 내부 경로). 업그레이드로 경로가 바뀌면 빌드가 실패해 바로 드러난다
import { HTML_LIMITED_BOT_UA_RE } from "next/dist/shared/lib/router/utils/html-bots";

/**
 * 검색·미리보기 봇과 스크립트 수집기의 UA. 이들이 연 틀 글은 백엔드가 원문을 새로 읽지 않게 한다(설계 3.2).
 * Next의 userAgent().isBot은 Ahrefs·Semrush 같은 봇을 놓쳐 직접 둔다.
 * 카카오톡 인앱 브라우저(KAKAOTALK x.y)는 사람이라 걸리지 않는다. 미리보기 수집기는 kakaotalk-scrap이다.
 * 다음 검색 로봇의 지금 UA는 Daum/4.1이다(Daumoa는 옛 UA, 다음 앱 DaumApps/는 사람). GoogleOther는 UA에 bot이 없다.
 */
export const CRAWLER_UA =
  /bot|crawl|spider|slurp|scrap|facebookexternalhit|Yeti|Daumoa|Daum\/|GoogleOther|HeadlessChrome|Lighthouse|curl|wget|python|Go-http-client|okhttp|axios|node-fetch/i;

/** 봇이면 true. UA가 없으면 봇으로 본다. 메타데이터를 <head>로 받는 Next 기본 봇 목록(WhatsApp·BingPreview 등)도 봇이다 */
export function isCrawler(ua: string | null): boolean {
  if (!ua) return true;
  return CRAWLER_UA.test(ua) || HTML_LIMITED_BOT_UA_RE.test(ua);
}

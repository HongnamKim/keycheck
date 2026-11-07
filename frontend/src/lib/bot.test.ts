import { describe, expect, it } from "vitest";
import { isCrawler } from "./bot";

describe("isCrawler", () => {
  it.each([
    ["Googlebot", "Mozilla/5.0 (compatible; Googlebot/2.1; +http://www.google.com/bot.html)"],
    [
      "Googlebot 스마트폰",
      "Mozilla/5.0 (Linux; Android 6.0.1; Nexus 5X Build/MMB29P) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36 (compatible; Googlebot/2.1; +http://www.google.com/bot.html)",
    ],
    ["네이버 Yeti", "Mozilla/5.0 (compatible; Yeti/1.1; +https://naver.me/spd)"],
    ["다음 Daumoa", "Mozilla/5.0 (compatible; MSIE or Firefox mutant; not on Windows server;) Daumoa/4.0"],
    ["다음 검색 로봇", "Mozilla/5.0 (compatible; Daum/4.1; +https://cs.daum.net/faq/15/4118.html?faqId=28966)"],
    ["GoogleOther", "Mozilla/5.0 (compatible; GoogleOther)"],
    ["카카오 미리보기", "facebookexternalhit/1.1;kakaotalk-scrap/1.0; +https://devtalk.kakao.com/t/scrap/33984"],
    ["페이스북 미리보기", "facebookexternalhit/1.1 (+http://www.facebook.com/externalhit_uatext.php)"],
    ["디스코드 미리보기", "Mozilla/5.0 (compatible; Discordbot/2.0; +https://discordapp.com)"],
    ["curl", "curl/8.7.1"],
    ["Ahrefs", "Mozilla/5.0 (compatible; AhrefsBot/7.0; +http://ahrefs.com/robot/)"],
    ["WhatsApp 미리보기", "WhatsApp/2.23.20.0 A"],
    ["Bing 미리보기", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) BingPreview/1.0b"],
    ["Skype 미리보기", "Mozilla/5.0 (Windows NT 6.1; WOW64) SkypeUriPreview Preview/0.5"],
    ["Google 검사 도구", "Mozilla/5.0 (compatible; Google-InspectionTool/1.0;)"],
  ])("%s는 봇이다", (_, ua) => {
    expect(isCrawler(ua)).toBe(true);
  });

  it("UA가 없거나 비었으면 봇으로 본다", () => {
    expect(isCrawler(null)).toBe(true);
    expect(isCrawler("")).toBe(true);
  });

  it.each([
    [
      "Chrome",
      "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/141.0.0.0 Safari/537.36",
    ],
    [
      "iOS Safari",
      "Mozilla/5.0 (iPhone; CPU iPhone OS 18_6 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/18.6 Mobile/15E148 Safari/604.1",
    ],
    [
      "카카오톡 인앱 브라우저",
      "Mozilla/5.0 (iPhone; CPU iPhone OS 18_6 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Mobile/15E148 KAKAOTALK 10.9.5",
    ],
    [
      "카카오톡 인앱 브라우저(안드로이드)",
      "Mozilla/5.0 (Linux; Android 14; SM-S921N Build/UP1A.231005.007; wv) AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/141.0.0.0 Mobile Safari/537.36;KAKAOTALK 2610950",
    ],
    [
      "다음 앱",
      "Mozilla/5.0 (iPhone; CPU iPhone OS 18_6 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Mobile/15E148 DaumApps/8.4.0 DaumDevice/mobile",
    ],
  ])("%s는 사람이다", (_, ua) => {
    expect(isCrawler(ua)).toBe(false);
  });
});

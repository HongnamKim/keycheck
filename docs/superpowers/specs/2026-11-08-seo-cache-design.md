# 목록 캐싱·검색 노출(SEO)·링크 미리보기 설계

작성 2026-11-08. 근거 조사(코드·Next 16.4 문서·외부 자료)는 설계 에이전트 두 명과 반박 검증 두 명이 했다.

## 1. 왜 하는가

- 상세에서 "목록으로"·"메인으로"를 누르면 목록을 서버에서 다시 받느라 "글을 불러오는 중이에요"가 뜬다. 뒤로 가기는 바로 뜬다. 사용자 요청: 캐싱해서 링크로 돌아와도 바로 보이게.
- 글 링크를 카카오톡·디스코드에 붙이면 글 제목·사진이 아니라 사이트 기본 문구만 뜬다. 글마다 메타데이터가 없고, og 이미지·사이트맵도 없다. 사용자 요청: 검색 노출용 메타데이터와 미리보기 이미지, 사이트맵, 구글 서치 콘솔·네이버 서치어드바이저 등록.

## 2. 목표와 비목표

**목표**
- 목록(/ic, /gb, 필터 주소 포함)과 메인을 서버·브라우저에서 캐시한다. 상세에서 링크로 돌아오면 목록을 받은 지 15분, 메인은 5분 안이면 요청 없이 바로 보인다.
- 글 상세마다 제목·설명·대표 주소(canonical)·검색 노출 여부·미리보기 카드를 둔다. 사이트 기본 카드, 사이트맵, robots의 사이트맵 줄, 네이버 소유 확인 태그.
- 검색·미리보기 봇이 아직 원문을 읽지 않은 글을 열어도 geekhack을 읽거나 요약하지 않는다.
- 하루 요약 상한을 300건에서 2,000건으로 올린다.

**비목표**
- 상세 페이지 캐싱. 요약 진행 중 3초마다 다시 확인하므로 캐시하지 않는다.
- Article 구조화 데이터, 다음·Bing 등록(나중), RSS.
- 헤더 밑줄 변경. 점검 결과 주소와 어긋난 적이 없었고, 목록에서 연 상세에 남는 밑줄은 의도한 동작이라 그대로 둔다(사용자 결정).

**하면 안 되는 것**
- 봇 방문 때문에 원문 읽기·Claude 요약이 늘어나는 것.
- 백엔드 오류 화면이 캐시에 남아 정상화된 뒤에도 보이는 것.
- 요약 진행 중 화면 갱신이 낡은 값을 받는 것.
- 화면 문구에 "해외". 자동 테스트가 실제 geekhack·Anthropic·R2·Cloudflare를 부르는 것.

## 3. 정한 것

### 3.1 목록·메인 캐싱
- 서버 전용 새 모듈 `frontend/src/lib/cachedApi.ts`(맨 위 `import "server-only"`). `api.ts`는 클라이언트도 import해서 `'use cache'`를 넣으면 빌드가 막힌다.
  - `getFirstBatch(board, category, sort, sub | null)`: `'use cache'`, `cacheLife({ stale: 900, revalidate: 60, expire: 3600 })`. `{ batch, fetchedAt }`을 돌려준다. 목록 페이지와 메인 두 줄이 함께 쓴다.
  - `getCachedHighlights()`: `'use cache'`, `cacheLife({ stale: 300, revalidate: 60, expire: 900 })`. 새 글이 첫 방문자에게 늦게 보이는 시간을 줄이려고 expire를 짧게 둔다(300초 이상이어야 미리 그리기에 남는다).
- `BoardListPage`, `home/BoardRail`, `home/HighlightBento`, `home/Dateline`이 위 함수를 쓴다.
  - 캐시 밖의 `new Date()`는 `fetchedAt`으로 바꾼다. partialPrefetching에서는 캐시 밖 현재 시각이 미리 받기 렌더를 끊는다.
  - 캐시 함수는 실패하면 던지지 않고 캐시 수명을 0으로 둔 뒤 `null`을 돌려준다. Next 16.4는 캐시 함수 안의 오류를 받는 쪽이 잡아도 빌드 실패로 처리하기 때문이다(구현 중 확인). 수명 0인 결과는 저장되지 않고, 미리 그릴 때는 요청 때 채울 빈칸이 된다.
  - 받는 쪽은 `null`이면 `await connection()`을 먼저 부른 뒤 오류 안내를 그린다. 그래야 오류 안내가 정적 셸·재생성 결과·브라우저 캐시에 남지 않는다.
- `staleTimes`, `unstable_dynamicStaleTime`, `prefetch={true}`는 쓰지 않는다. 상세 페이지·`SummaryWatcher`·`StatsLive`·`PostFeed`·`feed.ts`는 그대로 둔다.
- 알려진 한계(Claude 판단으로 받아들임):
  - 목록 카드의 요약 상태·조회수는 최대 약 16분 늦을 수 있다. `router.refresh()`는 브라우저 캐시는 비우지만, 서버 캐시는 최대 60초 낡았을 수 있다.
  - "n분 전 확인" 같은 상대 시간의 기준이 받은 시각이 된다.
  - 메인은 트래픽이 적을 때 재생성 전의 옛 화면이 최대 15분 보일 수 있다.
  - Vercel 빌드가 메인을 미리 그리며 백엔드를 부른다. 빌드 때 백엔드가 없으면 그 칸은 요청 때 채운다.
  - 백엔드 장애 중에 메인 재생성이 일어나 세 칸이 모두 실패하면, 다음 배포까지 메인이 요청 때 채우는 화면으로 남을 수 있다(리뷰 추정). 오류 화면이 남지는 않으므로 성능 문제다.
  - `next dev`는 실패 결과를 한 번 더 보여 줄 수 있다. 캐시 동작은 `next start`로 확인한다.

### 3.2 봇은 원문을 새로 읽지 않는다
- 백엔드: `GET /api/posts/{id}?fill=false`(기본 true). false면 틀 글을 채우지 않고(`fillSkeleton` 생략), 미리 읽은 글의 요약 순서도 앞당기지 않는다(`clearPrefetched` 생략).
- 프론트: `src/lib/bot.ts`의 `isCrawler(ua: string | null): boolean`. UA가 없으면 봇으로 본다. 아래 정규식이나 Next 기본 봇 목록(`HTML_LIMITED_BOT_UA_RE`: WhatsApp, BingPreview 등 미리보기 봇)에 걸리면 봇이다.
  - `/bot|crawl|spider|slurp|scrap|facebookexternalhit|Yeti|Daumoa|Daum\/|GoogleOther|HeadlessChrome|Lighthouse|curl|wget|python|Go-http-client|okhttp|axios|node-fetch/i`
  - 카카오톡 인앱 브라우저(`KAKAOTALK x.y`)는 걸리지 않아야 한다.
- 상세 페이지와 `generateMetadata`는 `getPostForPage(id, fill)`를 함께 쓴다. React `cache`로 묶어 한 요청에서 한 번만 부른다. fill은 `headers()`의 UA로 정한다.
  - fill=false면 시간 제한은 기본 8초이고, 15초는 fill=true일 때만 쓴다.
- `SummaryWatcher`의 브라우저 재조회는 항상 `fill=false`로 부른다. 원문을 읽은 글에만 그려지므로 사람에게는 차이가 없다.
- 백엔드 도메인의 `GET /robots.txt`는 `User-agent: *` / `Disallow: /`이다. 백엔드 컨트롤러로 두어 이미지 배포만으로 반영되게 한다(Caddy를 고치면 서버에서 손으로 바꿔야 한다).
- 미리보기 봇(카카오·디스코드)도 채우지 않는다. 공유한 사람이 대개 먼저 열어 이미 채워져 있다.
- 사람이 막 연 글을 바로 공유하면, 요약 전이라 기본 문구로 스크랩될 수 있다(알려진 한계).

### 3.3 메타데이터
- 순수 함수 모듈 `src/lib/seo.ts`에 `SITE_URL`(layout에서 옮김), `postMetadata(post)`, `ogCardVersion(post)`, `ogPhotoUrl(post)`를 둔다.
- **글 상세(`generateMetadata`)**
  - **title:** `${displayTitle(productName, title)} ${boardLabel(board)}`. 템플릿으로 " · 키체크"가 붙는다.
  - **description:** 요약 완료면 `summaryLines[0]`을 120자에서 자르고 "…"를 붙인다. 아니면 `geekhack ${boardLabel} 글 "${이름}"의 한국어 요약과 원문 링크를 키체크에서 확인해 보세요`.
  - **canonical:** `/posts/${id}`. 목록 조건 쿼리(`?board=` 등)는 뺀다.
  - **robots:** 원문을 읽었고 요약 완료이고 요약 줄이 있을 때만 색인한다. 아니면 `{ index: false, follow: true }`. 백엔드 오류 때는 robots를 넣지 않는다(잠깐의 장애로 색인이 빠지지 않게).
  - **openGraph:** `type: "article"`, `url`, `siteName`, `locale: "ko_KR"`, `title`(" · 키체크" 없이), `description`, `publishedTime`, `images: [{ url: /posts/${id}/og.png?v=${ogCardVersion}, width: 1200, height: 630, alt }]`. twitter 카드는 openGraph에서 자동으로 채워진다(`summary_large_image`).
- **레이아웃:** openGraph에서 title·description을 빼서 페이지 값을 물려받게 하고, `siteName`·`locale`·`type: "website"`만 둔다. canonical은 레이아웃에 두지 않는다. 네이버 소유 확인 `verification.other["naver-site-verification"]`(이미 넣음).
- **메인:** `title.absolute`는 "키체크 · 키보드·키캡 IC·공동구매 한국어 요약", canonical은 `/`. WebSite JSON-LD(`name: 키체크`, `alternateName: keycheck`, `url`)는 `<`를 `<`로 바꿔 넣는다.
- **/ic, /gb:** canonical은 각 `/ic`·`/gb`이다. 필터 주소는 모두 여기로 모은다.
- **`next.config.ts`의 `htmlLimitedBots`:** Next 기본 목록(`next/dist/shared/lib/router/utils/html-bots`의 `HTML_LIMITED_BOT_UA_RE`)에 `Daumoa|Daum\/|TelegramBot|kakaotalk-scrap`을 더한다(다음 검색 로봇의 지금 UA는 `Daum/4.1`). 이 봇들은 메타데이터를 `<head>`에 받아야 한다.

### 3.4 미리보기 카드 (사용자 결정: 사진+제목 카드 생성)
- 글 카드 라우트는 `src/app/posts/[id]/og.png/route.tsx`이고, `next/og`의 `ImageResponse`로 1200×630 PNG를 만든다.
  - 사진을 깔고 아래쪽을 어둡게 한 뒤, 게시판 배지(IC/공동구매), 상품 이름(`displayTitle`), "키체크 · keycheck.kr"을 얹는다. 사진이 없으면 사이트 색 배경에 글자만 둔다.
  - 글 데이터는 `getPost(id, { fill: false })`로 받는다. 글이 없거나 백엔드 오류면 사이트 기본 카드를 그린다.
- **사진 형식:** 카드 도구(satori)는 우리 WebP 축소본을 그리지 못하고(빈 화면으로 확인), 카카오는 JPG·PNG만 받는다. 그래서 사진은 Cloudflare 이미지 변환으로 JPEG를 받는다.
  - `ogPhotoUrl`은 `representativeLargeUrl`(없으면 `representativeThumbUrl`)의 origin 뒤에 `/cdn-cgi/image/width=1200,height=630,fit=cover,format=jpeg`와 원래 경로를 붙인다.
  - 변환을 받지 못하면(꺼져 있거나 월 5,000건 무료 한도 초과) 사진 없는 카드로 그린다. 오류로 끝내지 않는다.
- **폰트:** IBM Plex Sans KR(사이트 글꼴) 700·500. Google Fonts CSS API에서 카드에 들어갈 글자만 `text=` 부분집합으로 받는다. satori가 WOFF2를 못 읽으므로 TTF 주소를 받는 방식으로 요청한다.
- **캐시와 주소:** `ogCardVersion`은 사진 주소, 이름, 게시판으로 만든 짧은 해시이고, 내용이 바뀌면 주소가 바뀐다(카카오는 이미지 주소로 캐시한다). 넣으려던 사진과 사이트 글꼴을 모두 받아 끝까지 그린 카드에만 `Cache-Control: public, max-age=86400, s-maxage=31536000, immutable`을 붙인다. 하나라도 못 받았으면 Next 기본값(캐시 안 함)이라 잠깐의 실패가 같은 주소로 1년 동안 남지 않는다. 사진과 글꼴은 함께 받는다(수집기가 기다리다 끊지 않게).
- **사이트 기본 카드:** `src/app/opengraph-image.tsx`. 빌드 때 한 번 그리는 정적 이미지로, 키체크와 "geekhack 키보드·키캡 IC·공동구매 한국어 요약"이 들어간다. 메인·목록이 쓰고, 글 페이지는 자기 카드를 쓴다.

### 3.5 사이트맵과 robots
- 백엔드 `GET /api/posts/sitemap`은 `[{ "id": 123 }]`을 `topicId` 내림차순으로 돌려준다.
  - 조건: 숨기지 않음, `topicId >= since`, 원문 읽음, 요약 완료, 요약 줄 있음. robots 색인 조건과 같게 맞춘다.
  - lastmod는 넣지 않는다. 요약 완료 시각을 담은 열이 없고, 부정확한 lastmod는 오히려 해롭다.
- 프론트 `src/app/sitemap.ts`는 `/`, `/ic`, `/gb`와 위 글들을 절대 주소로 담는다. 요청 때 만들고, 백엔드가 실패하면 500을 돌려준다(검색 엔진이 나중에 다시 가져간다).
- `robots.ts`는 기존 규칙을 유지하고 `sitemap: ${SITE_URL}/sitemap.xml`을 더한다.

### 3.6 하루 요약 상한
- `keycheck.summary.daily-limit` 기본값을 300에서 2,000으로 올린다(사용자 결정: 넉넉하게). `deploy/keycheck.env.example`과 문서의 300 표기도 고친다.
- 글은 한 번만 요약하므로 남은 글(약 5,000개)을 모두 요약해도 약 $110이고, 마지막 안전망은 Anthropic 월 한도 $100이다(사용자 설정).

## 4. 사용자가 할 일 (대시보드)
- **구글 서치 콘솔:** 도메인 속성으로 등록하고 DNS TXT로 소유를 확인한다. 배포 뒤 사이트맵 `https://keycheck.kr/sitemap.xml`을 제출한다.
- **네이버 서치어드바이저:** 배포 뒤 소유 확인, 사이트맵 제출, robots.txt 검증을 한다.
- **Cloudflare:** Images → Transformations에서 `keycheck.kr` 존을 켠다(무료 월 5,000건, 넘으면 새 변환만 실패하고 과금은 없다). 켜지 않아도 카드는 사진 없이 나온다.

## 5. 검증
- **로컬:** 프론트 `npm test && npm run lint && npm run build`, 백엔드 `./gradlew test`.
  - 빌드 표에서 `/ic`·`/gb`·`/posts/[id]`는 부분 prerender다. `/`는 백엔드가 있으면 정적(재검증 1m, 만료 15m), 없으면 부분 prerender다.
  - `NEXT_PUBLIC_API_BASE_URL=http://localhost:8080`(백엔드 없음)으로도 빌드가 통과해야 한다.
- **로컬 운영 빌드(`next start`)와 로컬 백엔드(요약기 fake, 수집 끔), Playwright로 확인:**
  - `/ic` → 글 → "목록으로": 미리 받기 헤더가 없는 `/ic` RSC 요청이 0건이고, 로딩 문구가 화면에 보이지 않는다.
  - 백엔드를 끄면 `LoadFailed`가 뜨고, 다시 켠 뒤 링크로 오면 정상 목록이 나온다.
  - 봇 UA로 틀 글을 열어도 `detail_fetched_at`이 null로 남는다.
  - 봇 UA의 `<head>`에 title·og·canonical·robots가 들어간다.
  - `/posts/{id}/og.png`가 1200×630 PNG로 나온다.
- **운영 배포 후(요약 완료 글로만):**
  - 카카오 공유 디버거와 디스코드에서 미리보기가 뜬다.
  - `sitemap.xml`과 `robots.txt`가 맞게 나온다.
  - `api.keycheck.kr/robots.txt`가 전체를 막는다.
  - 틀 글 시험은 배포된 백엔드 커밋을 확인한 뒤에만 한다. 옛 백엔드는 `fill` 파라미터를 무시하고 원문을 읽는다.

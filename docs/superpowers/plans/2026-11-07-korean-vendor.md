# 한국 벤더 표시 구현 계획

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 원문 첫 글의 링크를 저장하고, 요약할 때 AI가 벤더(이름·지역·판매 링크)를 뽑아, 한국 벤더가 있는 글에 "한국 벤더" 배지를 달고 상세 벤더 줄에서 한국 벤더를 앞에 굵게 링크로 보여준다.

**Architecture:** 파서가 `TopicPage`에 링크 목록을 더하고 `CrawlStore`가 `post.body_links`에 저장한다. 요약기는 `<links>`를 받아 `vendors`를 돌려주고, 넘긴 링크에 없는 주소는 버린다. 워커가 `post.vendors`에 저장한다. 카드 응답은 `hasKoreanVendor`, 상세 응답은 한국 벤더를 앞으로 정렬한 `vendors`를 준다. 프론트는 순수 함수로 배지 조건과 벤더 줄을 만든다.

**Tech Stack:** Spring Boot 4 / Java 21, jsoup, Flyway, JPA(jsonb), Anthropic Java SDK structured outputs, Next.js 16, vitest.

**Spec:** `docs/superpowers/specs/2026-11-07-korean-vendor-design.md`

## Global Constraints

- 커밋·푸시 금지(사용자가 말할 때만). 새 파일은 만들자마자 `git add`. `backend/.env`를 읽거나 출력하지 않는다.
- 자동 테스트는 실제 geekhack·Anthropic·R2를 부르지 않는다. 테스트 HTML은 구조만 흉내 내 직접 쓴다(원문 저장본을 커밋하지 않는다).
- Java: `record` 금지(`private final` + Lombok, jsonb 원소 클래스는 `SpecItem`처럼 `@Getter @EqualsAndHashCode` + `@JsonCreator`), 2칸 들여쓰기, 한국어 `@DisplayName` + AssertJ.
- 배지 문구는 정확히 "한국 벤더". 화면 문구에 "해외" 금지.
- 지역 코드는 대문자 문자열. 한국은 정확히 `"KR"`.
- 마이그레이션은 V11(V10까지 있음).
- 검증: 백엔드 `cd backend && ./gradlew test`, 프론트 `cd frontend && npm test && npm run lint && npm run build`.

## Review Focus

1. **AI가 원문에 없는 주소를 줌** → url은 null이 되고 이름만 보인다. Task 2 테스트.
2. **링크가 아주 많거나 이상한 글**(첨부 수백 개, `javascript:`·`mailto:` 링크, 같은 주소 반복, 아주 긴 주소) → 상한 안에서 http/https 판매 링크만 남는다. Task 1 테스트.
3. **"Swagkeys(KR/CN)"처럼 지역이 여럿** → KR로 보고 배지가 붙는다. Task 2 프롬프트·검증(대문자 정규화) 테스트.
4. **벤더 목록이 없거나 아직 뽑지 않은 글**(기존 글, 가짜 요약) → 배지 없음, 상세는 AI 스펙표 "벤더" 줄을 그대로 보여준다. Task 3·4 테스트.
5. **상세 벤더 줄의 링크 안전** → 새 탭 + `rel="noopener noreferrer"`, http/https만. Task 4 테스트(`vendorLink` 함수).

---

### Task 1: 첫 글 링크 수집과 저장

**Files:**
- Create: `backend/src/main/java/com/keycheck/post/domain/BodyLink.java`, `backend/src/main/resources/db/migration/V11__post_vendors.sql`
- Modify: `crawl/parser/TopicParser.java`, `crawl/dto/TopicPage.java`, `crawl/service/CrawlStore.java`(`writeContent`), `post/domain/Post.java`
- Test: `backend/src/test/java/com/keycheck/crawl/parser/TopicParserTest.java`, `crawl/service/CrawlStoreTest.java`

**Interfaces:**
- Produces:
  - `com.keycheck.post.domain.BodyLink(String text, String url)` — `@Getter @EqualsAndHashCode`, `@JsonCreator`(jsonb 원소, `SpecItem`과 같은 모양). `TopicPage`와 `Post`가 함께 쓴다.
  - `TopicPage`에 `List<BodyLink> links`(생성자 마지막 인자).
  - V11: `ALTER TABLE post ADD COLUMN body_links JSONB;`, `ALTER TABLE post ADD COLUMN vendors JSONB;`(둘 다 NULL 허용, 한국어 주석).
  - `Post`: `List<BodyLink> bodyLinks`(`@JdbcTypeCode(SqlTypes.JSON)`, `@Setter` — `CrawlStore.writeContent`가 넣는다). `vendors` 엔티티 필드는 Task 2가 더한다(Task 1은 칼럼만).
- `TopicParser` 규칙: 첫 글 `div.inner`의 `a[href]`, `abs:href`가 아니라 `attr("href")`를 `URI`로 검사해 scheme이 http/https인 것만. `action=dlattach`가 들어간 주소와 호스트가 `geekhack.org`(또는 `*.geekhack.org`)인 주소는 뺀다. 같은 url은 처음 것만. 최대 200개. text는 `link.text().strip()`을 200자로 자르고, 비면 url 자체. url이 2,000자를 넘으면 버린다.

- [ ] **Step 1: 실패하는 테스트**
  - `TopicParserTest.collectsBodyLinks`: 첫 글 본문에 `<a href="https://swagkeys.com/products/x">Swagkeys (KR)</a>`, `<a href="https://novelkeys.com/x">NovelKeys</a>`, 같은 swagkeys 주소 한 번 더, `<a href="mailto:a@b.c">메일</a>`, `<a href="javascript:void(0)">x</a>`, `<a href="https://geekhack.org/index.php?topic=1.0">다른 글</a>`, `<a href="https://geekhack.org/index.php?action=dlattach;topic=1.0;attach=2">첨부</a>`, 댓글(두 번째 post_wrapper)에 `<a href="https://comment.example">댓글 링크</a>` → links == `[BodyLink("Swagkeys (KR)", "https://swagkeys.com/products/x"), BodyLink("NovelKeys", "https://novelkeys.com/x")]`.
  - `TopicParserTest.capsBodyLinks`: 서로 다른 링크 250개 → 200개, 2,001자 주소는 빠짐, 글자가 빈 링크는 text가 url.
  - `CrawlStoreTest.storesBodyLinks`: `fillSkeleton`에 링크 두 개가 든 `TopicContent` → 다시 읽은 `post.getBodyLinks()`가 그 두 개.
- [ ] **Step 2: 실패 확인** — `./gradlew test --tests '*TopicParserTest' --tests '*CrawlStoreTest'` → FAIL.
- [ ] **Step 3: 구현** — 위 규칙. `TopicPage` 생성자 호출부(파서, 테스트 헬퍼) 모두 고친다.
- [ ] **Step 4: 통과 확인** — `./gradlew test` 전체 PASS.

### Task 2: 요약기가 벤더를 뽑아 저장

**Files:**
- Create: `backend/src/main/java/com/keycheck/post/domain/VendorItem.java`
- Modify: `summary/dto/PostSource.java`, `summary/dto/SummaryResult.java`, `summary/service/ClaudeSummarizer.java`, `summary/service/FakeSummarizer.java`, `summary/service/SummaryWorker.java`, `post/repository/PostRepository.java`(`saveSummary`), `post/domain/Post.java`(`vendors` 필드)
- Test: `summary/service/ClaudeSummarizerTest.java`, `FakeSummarizerTest.java`, `SummaryWorkerTest.java`

**Interfaces:**
- Consumes: Task 1 `BodyLink`, `Post.getBodyLinks()`.
- Produces:
  - `VendorItem(String name, String region, String url)`(`@Getter @EqualsAndHashCode`, `@JsonCreator`), `boolean isKorean()`(region이 `"KR"`).
  - `PostSource`에 `List<BodyLink> links`(마지막 인자, null이면 빈 목록으로 다룬다).
  - `SummaryResult`에 `List<VendorItem> vendors`(마지막 인자, 빈 목록 가능, null 아님).
  - `saveSummary(...)`에 `List<VendorItem> vendors`(마지막 인자) — `p.vendors = :vendors`.
  - 스키마: `vendors: {type: array, items: {type: object, properties: {name: string, region: {anyOf: [string, null]}, url: {anyOf: [string, null]}}, required: [name, region, url], additionalProperties: false}}`, required에 `vendors`.
  - 사용자 메시지: `<links>` 구역(링크가 있을 때만, 한 줄에 `{text} | {url}`, 최대 150개, `<body>` 다음).

- [ ] **Step 1: 실패하는 테스트**
  - `ClaudeSummarizerTest.mapsVendors`: links에 `("Swagkeys", "https://swagkeys.com/p")`, 응답 vendors `[{name:"Swagkeys", region:"kr", url:"https://swagkeys.com/p"}, {name:"NovelKeys", region:"US", url:null}]` → `[VendorItem("Swagkeys","KR","https://swagkeys.com/p"), VendorItem("NovelKeys","US",null)]`(region 대문자 정규화).
  - `dropsUnknownVendorUrl`: url이 links에 없는 `"https://evil.example"` → url null, 이름·지역은 유지.
  - `dropsBlankVendorAndCaps`: 이름이 빈 항목 제거, 31개면 30개.
  - `sendsLinks`: 사용자 메시지에 `<links>`와 `Swagkeys | https://swagkeys.com/p`. 링크가 없으면 `<links>` 구역 없음.
  - `schemaRequiresVendors`, `systemPromptDescribesVendors`(프롬프트에 `vendors`, `KR`, `<links>`, "링크 목록에 있는 주소만").
  - `FakeSummarizerTest`: vendors 빈 목록.
  - `SummaryWorkerTest.savesVendorsAndPassesLinks`: 글에 body_links(jdbcTemplate로 jsonb) → 요약기가 받은 `PostSource.getLinks()`가 그 목록, 결과 vendors가 `post.vendors`에 저장. 가짜 결과면 빈 목록 저장.
- [ ] **Step 2: 실패 확인** — `./gradlew test --tests '*Summarizer*' --tests '*SummaryWorkerTest'` → FAIL.
- [ ] **Step 3: 구현**
  - 프롬프트 구역 `vendors:`(설계 3.2 그대로): 본문에 적힌 판매처(벤더)만, 이름은 원문 표기, region은 원문에 적힌 지역 표기(KR, Korea, 한국, South Korea → KR; US, CA, EU, UK, AU, OCE, CN, SG, SEA, JP, TW 등 짧은 대문자 코드)만 근거로, 여러 지역이면 한국이 있으면 KR, 모르면 null, url은 `<links>`에 있는 주소 중 그 벤더의 판매 페이지만(지어내지 않는다, 없으면 null), 벤더 목록이 없으면 빈 배열, IC에 계획된 벤더가 적혀 있으면 뽑는다. 지역 근거가 없으면 상점 이름만 보고 KR로 정하지 않는다.
  - 검증(`toResult`가 넘긴 링크 집합을 받도록 `toResult(response, board, links)`): url이 링크 집합에 없으면 null, name strip 후 비면 제거, region strip·대문자·빈 값은 null, 최대 30개.
  - 워커: `new PostSource(..., post.getBodyLinks())`, 저장에 `result.getVendors()`.
- [ ] **Step 4: 통과 확인** — `./gradlew test` 전체 PASS.

### Task 3: API

**Files:**
- Create: `backend/src/main/java/com/keycheck/post/dto/VendorResponse.java`(`name`, `region`, `url`)
- Modify: `post/dto/PostCardResponse.java`, `post/dto/PostDetailResponse.java`, `post/controller/PostController.java`(설명 한 문장)
- Test: `backend/src/test/java/com/keycheck/post/controller/PostVendorApiTest.java`

**Interfaces:**
- Produces (JSON): 카드 `hasKoreanVendor: boolean`(`summaryStatus` 다음), 상세 `vendors: [{name, region, url}]`(한국 벤더 먼저, 각 무리 안은 저장 순서, vendors가 null이면 빈 배열).

- [ ] **Step 1: 실패하는 테스트** — `cardHasKoreanVendor`(vendors jsonb에 KR 하나 → true, US만 → false, null → false), `detailVendorsKoreanFirst`(`[US A, KR B, EU C, KR D]` → `[B, D, A, C]`), 하이라이트 응답 카드에도 `hasKoreanVendor`.
- [ ] **Step 2: 실패 확인** → FAIL.
- [ ] **Step 3: 구현** — 위치 인자 순서 맞춤. 상세는 `PostDetailResponse.from`에서 정렬.
- [ ] **Step 4: 통과 확인** — `./gradlew test` 전체 PASS.

### Task 4: 화면

**Files:**
- Create: `frontend/src/lib/vendor.ts` + `vendor.test.ts`, `frontend/src/components/KoreanVendorBadge.tsx` + `.module.css`
- Modify: `frontend/src/lib/api.ts`(타입), `frontend/src/lib/feed.test.ts` 등 카드 리터럴, `frontend/src/components/PostCard.tsx`, `components/home/RailCard.tsx`, `components/home/BentoCell.tsx`, `frontend/src/app/posts/[id]/page.tsx`(+ css)

**Interfaces:**
- Consumes: Task 3 JSON.
- Produces:
  - `api.ts`: `PostCard.hasKoreanVendor: boolean`, `interface Vendor { name: string; region: string | null; url: string | null }`, `PostDetail.vendors: Vendor[]`.
  - `vendor.ts`: `isVendorSpec(label: string): boolean`(라벨에 "벤더" 포함), `vendorLink(url: string | null): string | null`(http/https만, 아니면 null), `vendorLine(vendors: Vendor[]): { korean: { name: string; href: string | null }[]; others: string }`(others는 `"이름(지역)"`, 지역 없으면 이름만, `", "`로 이음).
  - `KoreanVendorBadge({ className?: string })` → `<span>한국 벤더</span>`.

- [ ] **Step 1: 실패하는 테스트** (`vendor.test.ts`) — `vendorLine([{US A url}, {KR B https://b}, {KR C null}, {null D}])` → `korean: [{name:"B", href:"https://b"}, {name:"C", href:null}]`, `others: "A(US), D"`; `vendorLink("javascript:alert(1)") === null`, `vendorLink("https://x.y/z") === "https://x.y/z"`; `isVendorSpec("벤더") && isVendorSpec("지역별 벤더") && !isVendorSpec("가격")`.
- [ ] **Step 2: 실패 확인** — `npm test` FAIL.
- [ ] **Step 3: 구현**
  - 배지: `hasKoreanVendor`면 목록 카드(PostCard, 게시판 배지 옆), 메인 하이라이트 칸(BentoCell의 배지 줄 — 작은 칸 포함 모든 사진 칸과 마감 띠), 메인 가로 줄 카드(RailCard 사진 위 칩 줄, 분류 칩 다음). 강조색 바탕 흰 글자 또는 강조색 테두리 중 기존 배지와 어울리는 쪽, 크기는 같은 자리의 기존 배지·칩과 맞춘다.
  - 상세: `post.vendors.length > 0`이면 스펙표에서 `isVendorSpec` 줄을 빼고, 그 자리(없으면 끝)에 `<dt>벤더</dt><dd>`: 한국 벤더는 `<strong>`(href 있으면 `<a target="_blank" rel="noopener noreferrer">` 안에), 그 뒤 `others`. 스펙표가 비어 있어도 벤더가 있으면 표를 그린다. vendors가 비면 지금 그대로.
- [ ] **Step 4: 확인** — `npm test && npm run lint && npm run build` PASS. 화면은 오케스트레이터가 실제 데이터로 확인한다.

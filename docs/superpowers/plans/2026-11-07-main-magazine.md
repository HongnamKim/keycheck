# 메인 매거진(C안) 적용 구현 계획

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 요약할 때 공동구매 기간을 뽑아 저장하고(제목이 바뀌면 다시), 하이라이트 API를 만들어, 메인을 승인된 C안(벤토 갤러리 + 가로 줄, 세 폭)으로 바꾼다.

**Architecture:** 백엔드는 요약 응답에 `gbStartDate`/`gbEndDate`를 더하고, "GB로 뽑아 봤는지"(`gb_dates_extracted`)와 "요약할 때의 제목"(`summarized_title`)으로 다시 요약 대상을 고른다. 하이라이트는 칸마다 JPQL 쿼리로 고르는 `PostHighlightService`가 `GET /api/posts/highlights`로 준다. 프론트 메인은 서버 컴포넌트가 데이터를 받고, 가로 줄의 스크롤·화살표·진행 막대만 클라이언트 컴포넌트다.

**Tech Stack:** Spring Boot 4.1.2 / Java 21, Flyway, JPA, Anthropic Java SDK(structured outputs), Testcontainers, Next.js 16(cacheComponents), CSS Modules, vitest.

**Spec:** `docs/superpowers/specs/2026-11-07-main-magazine-design.md` · 시안 `docs/design/main-c-responsive.html` · 코드 지도 `.superpowers/work/recon/{summary,api,front,mockup}.md`(git 제외, 구현자 참고용)

## Global Constraints

- 커밋·푸시 금지(사용자가 말할 때만). 새 파일은 만들자마자 `git add`. `backend/.env`를 읽거나 출력하지 않는다.
- 자동 테스트는 실제 geekhack·Anthropic·R2·이미지 호스트를 부르지 않는다.
- Java: `record` 금지(`private final` + Lombok `@Getter`/`@RequiredArgsConstructor`), 엔티티는 `@Getter`·`@NoArgsConstructor(access = PROTECTED)`, 들여쓰기 2칸, 테스트는 한국어 `@DisplayName` + AssertJ, 패키지는 도메인 아래 역할 폴더. JPQL enum은 완전한 이름(`com.keycheck.post.domain.Board.GB`).
- 화면 문구에 "해외" 금지. 문구는 해요체.
- 한국 시간: 백엔드 `ZoneId.of("Asia/Seoul")`, 프론트 `format.ts`의 `KST_OFFSET_MS` 방식.
- 날짜 전송 형식: `LocalDate` → `"YYYY-MM-DD"`. 프론트는 이 문자열을 `new Date()`로 파싱하지 않고 나눠서 쓴다.
- Vercel 이미지 변환 금지(이미 `images.unoptimized: true`). 이미지는 `RemoteImage` + `imageSources`.
- 폭 기준: 데스크톱 ≥1024px, 태블릿 600~1023px, 모바일 ≤599px(`@media (max-width: 1023px)`, `@media (max-width: 599px)`).
- 검증: 백엔드 `cd backend && ./gradlew test`, 프론트 `cd frontend && npm test && npm run lint && npm run build`(Node 22는 PATH에 있음).

## Review Focus

1. **GB 다시 요약이 끝없이 반복되는 경우**(제목이 요약 도중 또 바뀜, 요약이 실패함, 가짜 요약기) → 같은 제목으로는 한 번만 요약하고, 실패해도 다시 고르지 않는다(실패하면 요약은 DONE 그대로, 기간은 비운다). Task 2 테스트.
2. **Claude가 틀린 날짜를 줌**(`"2026-13-01"`, `"Nov 10"`, 마감<시작, IC 글에 날짜) → 날짜 둘 다 null, 요약은 저장. Task 1 테스트.
3. **하이라이트 후보가 모자람**(창이 비거나 글이 6개 미만) → 창을 넓히고, 그래도 없으면 칸을 빼며, 화면은 단순 격자로 남은 칸을 그린다(0칸이면 문구). Task 4·5 테스트, Task 7 Step 7 빈 상태 화면.
4. **한국 날짜 경계**(마감일이 오늘이면 D-DAY, 어제면 끝남, UTC 15:00 이후는 다음 날) → Task 5 테스트.
5. **축소본이 아직 없거나 사라짐** → 큰 칸은 1280 → 640 → 원본 순으로, 나머지는 640 → 원본(기존 RemoteImage 대체). Task 5 `bentoImage` 테스트.

---

## 백엔드

### Task 0: geekhack 첨부 원본을 받지 못하는 버그 (실제 실행에서 발견)

로컬에서 축소본 작업을 돌려 보니 geekhack 첨부(`index.php?action=dlattach…`) 162장이 모두 실패했다. 원인: geekhack이 `Content-Encoding: none`(비표준)을 보내고 Apache HttpClient 5가 `ClientProtocolException: Unsupported Content-Encoding: none`으로 거절한다(로그 `.superpowers/work/run-backend.log`). 이미지 원본에는 압축 해제가 필요 없다.

**Files:**
- Modify: `backend/src/main/java/com/keycheck/image/client/ImageDownloader.java`
- Test: `backend/src/test/java/com/keycheck/image/client/ImageDownloaderTest.java`

- [ ] **Step 1: 실패하는 테스트** — `acceptsContentEncodingNone`: 로컬 가짜 서버(기존 테스트 방식)가 `Content-Encoding: none` 헤더와 PNG 바이트를 돌려줌 → `download`가 그 바이트를 그대로 돌려준다. `ignoresGzipHeaderForImages`는 만들지 않는다(필요 없음).
- [ ] **Step 2: 실패 확인** — `./gradlew test --tests '*ImageDownloaderTest'` → FAIL(`원본을 받지 못했다`).
- [ ] **Step 3: 구현** — HttpClient 빌더에 `disableContentCompression()`(Accept-Encoding을 보내지 않고 응답을 풀지 않는다). 25MB 상한·시간 상한은 그대로. 클래스 주석에 이유 한 줄.
- [ ] **Step 4: 통과 확인** — `./gradlew test` 전체 PASS. 로그의 다른 실패 사유("원본을 받지 못했다" 8건 등)도 같은 원인인지 로그에서 확인해 보고만 한다(고치지 않는다).

### Task 1: 요약기가 공동구매 기간을 뽑는다

**Files:**
- Create: `backend/src/main/resources/db/migration/V9__post_gb_period.sql`
- Modify: `backend/src/main/java/com/keycheck/post/domain/Post.java`, `summary/dto/PostSource.java`, `summary/dto/SummaryResult.java`, `summary/service/ClaudeSummarizer.java`, `summary/service/FakeSummarizer.java`, `summary/service/SummaryWorker.java`(PostSource 생성 한 줄)
- Test: `backend/src/test/java/com/keycheck/summary/service/ClaudeSummarizerTest.java`, `FakeSummarizerTest.java`, `SummaryWorkerTest.java`(생성자 인자만)

**Interfaces:**
- Produces:
  - V9 칸: `gb_start_date DATE`, `gb_end_date DATE`, `gb_dates_extracted BOOLEAN NOT NULL DEFAULT FALSE`, `summarized_title VARCHAR(500)`(한국어 `--` 주석, 백필 없음 — 기존 요약은 `gb_dates_extracted = FALSE`라서 Task 2 조건이 잡는다).
  - `Post`: `LocalDate gbStartDate`, `LocalDate gbEndDate`, `boolean gbDatesExtracted`, `String summarizedTitle`(`@Column(length = 500)`), 모두 `@Setter` 없음.
  - `PostSource(Board board, String title, String bodyText, Instant postedAt)`
  - `SummaryResult(List<String> lines, List<SpecItem> specs, Category category, Subcategory subcategory, String summarizedBy, LocalDate gbStartDate, LocalDate gbEndDate)`
  - `ClaudeSummarizer.toResult(BetaMessage response, Board board)`(package-private static)

- [ ] **Step 1: 실패하는 테스트** (`ClaudeSummarizerTest`, 응답 JSON에 두 키를 더한 인라인 문자열로)
  - `extractsGbPeriod`: GB 소스, 응답 `"gbStartDate":"2026-10-30","gbEndDate":"2026-11-13"` → `LocalDate.of(2026,10,30)`, `LocalDate.of(2026,11,13)`.
  - `invalidGbDateDropsBoth`: `"gbStartDate":"2026-13-01","gbEndDate":"2026-11-13"` → 둘 다 null, 요약 줄은 그대로. `"Nov 10"`도 같은 결과.
  - `endBeforeStartDropsBoth`: 시작 `2026-11-13`, 마감 `2026-10-30` → 둘 다 null. 같은 날은 유지.
  - `icNeverHasGbPeriod`: `Board.IC` 소스, 응답에 날짜가 있어도 둘 다 null.
  - `missingGbKeysAreNull`: 키가 없는 기존 `SUMMARY_JSON` → 둘 다 null(기존 테스트 유지).
  - `schemaRequiresGbPeriod`: 요청 JSON의 `output_config.format.schema.properties.gbStartDate.anyOf[0].format == "date"`, `anyOf[1].type == "null"`, `required`에 `gbStartDate`·`gbEndDate` 포함.
  - `sendsPostedAt`: 사용자 메시지에 `<posted_at>\n2026-10-13T00:00:00Z\n</posted_at>`(JSON 이스케이프 `\\n`).
  - `systemPromptDescribesGbPeriod`: SYSTEM_PROMPT에 `gbStartDate`, `posted_at`, `취소`, `제목`, `끝난`(이미 끝난 공동구매도 적힌 날짜는 그대로 — 설계 3.5), `확신`(확신이 없으면 null) 포함(기존 `systemPromptDescribesBoards` 문구는 그대로).
  - `FakeSummarizerTest`: 결과 날짜가 둘 다 null.
- [ ] **Step 2: 실패 확인** — `./gradlew test --tests '*ClaudeSummarizerTest' --tests '*FakeSummarizerTest'` → FAIL(컴파일 오류).
- [ ] **Step 3: 구현**
  - FORMAT `properties`에 `"gbStartDate"`·`"gbEndDate"`: `{"anyOf": [{"type":"string","format":"date"}, {"type":"null"}]}`, `required`에 둘 다 추가.
  - `Output`은 날짜를 `String`으로 받는다(형식 오류로 역직렬화 예외가 나면 글 전체가 실패하므로). `toResult`가 `LocalDate.parse`, 실패·마감<시작이면 둘 다 null + `log.warn`, `board != GB`면 둘 다 null.
  - SYSTEM_PROMPT에 구역 추가(설계 3.1 그대로): GB 글만, 제목·본문에 적힌 날짜만, 연도가 없으면 `<posted_at>` 기준, 월/일·일/월이 모호하면 기간이 그럴듯한 쪽, 확신 없으면 null, 취소·연기면 마감 null, 이미 끝난 공동구매도 적힌 날짜는 그대로, **제목과 본문이 다르면 제목을 따른다**(바뀐 제목으로 다시 요약하므로), `YYYY-MM-DD`. 입력 설명에 `<posted_at>`(글 작성 시각, UTC) 추가.
  - `params()`: `<board>` 다음에 `<posted_at>\n{postedAt}\n</posted_at>`.
  - `SummaryWorker`의 `new PostSource(...)`에 `post.getPostedAt()`.
- [ ] **Step 4: 통과 확인** — 같은 명령 PASS, 이어 `./gradlew test` 전체 PASS.

### Task 2: 기간을 저장하고, 뽑아 본 적 없거나 제목이 바뀐 GB 글을 다시 요약

**Files:**
- Modify: `backend/src/main/java/com/keycheck/post/repository/PostRepository.java`(`findSummaryTargets`, `saveSummary`, 새 `markGbRedoFailed`), `summary/service/SummaryWorker.java`
- Test: `backend/src/test/java/com/keycheck/summary/service/SummaryWorkerTest.java`

**Interfaces:**
- Consumes: Task 1의 칸·`SummaryResult` 날짜.
- Produces:
  - `findSummaryTargets(long sinceTopicId, boolean redoFake, boolean redoMissingSubcategory, boolean redoGb, Limit limit)` — 기존 세 조건에 `or (:redoGb = true and DONE and p.board = GB and (p.gbDatesExtracted = false or p.summarizedTitle <> p.title))`. 정렬은 그대로(PENDING 먼저).
  - `saveSummary(Long id, List<String> lines, List<SpecItem> specs, Category category, Subcategory subcategory, String summarizedBy, LocalDate gbStartDate, LocalDate gbEndDate, boolean gbDatesExtracted, String summarizedTitle)`
  - `markGbRedoFailed(Long id, String summarizedTitle)` — `gb_start_date = null, gb_end_date = null, gb_dates_extracted = true, summarized_title = :summarizedTitle`만 바꾼다(요약 상태·요약은 그대로).

- [ ] **Step 1: 실패하는 테스트** (`SummaryWorkerTest`, GB 글은 `saveLoaded(id, p -> p.setBoard(Board.GB))` + `markSummarizedBy`, 칸은 jdbcTemplate)
  - `savesGbPeriodAndMarks`: GB PENDING 글, 결과에 날짜 → `gb_start_date`/`gb_end_date` 저장, `gb_dates_extracted = true`, `summarized_title = 제목`.
  - `icSummaryDoesNotMarkGb`: IC 글 요약 → `gb_dates_extracted = false`, `summarized_title = 제목`.
  - `redoesGbNeverExtracted`: Claude 결과 요약기(RecordingSummarizer에 `result` 지정)로, DONE GB 글(`gb_dates_extracted = false`) → 다시 요약됨. DONE IC 글은 아님.
  - `redoesGbWhenTitleChanged`: DONE GB 글(`extracted = true`, `summarized_title = '옛 제목'`, `title = '새 제목'`) → 다시 요약, 넘긴 제목은 '새 제목', 이후 `summarized_title = '새 제목'`, 다음 `summarizeNext()`는 false.
  - `icTitleChangeIsNotRedone`: DONE IC 글의 제목만 바뀜 → 대상 아님.
  - `fakeSummarizerSkipsGbRedo`: `new FakeSummarizer()`로 만든 워커 → DONE GB(`extracted = false`) 글을 고르지 않음.
  - `titleChangedDuringSummaryIsRedoneAgain`: DONE GB 글(`gb_dates_extracted = true`, `summarized_title = '옛 제목'`, `title = '새 제목'`)로 시작한다(DONE이라 첫 실행 뒤 `redone`에 들어간다). `onSummarize`가 첫 호출에서만 제목을 '더 새 제목'으로 바꿈 → 첫 `summarizeNext()` 뒤 `summarized_title = '새 제목'`(넘긴 제목), 같은 워커의 두 번째 `summarizeNext()`는 true이고 넘긴 제목이 '더 새 제목', 세 번째는 false. 요약기가 받은 제목은 `['새 제목', '더 새 제목']`(GB 다시 요약 대상에 `redone`을 걸면 두 번째가 false라 실패한다).
  - `failedGbRedoKeepsSummary`: DONE GB 글(`summarized_title = '옛 제목'`, `title = '새 제목'`, 기간 있음) 다시 요약 중 글 탓 실패(`summarizer.error = new IllegalStateException()`) → `summary_status`는 DONE, 요약 줄은 그대로, `gb_start_date`·`gb_end_date`는 null, `gb_dates_extracted = true`, `summarized_title = '새 제목'`, **새 워커 인스턴스**의 `summarizeNext()`도 false(메모리가 아니라 DB 표시로 막힌다). PENDING 글의 실패는 지금처럼 FAILED.
  - `failedOtherRedoStillFails`: redo-missing-subcategory 대상(DONE IC 키보드 글, 세부 분류 없음, `worker(false, true)`)의 글 탓 실패 → 지금처럼 FAILED(GB 다시 요약 규칙이 다른 다시 요약으로 번지지 않는다).
  - `apiUnavailableDuringGbRedoChangesNothing`: `AnthropicIoException` → 칸 그대로, 쉼(기존 ERROR_PAUSE 동작).
- [ ] **Step 2: 실패 확인** — `./gradlew test --tests '*SummaryWorkerTest'` → FAIL.
- [ ] **Step 3: 구현**
  - `boolean claude = !(summarizer instanceof FakeSummarizer)`; `redoGb = claude`(설정 없음, 늘 켜짐).
  - 대상 거르기: GB 다시 요약 대상(`claude && board == GB && DONE && (!extracted || !title.equals(summarizedTitle))`)은 `redone`으로 거르지 않는다. 그 밖의 다시 요약은 지금처럼 `redone`.
  - 저장: `gbDatesExtracted = post.getBoard() == Board.GB && claude`, `summarizedTitle = post.getTitle()`(요약기에 넘긴 제목).
  - 실패: 고른 글이 GB 다시 요약 대상(위 조건, 다른 다시 요약 조건에도 걸려 있어도)이고 글 탓 실패면 `markGbRedoFailed(id, post.getTitle())` + `redone.add(id)`, 요약 상태·요약은 바꾸지 않는다. 결정: 한국어 요약은 여전히 맞으므로 DONE을 유지하고, 바뀐 제목의 기간은 알 수 없으므로 기간을 비운다(설계 2장 "마감일이 불확실하면 D-day를 보여주지 않는다"). "뽑아 봤다"(`gb_dates_extracted = true`)와 `summarized_title`을 남겨 재시작 뒤에도 다시 고르지 않는다(설계 3.1 "뽑았는데 null인 글을 계속 다시 요약하지 않는다", Review Focus 1). 그 밖의 실패(PENDING 글, redo-fake·redo-missing-subcategory 다시 요약)는 지금처럼 FAILED.
  - 쿼리·워커 Javadoc 갱신.
- [ ] **Step 4: 통과 확인** — 같은 명령 PASS, `./gradlew test` 전체 PASS.

### Task 3: 카드 응답에 기간과 1280 축소본

**Files:**
- Modify: `backend/src/main/java/com/keycheck/post/dto/PostCardResponse.java`, `post/controller/PostController.java`(설명 한 문장)
- Test: `backend/src/test/java/com/keycheck/post/controller/PostThumbApiTest.java`(또는 새 `PostGbPeriodApiTest.java`)

**Interfaces:**
- Produces: `PostCardResponse`에 `String representativeLargeUrl`(`urls.url(representative.getThumbLargeKey())`), `LocalDate gbStartDate`, `LocalDate gbEndDate`. `from(...)` 시그니처는 그대로.

- [ ] **Step 1: 실패하는 테스트**
  - `cardHasGbPeriod`: GB 글에 `update post set gb_start_date = '2026-10-30', gb_end_date = '2026-11-13'` → 목록 `$.items[0].gbStartDate == "2026-10-30"`, `gbEndDate == "2026-11-13"`; 기간 없는 글은 둘 다 null.
  - `cardHasLargeThumb`: 대표 이미지 `thumb_large_key = 'thumbs/5-1280.webp'` → `$.items[0].representativeLargeUrl == "https://img.test/thumbs/5-1280.webp"`; 없으면 null. 하단 목록(neighbors) 카드도 같은 필드.
- [ ] **Step 2: 실패 확인** — 해당 테스트 FAIL.
- [ ] **Step 3: 구현** — 필드를 `representativeThumbUrl` 다음, 기간은 `detailLoaded` 앞에 두고 `new` 인자 위치를 맞춘다. 목록 API 설명에 "공동구매 글은 요약기가 뽑은 기간(gbStartDate·gbEndDate, 모르면 null)을 준다" 한 문장.
- [ ] **Step 4: 통과 확인** — `./gradlew test` 전체 PASS.

### Task 4: 하이라이트 API

**Files:**
- Create: `backend/src/main/java/com/keycheck/post/service/PostHighlightService.java`, `post/dto/HighlightSlot.java`(enum), `post/dto/HighlightSlotResponse.java`, `post/dto/HighlightResponse.java`
- Modify: `post/repository/PostRepository.java`, `post/controller/PostController.java`, `post/service/PostQueryService.java`(`cards`를 package-private으로)
- Test: `backend/src/test/java/com/keycheck/post/controller/PostHighlightApiTest.java`, `PostHighlightSinceApiTest.java`(경계 이전 글만, `since-topic-id` 속성), `OpenApiTest.java`(한 줄)
- Modify(테스트): `backend/src/test/java/com/keycheck/MutableClock.java`(`set(Instant)` 추가 — 테스트마다 시계를 되돌린다)

**Interfaces:**
- Consumes: Task 1 `Post.gbEndDate`, Task 3 카드 필드, `PostQueryService.cards(List<Post>)`.
- Produces (JSON, Task 5·7이 그대로 쓴다):
  - `GET /api/posts/highlights` → `HighlightResponse { LocalDate today; long newIcCount; long newGbCount; long endingSoonGbCount; List<HighlightSlotResponse> slots }`
  - `HighlightSlotResponse { HighlightSlot slot; boolean deadline; Integer windowDays; Integer rank; PostCardResponse post }` — 빈 칸은 목록에서 뺀다. 순서는 enum 순서.
  - `enum HighlightSlot { FEATURE, TALL, DEADLINE, IC_REPLIES, IC_VIEWS, GB_VIEWS }`
  - 리포지토리:
    - `List<Post> findHighlightsByViews(long sinceTopicId, Board board, Instant postedFrom, Limit limit)` — `order by p.views desc, p.topicId desc`
    - `List<Post> findHighlightsByReplies(long sinceTopicId, Board board, Instant postedFrom, Limit limit)` — `order by p.replies desc, p.views desc, p.topicId desc`
    - 위 둘의 조건: `hidden = false and topicId >= :sinceTopicId and detailFetchedAt is not null and representativeImageId is not null and board = :board and postedAt >= :postedFrom`
    - `List<Post> findHighlightsByDeadline(long sinceTopicId, LocalDate today, Limit limit)` — 공통 후보 조건(`hidden = false`, 경계, `detailFetchedAt is not null`, `representativeImageId is not null` — 설계 3.2: 마감 띠도 후보 조건을 그대로 쓴다) + `board = GB and gbEndDate >= :today`, `order by p.gbEndDate asc, p.views desc, p.topicId desc`
    - `long countPostedSince(long sinceTopicId, Board board, Instant postedFrom)`, `long countGbEndingBetween(long sinceTopicId, LocalDate from, LocalDate to)`(`board = com.keycheck.post.domain.Board.GB`, hidden·경계만 — 본문·이미지는 묻지 않는다. 게시판 조건은 GB에서 IC로 옮겨져 예전 기간이 남은 글을 빼려고 꼭 넣는다)

- [ ] **Step 1: 실패하는 테스트** (`PostHighlightApiTest`: MockMvc, `@MockitoBean GeekhackClient/RequestGate` + `@AfterEach verifyNoInteractions`, `MutableClock` 빈(`PostStatsApiTest` 방식), `@BeforeEach`에서 `clock.set(Instant.parse("2026-11-13T03:00:00Z"))`(KST 11/13 12:00)로 되돌린 뒤 데이터를 만든다 — 빈 하나를 테스트끼리 함께 쓰므로 순서에 따라 결과가 달라지지 않게. 날짜는 모두 시계 기준 상대값(`today.plusDays(n)`, `daysAgo`)으로 넣는다. 헬퍼 `save(Board, topicId, int daysAgo, int views, int replies)`는 대표 이미지 있는 본문 읽은 글, `postedAt = clock − daysAgo일`)
  - `picksSlotsByRules`: 데이터를 만들어 FEATURE = 7일 GB 조회 1위(windowDays 7, rank 1), TALL = 14일 IC 조회 1위, DEADLINE = 마감 가장 가까운 GB(`deadline = true`, rank null, windowDays null), IC_REPLIES = 14일 IC 댓글 1위(TALL 제외), IC_VIEWS = 14일 IC 조회 중 남은 첫 글(rank = 그 창 조회 순위, 예 2), GB_VIEWS = 30일 GB 조회 중 남은 첫 글. 각 `post.title`과 `windowDays`, `rank`를 단언.
  - `noRepeatAcrossSlots`: 글이 적어 같은 글이 여러 규칙의 1위여도 6칸에 같은 id가 두 번 나오지 않는다.
  - `widensWindow`: 7일 안에 GB가 없고 20일 전 GB만 있음 → FEATURE의 windowDays = 30. 90일 전만 있으면 90, 200일 전만 있으면 null(전체).
  - `deadlineFallsBackToPhoto`: 마감이 오늘 이전인 GB만 있음 → DEADLINE 칸은 `deadline = false`, 14일 GB 조회 중 남은 첫 글(창 넓히기 적용). 둘째 경우: 3일 전 GB(조회 100)와 10일 전 GB(조회 500) → FEATURE는 3일 전 글, 대체 칸은 10일 전 글이고 `rank = 1`(14일 GB 조회 1위가 FEATURE와 다르면 그 1위를 쓴다 — Step 3 "N위" 결정).
  - `deadlineIncludesToday`: `clock.set("2026-11-13T14:59:59Z")`(KST 11/13 23:59:59), `gb_end_date = 2026-11-13` → DEADLINE으로 뽑힌다(`today == "2026-11-13"`). `clock.set("2026-11-13T15:00:00Z")`(KST 11/14 0시) → 뽑히지 않고 `today == "2026-11-14"`.
  - `excludesNonCandidates`: 숨김, 틀 글, 대표 이미지 없음 GB 글에 마감(`today + 3`)도 넣는다(마감 띠 쿼리의 후보 조건까지 확인) → 이 글들은 DEADLINE을 포함해 어떤 칸에도 없다. 경계 이전 글(마감 `today + 3`인 GB 포함)은 `since-topic-id` 속성이 필요해 별도 클래스 `PostHighlightSinceApiTest`에서 같은 단언.
  - `omitsEmptySlots`: IC 글만 2개 → slots에 TALL·IC_REPLIES만(GB 칸 없음), 다른 칸 없음.
  - `countsDateline`: 최근 7일 IC 3개·GB 1개, 마감이 `today`·`today + 14`인 GB(포함), `today − 1`·`today + 15`인 GB(제외), 마감 `today + 3`인 IC 글(게시판이 IC라 제외). 마감을 넣은 글은 모두 8일 전 작성 → `newIcCount 3`, `newGbCount 1`, `endingSoonGbCount 2`.
  - `OpenApiTest`: `$.paths['/api/posts/highlights'].get.summary == "메인 하이라이트"`.
- [ ] **Step 2: 실패 확인** — `./gradlew test --tests '*PostHighlight*'` → FAIL.
- [ ] **Step 3: 구현**
  - 창 넓히기 순서: 처음 창 → 30일(처음 창 < 30일일 때만) → 90일 → 전체(`Instant.EPOCH`, windowDays null). 처음 창: FEATURE 7, TALL 14, DEADLINE 대체 14, IC_REPLIES 14, IC_VIEWS 14, GB_VIEWS 30.
  - 칸 순서 = 제외 순서(FEATURE → TALL → DEADLINE → IC_REPLIES → IC_VIEWS → GB_VIEWS). 각 쿼리는 `Limit.of(used.size() + 1)`로 읽어 쓴 id를 건너뛴 첫 글, `rank` = 그 글의 결과 목록 위치 + 1.
  - "N위" 결정: 설계 3.2의 "조회수 2위"(작은 칸 2, 마감 띠 대체)는 "앞 칸에 들어간 글은 다시 넣지 않는다"를 적용한 결과를 적은 것으로 읽는다(시안 메모도 "앞 칸에서 이미 고른 글은 건너뛰어요"). 그래서 모든 칸은 "앞 칸을 뺀 첫 글"을 고르고, 화면에는 `rank`로 실제 순위를 보여준다. 흔한 경우(1위가 앞 칸에 있음)에는 설계 문구와 결과가 같고, 1위가 비어 있으면 그 1위를 쓴다(2위부터 보면 1위가 남는데도 칸이 빌 수 있다). `deadlineFallsBackToPhoto` 둘째 경우가 이를 고정한다.
  - "최근 N일" = `clock.instant().minus(Duration.ofDays(n))`(굴러가는 창). `today = clock.instant().atZone(SEOUL).toLocalDate()`. 마감 임박 수 = `[today, today+14]`(양끝 포함).
  - 카드는 `PostQueryService.cards(...)` 재사용(한 번에 모아 부른다).
  - 컨트롤러: `@Operation(summary = "메인 하이라이트", description = ...)`, `/api/posts/highlights`(리터럴 경로가 `/{id}`보다 먼저 매칭된다).
- [ ] **Step 4: 통과 확인** — `./gradlew test` 전체 PASS.

---

## 프론트

### Task 5: 순수 함수와 API 타입

**Files:**
- Create: `frontend/src/lib/gbPeriod.ts` + `gbPeriod.test.ts`, `frontend/src/lib/rail.ts` + `rail.test.ts`, `frontend/src/lib/highlight.ts` + `highlight.test.ts`
- Modify: `frontend/src/lib/format.ts` + `format.test.ts`, `frontend/src/lib/api.ts`, `frontend/src/lib/feed.test.ts`(카드 리터럴에 새 필드)

**Interfaces:**
- Consumes: Task 3·4 JSON.
- Produces:
  - `api.ts`: `PostCard`에 `representativeLargeUrl: string | null; gbStartDate: string | null; gbEndDate: string | null`. `type HighlightSlotKind = "FEATURE" | "TALL" | "DEADLINE" | "IC_REPLIES" | "IC_VIEWS" | "GB_VIEWS"`, `interface HighlightSlot { slot: HighlightSlotKind; deadline: boolean; windowDays: number | null; rank: number | null; post: PostCard }`, `interface Highlights { today: string; newIcCount: number; newGbCount: number; endingSoonGbCount: number; slots: HighlightSlot[] }`, `getHighlights(): Promise<Highlights>`(실패하면 throw). 이름 대응: 백엔드 enum `HighlightSlot` ↔ 프론트 `HighlightSlotKind`, 백엔드 `HighlightSlotResponse` ↔ 프론트 `HighlightSlot`, `HighlightResponse` ↔ `Highlights`.
  - `format.ts`: `kstDateKey(now: Date): string`("2026-11-07", 가로 줄의 오늘), `formatDateline(today: string): string`(`"2026-11-07"` → "2026.11.07 토", `highlights.today`를 받는다), `formatShortDate(iso: string): string`(KST "11/1"), `formatMonthDay(date: string, withWeekday?: boolean): string`("11/13", "11/13(금)" — `YYYY-MM-DD` 문자열), `photoChipLabel(category: Category, subcategory: Subcategory | null): string | null`(세부 분류가 있고 `KB_ETC`/`KC_ETC`가 아니면 그 라벨, 아니면 KEYBOARD "키보드" / KEYCAP "키캡" / OTHER "기타", UNCLASSIFIED면 null).
  - `gbPeriod.ts`: `gbDaysLeft(endDate: string | null, today: string): number | null`(마감일 − 오늘 일수, 지났거나 null이면 null), `gbPeriodChip(startDate: string | null, endDate: string | null, today: string, withWeekday = false): { range: string; dday: string | null; ended: boolean } | null`(마감 null이면 null, 시작 null이면 `"~ 11/13"`, 지난 마감이면 range 끝에 `" 끝남"`·dday null. 가로 줄 칩, 큰 칸 장부(`withWeekday = true`), 마감 띠 메타가 함께 쓴다).
  - `highlight.ts`: `slotKicker(slot: HighlightSlot): string[]`(시안 `.mc-segs` 토막. 토막 안 띄어쓰기는 시안 `&nbsp;` 자리만 `\u00a0`), `bentoLayout(slots: HighlightSlot[]): "bento" | "grid" | "empty"`, `bentoImage(post: PostCard, large: boolean): { src: string; fallback: string | null } | null`.
  - `rail.ts`: `railProgress(scrollLeft: number, scrollWidth: number, clientWidth: number): { start: number; size: number }`(퍼센트 0~100), `railEdges(scrollLeft: number, scrollWidth: number, clientWidth: number): { atStart: boolean; atEnd: boolean }`(1px 여유).

- [ ] **Step 1: 실패하는 테스트**
  - `gbPeriodChip("2026-10-30", "2026-11-13", "2026-11-05")` → `{ range: "10/30 → 11/13", dday: "D-8", ended: false }`; 오늘이 11/13 → `dday: "D-DAY"`; 오늘이 11/14 → `{ range: "10/30 → 11/13 끝남", dday: null, ended: true }`; 시작 null → `range: "~ 11/13"`; 마감 null → `null`. `withWeekday = true`: `"10/30(금) → 11/13(금)"`, 시작 null → `"~ 11/13(금)"`, 오늘이 11/14 → `"10/30(금) → 11/13(금) 끝남"`(dday null).
  - `gbDaysLeft("2026-11-13", "2026-11-05") === 8`, 지남 → null, 같은 날 → 0.
  - `kstDateKey(new Date("2026-11-06T14:59:59Z")) === "2026-11-06"`, `"2026-11-06T15:00:00Z"` → `"2026-11-07"`; `formatDateline("2026-11-07") === "2026.11.07 토"`; `formatShortDate("2026-10-31T16:00:00Z") === "11/1"`; `formatMonthDay("2026-11-13", true) === "11/13(금)"`.
  - `photoChipLabel("KEYBOARD", "KB_60") === "60%"`, `("KEYCAP", "KC_ETC") === "키캡"`, `("UNCLASSIFIED", null) === null`.
  - `slotKicker`(아래 `␣`는 `\u00a0`): 큰 칸·세로 칸·마감 띠 대체 칸(c 자리, 넓은 칸)은 "최근"을 붙이고, 작은 칸(IC_REPLIES·IC_VIEWS·GB_VIEWS)은 시안처럼 뺀다. FEATURE(windowDays 7, rank 1) → `["최근␣7일 새 공동구매", "조회␣1위"]`, TALL(14, 1) → `["최근␣2주 새 IC", "조회␣1위"]`, IC_REPLIES(14, 1) → `["2주 새 IC", "댓글␣1위"]`, IC_VIEWS(14, 2) → `["2주 새 IC", "조회␣2위"]`, GB_VIEWS(30, 1) → `["30일 새 공동구매", "조회␣1위"]`, DEADLINE(deadline true) → `["곧 끝나는 공동구매"]`, DEADLINE(deadline false, 14, 2) → `["최근␣2주 새 공동구매", "조회␣2위"]`, windowDays 90 → `"최근␣90일 …"`(작은 칸 `"90일 …"`), null → `"전체 기간 …"`(작은 칸도 같음).
  - `bentoLayout`: 6칸이고 DEADLINE 포함 → `"bento"`, 1~5칸 → `"grid"`, 0칸 → `"empty"`.
  - `bentoImage(post, true)`: large 있음 → `{src: large, fallback: 원본}`; large 없고 thumb 있음 → `{src: thumb, fallback: 원본}`; 둘 다 없음 → `{src: 원본, fallback: null}`; 원본 없음 → `null`. `large=false`면 thumb만 본다.
  - `railProgress(0, 1000, 250)` → `{start: 0, size: 25}`, `railProgress(750, 1000, 250)` → `{start: 75, size: 25}`, 넘칠 것 없음(`scrollWidth <= clientWidth`) → `{start: 0, size: 100}`; `railEdges(0, …)` → `atStart true`, `railEdges(749, 1000, 250)` → `atEnd true`.
- [ ] **Step 2: 실패 확인** — `npm test` → FAIL.
- [ ] **Step 3: 구현** — 날짜 문자열은 `split("-")`·`Date.UTC`로 계산(시간대 없음). 요일 배열 `["일","월","화","수","목","금","토"]`.
- [ ] **Step 4: 통과 확인** — `npm test && npm run lint` PASS(빌드는 Task 7에서).

### Task 6: 헤더의 모바일 탭 줄

**Files:**
- Modify: `frontend/src/app/layout.module.css`, `frontend/src/components/BoardNav.module.css`

- [ ] **Step 1: 구현** — `@media (max-width: 599px)`: `.headerInner { gap: 0; padding: 6px 16px 0; }`, `.logo { font-size: 24px; }`; `.nav { flex: 0 0 calc(100% + 32px); gap: 0; margin: 6px -16px 0; padding: 0 8px; border-top: 1px solid var(--color-divider); }`, `.link { flex: 1 1 0; justify-content: center; min-height: 46px; font-size: 15px; }`. TSX는 바꾸지 않는다. 데스크톱·태블릿 모양은 그대로.
- [ ] **Step 2: 확인** — `npm run lint && npm run build` PASS. 화면 확인은 Task 7에서 함께.

### Task 7: 메인 화면

**Files:**
- Create: `frontend/src/components/home/Dateline.tsx`, `HighlightBento.tsx`, `BentoCell.tsx`, `BoardRail.tsx`, `RailCard.tsx`, `RailScroller.tsx`("use client"), 각 `.module.css`(이름은 구현자 판단)
- Modify: `frontend/src/app/page.tsx`, `frontend/src/app/page.module.css`(`.message`는 `not-found.tsx`가 쓰므로 남긴다)
- Delete: `frontend/src/components/BoardColumn.tsx`, `BoardColumn.module.css`; 이로써 안 쓰이게 된 `BOARD_INFO.badge` 필드(쓰는 곳이 없으면)

**Interfaces:**
- Consumes: Task 5 전부, `RemoteImage`(`src`, `fallbackSrc` 필수), `BoardBadge`, `formatViews`, `postHref`, `listHref`, `FreshListLink`.

- [ ] **Step 1: 구조** (`page.tsx`, 서버 컴포넌트)
  - 정적: HIGHLIGHT 머리(키커 "HIGHLIGHT", 제목 "요즘 키보드·키캡,<br/>사진으로 먼저 봐요", 리드 "geekhack IC·공동구매 글에서 지금 볼 만한 글을 골랐어요. 아래로 내리면 게시판별 목록이 이어져요." — 시안의 "여섯 개"는 칸이 빌 수 있어(설계 3.2) 뺀다), 바로 가기(`↓ 아래로 이어져요` + IC·공동구매 → `#home-ic`, `#home-gb`), 섹션 머리("01 IC", "02 공동구매", 부제, "전체 보기 →" = `listHref`).
  - `<Suspense>` 넷, 각자 따로: 날짜 줄(`Dateline`, getHighlights), 벤토(`HighlightBento`, 같은 getHighlights — 한 렌더에서 한 번만 요청된다), IC 줄(`BoardRail board="ic"`), 공동구매 줄(`BoardRail board="gb"`)(각 getPosts 첫 묶음 10개). 두 줄이 Suspense를 나눠 쓰지 않는다(지금 BoardColumn처럼 각자 뜨고, 하이라이트를 기다리지 않는다 — 설계 3.5). 각 fallback은 자리 표시(높이를 잡아 레이아웃이 튀지 않게). 실패하면 `unstable_rethrow` 후 "불러오지 못했어요" 문구(기존 BoardColumn 방식). 줄이 비면 "아직 수집된 글이 없어요", 벤토가 비면(`bentoLayout`이 `"empty"`) 벤토 자리에 "아직 고를 글이 없어요"(HIGHLIGHT 머리는 그대로).
  - "오늘": 날짜 줄과 벤토는 `highlights.today`(서버가 마감 띠를 고른 날)를 쓴다. 그래서 마감 띠의 `gbDaysLeft`는 늘 0 이상이다. 가로 줄은 getPosts를 await한 뒤 `kstDateKey(new Date())`로 구한다. 둘 다 자식에 문자열로 넘긴다(클라이언트에서 Date를 읽지 않는다).
- [ ] **Step 2: 날짜 줄** — `{formatDateline(highlights.today)}` + `최근 7일 새 IC {n} · 새 공동구매 {n} · 2주 안에 마감 {n}`. 시안의 "· 저녁판"은 넣지 않는다.
- [ ] **Step 3: 벤토** — `bentoLayout`이 `"bento"`면 시안 그리드(데스크톱 `"a a b d" "a a b e" "c c c f"`, 태블릿·모바일은 시안 값), `"grid"`면 남은 칸을 같은 크기 카드로 3열(태블릿 2열, 모바일 1열) 격자에. 칸 매핑: FEATURE=a, TALL=b, DEADLINE=c, IC_REPLIES=d, IC_VIEWS=e, GB_VIEWS=f.
  - 사진 칸: `bentoImage(post, slot === "FEATURE")`, 출처 배지(`BoardBadge`), 큰·세로 칸은 `photoChipLabel` 칩, 키커 `slotKicker` 토막(`.mc-segs`), 제목, 큰 칸만 요약 첫 줄(3줄 clamp)과 장부 두 칸. 장부 기간 칸은 `gbPeriodChip(start, end, highlights.today, true)`로 그린다: null(마감 모름)이면 이 칸을 빼고 반응 칸만 전체 폭, 있으면 첫 줄 `range`("10/30(금) → 11/13(금)", 시작 모르면 "~ 11/13(금)", 끝났으면 "… 끝남"), 둘째 줄은 `dday`가 있을 때만 "마감까지 {dday}". 반응 칸 토막 [조회 {formatViews}][댓글 {n}] / "{formatShortDate(postedAt)} 올라옴". 세로 칸·작은 칸 메타 토막 [조회][댓글][날짜], IC_REPLIES만 고른 기준을 앞에 [댓글][조회][날짜](시안 d). 날짜 토막은 `formatShortDate(postedAt)`(후보는 본문을 읽은 글이라 postedAt이 있다).
  - 마감 띠(`deadline = true`): 사진 없음, 주황 바탕 + 키캡 무늬(시안), 배지는 흰 공동구매 배지(시안 `.mc-badge-gb-on`) + `photoChipLabel` 태그, 키커 "곧 끝나는 공동구매", 제목, 메타 토막 [`gbPeriodChip(start, end, highlights.today).range`][조회][댓글], 오른쪽 "{formatMonthDay(end,true)} 마감" + 큰 `dday`(`D-n`/`D-DAY`, 같은 today로 고른 글이라 늘 있다). `deadline = false`면 c 자리에 사진 칸.
  - 큰 칸 이미지는 `loading="eager"` + `fetchPriority="high"`(Next 16에서 `priority`는 쓰지 않는다), 나머지는 lazy. 모든 칸은 상세 링크(`postHref(id)`), 44px 이상.
- [ ] **Step 4: 가로 줄** — `BoardRail`(서버)이 카드 10개를 `RailCard`로 그려 `RailScroller`(클라이언트)의 children으로 넘긴다. 카드: 미디어(IC 4:5, 공동구매 3:2) + 칩(`photoChipLabel`, 공동구매는 `gbPeriodChip` → 기간 칩 + D-day 칩 또는 "끝남" 칩), 제목, 요약 첫 줄(두 줄 clamp, DONE일 때만), 메타 토막 [조회][댓글][날짜]. 날짜는 `postedAt`이 있으면 `formatShortDate(postedAt)`, 없으면(틀 글) `estimatedPostedAt`으로 "{formatShortDate}쯤"(기존 `formatPostedDate`의 "쯤" 규칙), 둘 다 없으면 날짜 토막을 뺀다. 공동구매 칩의 오늘은 Step 1의 `kstDateKey(new Date())`. 이미지 없는 글은 기존 문구("이미지 없음" / "열면 불러와요").
  - `RailScroller`: 가로 스크롤(`scroll-snap`), 데스크톱만 좌우 화살표(`railEdges`로 비활성, `scrollBy` 한 화면, `prefers-reduced-motion`이면 `behavior: "auto"`), 진행 막대는 scroll·resize 때 `railProgress`. effect가 시작할 때 DOM에서 다시 계산(cacheComponents의 Activity로 숨겨졌다 다시 보일 때). 태블릿·모바일은 화살표를 숨기고 다음 카드가 엿보이는 폭(시안 공식).
- [ ] **Step 5: 스타일** — 시안 `docs/design/main-c-responsive.html`의 `.mc-*` CSS를 CSS 모듈로 옮긴다(값은 `.superpowers/work/recon/mockup.md` 참고). 컨테이너 쿼리 대신 위 미디어 쿼리. 메인 본문 폭 1120px·좌우 24px(모바일 16px), 날짜 줄은 헤더 바로 아래(레이아웃 `.main`의 위 여백을 메인에서만 상쇄), 가로 줄은 오른쪽 끝까지(음수 margin). 시안 메모·폭별 메모·영문 로고·로그인 자리·푸터·"1–4 / 10" 쪽수는 넣지 않는다. 글꼴은 사이트 글꼴(IBM Plex Sans KR·Mono) 그대로.
- [ ] **Step 6: 정리** — `BoardColumn.tsx`·`.module.css` 삭제, 안 쓰이게 된 export 정리(내 변경이 만든 것만), `page.module.css`에서 메인 전용 옛 클래스 삭제(`.message` 유지).
- [ ] **Step 7: 확인** — `npm test && npm run lint && npm run build` PASS. 이어서 화면 확인:
  - 띄우기 전(V9): 이 백엔드가 뜨면 Flyway가 아직 커밋하지 않은 V9를 사용자 로컬 DB(`localhost:5432/keycheck`)에 적용한다. V9 칸 이름·타입을 확정한 뒤 띄운다. 그 뒤 V9를 고치면 사용자의 백엔드가 checksum 불일치로 뜨지 않으므로, 로컬 DB에서 V9 칸 넷을 지우고(`alter table post drop column …`) `delete from flyway_schema_history where version = '9'` 한 뒤 다시 띄워 적용한다.
  - 백엔드: 이 작업 트리 코드로 **`KEYCHECK_SUMMARIZER=fake KEYCHECK_SUMMARY_ENABLED=false KEYCHECK_CRAWL_ENABLED=false KEYCHECK_PREFETCH_ENABLED=false KEYCHECK_THUMBNAIL_ENABLED=false SERVER_PORT=8081 ./gradlew bootRun`**. 요약 작업을 꺼서 가짜 요약이 로컬 DB에 쓰이지 않게 하고, 수집·미리 읽기·축소본도 명시해 끈다(`backend/.env`는 config import라 `application.yml` 기본값을 덮을 수 있다. OS 환경 변수 `KEYCHECK_*`는 `.env`의 `CRAWL_ENABLED=…`·`keycheck.….enabled=…` 어느 쪽보다도 우선한다. `.env`는 읽지 않는다). 8080·3000 서버는 오케스트레이터가 꺼 두었다.
  - 프론트: `NEXT_PUBLIC_API_BASE_URL=http://localhost:8081 npm run dev -- -p 3001`.
  - 데이터 준비: V9는 백필이 없고 가짜 요약기는 기간을 뽑지 않아 로컬 DB에 기간이 하나도 없다(그대로면 마감 띠·칩·장부가 그려지지 않는다). `docker compose exec -T postgres psql -U keycheck keycheck`로 넣고 바꾼 id를 적어 둔다: `curl -s http://localhost:8081/api/posts/highlights`의 FEATURE 글에 시작 오늘−7·마감 오늘+8(장부 두 칸, "마감까지 D-8"), FEATURE가 아닌 대표 이미지 있는 GB 글 하나에 시작 null·마감 오늘(마감 띠 D-DAY, "~ 오늘" 메타), 공동구매 줄 10개 안의 다른 두 글에 지난 기간(끝남 칩)과 시작 null·마감 오늘+20("~ " 칩 + D-20).
  - 찍기: Playwright(`.superpowers/work/tools`)로 1280·820·390 폭 스크린샷을 찍어 시안과 비교한다. 확인 목록: 마감 띠(흰 공동구매 배지, 기간·조회·댓글 메타, "{날짜} 마감", D-DAY), 큰 칸 장부 두 칸, 가로 줄 기간 칩·D-day 칩·끝남 칩·시작 모르는 칩, 작은 칸 키커("최근" 없음)와 IC_REPLIES 메타 순서. 콘솔 오류 0, 가로 넘침 0, 깨진 이미지 0(외부 호스트 실패 제외), 44px 미만 터치 대상 0.
  - 느려지지 않음(설계 3.5): `curl -sN http://localhost:3001/`로 받은 HTML에서 헤더가 먼저 오고, 날짜 줄·벤토·IC 줄·공동구매 줄이 각자 Suspense 경계(자리 표시)로 나뉘어 있는지 본다(가로 줄이 하이라이트 응답을 기다리지 않는 구조).
  - 빈 상태: 백엔드를 같은 명령에 `KEYCHECK_COLLECT_SINCE_TOPIC_ID=999999999`를 더해 다시 띄워, 하이라이트·두 줄이 모두 빈 화면을 390·1280 폭으로 찍는다("아직 고를 글이 없어요", "아직 수집된 글이 없어요", 날짜 줄 숫자 0, 레이아웃 깨짐 없음).
  - 되돌리기: 적어 둔 id 중 `gb_dates_extracted = false`인 행의 `gb_start_date`·`gb_end_date`를 NULL로 되돌린다(`gb_dates_extracted`는 FALSE 그대로라, 되돌리기를 잊어도 나중에 Claude로 다시 요약할 때 덮인다). 띄운 서버를 모두 끈다.

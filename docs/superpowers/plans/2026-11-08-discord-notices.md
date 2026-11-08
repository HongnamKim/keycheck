# 국내 판매 소식(디스코드 공지) 구현 계획

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Swagkeys·GeonWorks 디스코드 공지를 봇으로 모아 원문·사진을 보관하고, Claude로 판매 건을 나눠 요약해 `/sales` 목록·상세와 메인(가로 줄·하이라이트 띠·큰 칸 무작위)에 보여 준다.

**Architecture:** 백엔드 `notice` 도메인이 수집(디스코드 REST) → 저장(원문 JSON, 첨부 행) → 보관(R2 세 크기) → 요약(판매 건) 순서의 작업 세 개와 `/api/sales` API를 가진다. 지난 공지는 같은 저장·보관 경로를 쓰는 일회성 가져오기(`notice-import` 프로필)로 넣는다. 프론트는 기존 IC·공동구매 화면 부품을 따라 국내 판매 목록·상세·메인 부품을 만들고, 큰 칸 무작위는 정적 메인을 지키려고 그리기 전 인라인 스크립트로 고른다.

**Tech Stack:** Spring Boot 4.1 / Java 21 / JPA·Flyway(PostgreSQL 17) / RestClient / anthropic-java 3.4.0(beta messages) / AWS S3 SDK(R2) / Next.js 16.4 App Router(cacheComponents) / vitest

**Spec:** `docs/superpowers/specs/2026-11-08-discord-notices-design.md` (구현자는 계획과 함께 읽는다)

## Global Constraints

- 브랜치 `feature/discord-notices`. **커밋은 사용자가 말할 때만** 한다(CLAUDE.md). 각 태스크 끝에서 새 파일은 `git add`만 하고, 커밋 단계는 사용자가 태스크별 커밋을 허락한 경우에만 실행한다. 커밋 제목은 한국어 conventional commit, 본문은 `## 주요 내용` / `## 세부 내용`.
- 백엔드 스타일: Lombok, DTO·값 타입은 `record` 대신 `private final` + `@Getter`, 엔티티는 `@Getter @NoArgsConstructor(access = PROTECTED)` + 정적 `create`, 들여쓰기 2칸, 테스트는 `@DisplayName`(한국어) + AssertJ. Spring MVC 동기 방식(WebFlux 금지). 패키지는 `com.keycheck.notice.{config,client,domain,dto,repository,service,scheduler,controller,runner}`, 테스트도 같은 구조.
- jsonb는 `@JdbcTypeCode(SqlTypes.JSON)`, Jackson은 3(`tools.jackson.databind`, 어노테이션은 `com.fasterxml.jackson.annotation`).
- 자동 테스트는 실제 디스코드·Anthropic·R2를 부르지 않는다(가짜 서버·`FakeThumbStorage`·`@MockitoBean`). 테스트 데이터는 손으로 쓴 JSON. 공지 원문·이미지는 저장소에 넣지 않는다(`.superpowers/work`만).
- 비밀값(봇 토큰 등)을 셸 명령·인자·채팅에 싣지 않는다. 설정은 `${DISCORD_BOT_TOKEN:}`처럼 placeholder로 읽는다(`backend/.env`는 properties로 읽혀 대문자 relaxed binding이 안 된다).
- 시간: 판매 시각·날짜 계산은 한국 시각(Asia/Seoul). 프론트는 브라우저에서 날짜를 읽지 않고 서버가 받은 시각(`fetchedAt`/`renderedAt`)을 기준으로 한다.
- 화면 문구는 해요체, "해외" 금지. 판매처 표시 이름은 `Swagkeys`, `GeonWorks`.
- 프론트 Next 코드를 쓰기 전 `frontend/node_modules/next/dist/docs/`를 확인한다(`frontend/AGENTS.md`). 인라인 스크립트는 `01-app/02-guides/preventing-flash-before-hydration.md`를 따른다.
- 확인 명령: 백엔드 `cd backend && ./gradlew test`, 프론트 `cd frontend && npm test && npm run lint && npm run build`.
- 비용이 드는 실행(샘플 공지 Claude 요약)은 건수·예상 비용을 사용자에게 확인받은 뒤에만.

## Review Focus

1. **mp4 같은 이미지 아닌 첨부** — `ThumbnailMaker`가 거절한다. 첨부만 `FAILED`가 되고 공지는 요약되며 화면에 깨진 사진이 없어야 한다. (Task 5 테스트 `이미지가 아닌 첨부는 실패로 끝내고 다른 첨부는 보관한다`)
2. **디스코드가 `after` 조회 결과를 최신순으로 준다** — 오래된 순으로 처리하지 않으면 사진만 있는 메시지가 엉뚱한 공지에 묶인다. (Task 3 테스트 `after 조회 결과를 오래된 순으로 돌려준다`)
3. **R2 공개 주소가 비어 있는 개발 환경** — 원문 JSON 첨부 주소는 비우고, 요약 요청에 이미지 블록을 넣지 않으며, API 사진 주소는 null이고 화면은 "이미지 없음"이어야 한다. (Task 6 테스트 `공개 주소가 없으면 이미지 블록을 보내지 않는다`)
4. **AI가 이상한 시각을 준다**(`24:00`, `25:10`, 날짜 없이 시각만) — 그 값만 null로 두고 판매 건은 살린다. (Task 6 테스트 `읽을 수 없는 시각은 null로 둔다`)
5. **JS가 꺼졌거나 봇인 방문** — 메인은 geekhack 큰 칸 + 띠 1순위가 깜빡임 없이 보인다. (Task 15 테스트 `봇 UA면 고르지 않는다`, `UA가 비면 고르지 않는다`)

---

## 파일 구조

**백엔드 (`backend/src/main/java/com/keycheck/`)**
- `summary/service/DailySummaryBudget.java` — 하루 요약 한도 세기(기존 `SummaryWorker`에서 꺼냄). geekhack·공지 요약이 같이 쓴다.
- `notice/config/NoticeProperties.java`, `DiscordConfig.java`, `NoticeImportProperties.java`
- `notice/client/DiscordClient.java`, `DiscordRateLimitedException.java`, `DiscordUnavailableException.java`
- `notice/dto/DiscordMessage.java`, `DiscordAttachment.java` — 디스코드 메시지 JSON(원문 노드 포함)
- `notice/domain/Notice.java`, `NoticeAttachment.java`, `NoticeChannelCursor.java`, `SaleItem.java`, `SaleLink.java`, 열거형 `NoticeOrigin`, `ArchiveStatus`, `SaleCategory`, `SaleType`, `SalePhase`, 도우미 `Snowflakes.java`, `NoticeKeys.java`
- `notice/repository/NoticeRepository.java`, `NoticeAttachmentRepository.java`, `NoticeChannelCursorRepository.java`, `SaleItemRepository.java`
- `notice/service/NoticeIngestService.java` — 메시지 하나 저장(중복·원래 id·딸린 메시지·원문 주소 바꾸기)
- `notice/service/NoticeCollector.java` — 채널별 이어 받기
- `notice/service/NoticeArchiver.java` — 첨부 → R2 세 크기
- `notice/service/NoticeSummarizer.java`, `FakeNoticeSummarizer.java`, `ClaudeNoticeSummarizer.java`, `notice/dto/NoticeSource.java`, `NoticeSummaryResult.java`, `SaleItemDraft.java`
- `notice/service/NoticeSummaryWorker.java`
- `notice/service/SaleQueryService.java`, `SaleHighlightService.java`, `notice/controller/SaleController.java`, `notice/dto/Sale*Response.java`
- `notice/service/NoticeImporter.java`, `notice/runner/NoticeImportRunner.java`, `resources/application-notice-import.yml`
- `notice/scheduler/NoticeCollectScheduler.java`, `NoticeArchiveScheduler.java`, `NoticeSummaryScheduler.java`
- `resources/db/migration/V13__notice.sql`

**프론트 (`frontend/src/`)**
- `lib/api.ts`(타입·함수 추가), `lib/cachedApi.ts`(추가), `lib/saleQuery.ts`, `lib/saleStatus.ts`, `lib/homePick.ts`, `lib/feed.ts`(제네릭으로), `lib/bot.ts`(`CRAWLER_UA` 내보내기), `lib/seo.ts`(사이트맵·메타데이터), `lib/query.ts`(`salesNavCurrent`)
- `components/sale/SaleBadge.tsx`, `SaleCard.tsx`, `SaleFeed.tsx`, `SaleFilterBar.tsx`, `SalesListPage.tsx` (+ `.module.css`)
- `app/sales/page.tsx`, `app/sales/[id]/page.tsx`
- `components/home/SaleRail.tsx`, `SaleRailCard.tsx`, `SaleBand.tsx`, `SaleFeatureCell.tsx`, `InlineScript.tsx` (+ css), `HighlightBento.tsx`(수정), `app/page.tsx`(수정)
- `components/BoardNav.tsx`(수정), `app/sitemap.ts`(수정)

---

## 백엔드

### Task 1: 하루 요약 한도를 같이 쓰는 부품으로 꺼내기

**Files:**
- Create: `backend/src/main/java/com/keycheck/summary/service/DailySummaryBudget.java`
- Modify: `backend/src/main/java/com/keycheck/summary/service/SummaryWorker.java` (필드 `countedDate`·`countedToday`·`limitWarned`, 메서드 `countToday`·`uncountToday` 제거, 생성자에 budget 추가)
- Test: `backend/src/test/java/com/keycheck/summary/service/DailySummaryBudgetTest.java`, `SummaryWorkerTest.java`(생성자 호출만 바꿈)

**Interfaces:**
- Produces:
  - `@Component public class DailySummaryBudget` — 생성자 `DailySummaryBudget(SummaryProperties properties, Clock clock)`
  - `public synchronized boolean tryAcquire()` — 오늘(Asia/Seoul) 센 수가 `dailyLimit` 미만이면 하나 세고 true. 처음 닿을 때 한 번만 경고 로그(기존 문구 그대로).
  - `public synchronized void release()` — 센 하나를 되돌린다(0 밑으로 내려가지 않음).
  - `public Instant nextReset()` — 다음 한국 0시.
  - `SummaryWorker` 생성자: `SummaryWorker(PostRepository, Summarizer, SummaryProperties, CollectProperties, DailySummaryBudget, Clock)`

- [ ] **Step 1: 실패하는 테스트 작성** — `DailySummaryBudgetTest`(Spring 없이, `MutableClock`):
  - `한도까지만 센다`: `dailyLimit=2` → `tryAcquire()` true, true, false.
  - `한국 날짜가 바뀌면 다시 센다`: `2026-11-01T14:59:59Z`(23:59:59 KST)에 한도 소진 → 1초 뒤 `tryAcquire()` true.
  - `되돌리면 하나 더 셀 수 있다`: 소진 후 `release()` → true.
  - `nextReset은 다음 한국 0시`: `2026-11-01T00:00:00Z` → `2026-11-01T15:00:00Z`.
- [ ] **Step 2: 실행해 실패 확인** — `cd backend && ./gradlew test --tests '*DailySummaryBudgetTest'` → 컴파일 실패.
- [ ] **Step 3: `DailySummaryBudget` 구현 후 `SummaryWorker`가 쓰게 바꾸기** — 한도에 닿으면 `pausedUntil = budget.nextReset()`, `AnthropicServiceException`이면 `budget.release()`. 동작은 그대로. `SummaryWorkerTest`의 `new SummaryWorker(...)` 호출에 `new DailySummaryBudget(properties, clock)`을 넣는다(같은 `SummaryProperties`·`clock`).
- [ ] **Step 4: 실행해 통과 확인** — `./gradlew test --tests '*DailySummaryBudgetTest' --tests '*SummaryWorkerTest' --tests '*SummarySchedulerTest' --tests '*OpsConfigTest'` → PASS(기존 한도 테스트 `stopsAtDailyLimit`, `apiErrorResponseIsNotCountedTowardDailyLimit`, `fakeSummarizerIgnoresDailyLimit` 포함).
- [ ] **Step 5: 새 파일 `git add`** (커밋은 Global Constraints 참고)

### Task 2: 공지 테이블·엔티티·저장소

**Files:**
- Create: `backend/src/main/resources/db/migration/V13__notice.sql`
- Create: `notice/domain/{Notice,NoticeAttachment,NoticeChannelCursor,SaleItem,SaleLink,NoticeOrigin,ArchiveStatus,SaleCategory,SaleType,SalePhase,Snowflakes,NoticeKeys}.java`
- Create: `notice/repository/{NoticeRepository,NoticeAttachmentRepository,NoticeChannelCursorRepository,SaleItemRepository}.java`
- Test: `backend/src/test/java/com/keycheck/notice/repository/NoticeRepositoryTest.java`, `notice/domain/SnowflakesTest.java`

**V13 내용** (각 열 위에 한국어 `--` 주석, V1 스타일):
- `notice`: `id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY`, `source_message_id BIGINT NOT NULL UNIQUE`(원래 서버 메시지 id), `channel_message_id BIGINT`(팔로우 채널 사본 id, 봇만), `channel_id BIGINT NOT NULL`, `vendor VARCHAR(20) NOT NULL`, `origin VARCHAR(20) NOT NULL`(BOT/BACKFILL), `posted_at TIMESTAMPTZ NOT NULL`, `content TEXT NOT NULL`, `has_text BOOLEAN NOT NULL`, `raw JSONB NOT NULL`, `parent_id BIGINT REFERENCES notice (id)`, `summary_status VARCHAR(20) NOT NULL DEFAULT 'PENDING'`, `summarized_by VARCHAR(100)`, `summarized_at TIMESTAMPTZ`, `created_at`/`updated_at TIMESTAMPTZ`. 인덱스 `(summary_status, posted_at)`, `(vendor, posted_at DESC)`.
- `notice_attachment`: `id BIGINT PRIMARY KEY`(디스코드 첨부 id), `notice_id BIGINT NOT NULL REFERENCES notice (id) ON DELETE CASCADE`, `position INTEGER NOT NULL`, `filename TEXT NOT NULL`, `source_url TEXT`, `archive_status VARCHAR(20) NOT NULL DEFAULT 'PENDING'`, `failures INTEGER NOT NULL DEFAULT 0`. 부분 인덱스 `WHERE archive_status = 'PENDING'`.
- `notice_channel_cursor`: `channel_id BIGINT PRIMARY KEY`, `last_message_id BIGINT NOT NULL`.
- `sale_item`: `id` 식별자, `notice_id ... ON DELETE CASCADE`, `position INTEGER NOT NULL`, `vendor VARCHAR(20) NOT NULL`, `posted_at TIMESTAMPTZ NOT NULL`, `product_name VARCHAR(200) NOT NULL`, `category`/`sale_type`/`phase VARCHAR(20) NOT NULL`, `start_date DATE`, `start_time TIME`, `end_date DATE`, `end_time TIME`, `shipping_note TEXT`, `summary_lines JSONB NOT NULL`, `links JSONB NOT NULL`, `image_ids JSONB NOT NULL`(첨부 id 목록, 첫째가 대표), `hidden BOOLEAN NOT NULL DEFAULT FALSE`, `created_at TIMESTAMPTZ`. 인덱스 `(hidden, posted_at DESC, id DESC)`.

**Interfaces:**
- Produces:
  - 열거형: `NoticeOrigin { BOT, BACKFILL }`, `ArchiveStatus { PENDING, DONE, FAILED }`, `SaleCategory { KEYBOARD, KEYCAP, SWITCH, PARTS, OTHER }`, `SaleType { SALE, PREORDER, RESTOCK, DROP, RAFFLE }`, `SalePhase { ANNOUNCE, OPEN, CHANGE }`. 판매처는 기존 `com.keycheck.post.domain.KoreanVendor`, 요약 상태는 기존 `com.keycheck.post.domain.SummaryStatus`.
  - `Snowflakes.time(long id): Instant` — `(id >> 22) + 1420070400000` ms.
  - `NoticeKeys.archive(long attachmentId)` → `"notices/<id>.webp"`, `large(id)` → `"notices/<id>-1280.webp"`, `thumb(id)` → `"notices/<id>-640.webp"`.
  - `Notice.create(long sourceMessageId, Long channelMessageId, long channelId, KoreanVendor vendor, NoticeOrigin origin, Instant postedAt, String content, boolean hasText, String rawJson)`; `Notice.addAttachment(long attachmentId, int position, String filename, String sourceUrl): NoticeAttachment`; 세터 `setParent(Notice)`, `setSummaryStatus(SummaryStatus)`. `raw`는 `String` + `@JdbcTypeCode(SqlTypes.JSON) @Column(columnDefinition = "jsonb")`. 첨부는 `@OneToMany(mappedBy="notice", cascade=ALL, orphanRemoval=true) @OrderBy("position")`.
  - `SaleItem.create(Notice notice, int position, SaleItemDraft draft)` — 판매처·공지 시각은 공지에서 복사. `SaleLink { String text; String url; }`(값 타입, `@JsonCreator`).
  - `NoticeRepository`: `boolean existsBySourceMessageId(long)`, `Optional<Notice> findLatestTextNotice(KoreanVendor vendor, Instant from, Instant before)`(parent null·has_text true·`from <= posted_at < before`, 최신 1건), `List<Notice> findSummaryTargets(Instant postedBefore, Limit)`(아래 Task 7), `@Modifying markSummarized(Long id, SummaryStatus status, String summarizedBy, Instant at)`, `@Modifying updateSummaryStatus(Long id, SummaryStatus status)`.
  - `NoticeAttachmentRepository`: `List<NoticeAttachment> findArchiveTargets(int maxFailures, Limit)`(PENDING·failures < max, notice.posted_at 오래된 순), `@Modifying incrementFailures(Long id)`, `decrementFailures(Long id)`, `markArchived(Long id, ArchiveStatus status)`, `updateSourceUrl(Long id, String url)`, `long countPendingForNotice(Long noticeId)`(자기와 딸린 메시지 첨부 중 PENDING 수).
  - `NoticeChannelCursorRepository extends JpaRepository<NoticeChannelCursor, Long>`; `NoticeChannelCursor.create(long channelId, long lastMessageId)`, `advance(long messageId)`(더 클 때만).
  - `SaleItemRepository`: `@Modifying deleteByNoticeId(Long)`, 목록·상세·하이라이트 조회는 Task 8에서 추가.

- [ ] **Step 1: 실패하는 테스트 작성**
  - `SnowflakesTest.원래_메시지_id로_작성_시각을_구한다`: `Snowflakes.time(1568198861929971713L)` == `Instant.parse("2026-11-06T09:42:24.410Z")`.
  - `NoticeRepositoryTest`(`@DataJpaTest(properties="spring.jpa.hibernate.ddl-auto=validate") @Import(TestcontainersConfiguration.class)`):
    - `원래_메시지_id는_한_번만_저장된다`: 같은 `source_message_id` 두 번 저장 → `DataIntegrityViolationException`.
    - `가장_최근_글_공지를_찾는다`: 같은 판매처 글 공지 2건(10:00, 10:30)과 딸린 메시지 1건(10:31, parent 있음), 다른 판매처 1건(10:40) → `findLatestTextNotice(SWAGKEYS, 10:00-24h, 10:45)`가 10:30 공지.
    - `24시간_밖의_공지는_찾지_않는다`: 공지 어제 09:00, 기준 오늘 09:30 → empty.
    - `첨부는_순서대로_읽힌다`: position 1, 0으로 추가 → 다시 읽으면 0, 1.
    - `판매_건은_공지를_지우면_같이_지워진다`.
- [ ] **Step 2: 실행해 실패 확인** — `./gradlew test --tests '*NoticeRepositoryTest' --tests '*SnowflakesTest'` → FAIL.
- [ ] **Step 3: 마이그레이션·엔티티·저장소 구현** (`ddl-auto: validate`가 통과해야 한다)
- [ ] **Step 4: 실행해 통과 확인** — 같은 명령 → PASS, 그리고 `./gradlew test --tests '*KeycheckApplicationTests'` PASS.
- [ ] **Step 5: 새 파일 `git add`**

### Task 3: 디스코드 클라이언트와 설정

**Files:**
- Create: `notice/config/NoticeProperties.java`, `notice/config/DiscordConfig.java`, `notice/client/{DiscordClient,DiscordRateLimitedException,DiscordUnavailableException}.java`, `notice/dto/{DiscordMessage,DiscordAttachment}.java`
- Modify: `backend/src/main/resources/application.yml`, `backend/build.gradle`(test systemProperty), `backend/src/test/resources/application.properties`, `backend/.env.example`, `deploy/keycheck.env.example`
- Test: `notice/client/DiscordClientTest.java`, `notice/dto/DiscordMessageTest.java`

**설정 키** (`application.yml`, `keycheck.notice`):
```yaml
notice:
  enabled: ${NOTICE_ENABLED:false}          # 수집·보관 작업
  interval: 5m
  bot-token: ${DISCORD_BOT_TOKEN:}
  api-base-url: https://discord.com/api/v10
  user-agent: "DiscordBot (https://github.com/HongnamKim/keycheck, 0.1)"
  channels:
    SWAGKEYS: ${SWAGKEY_CHANNEL_ID:}
    GEONWORKS: ${GEONWORKS_CHANNEL_ID:}
  attach-window: 24h
  archive:
    interval: 3s
  summary:
    enabled: ${NOTICE_SUMMARY_ENABLED:false}  # 로컬에서 가져온 샘플을 확인 없이 Claude로 요약하지 않게 기본은 끔. 운영 env에서 true
    interval: 5s
    wait: 10m
  import:
    dir: ${NOTICE_IMPORT_DIR:}
    vendor: ${NOTICE_IMPORT_VENDOR:}
```
`spring.task.scheduling.pool.size`를 4 → 7로(주석에 공지 작업 셋 추가). 테스트 두 곳에 `keycheck.notice.enabled=false`, `keycheck.notice.summary.enabled=false`, `keycheck.notice.bot-token=`, `keycheck.notice.channels.SWAGKEYS=`, `keycheck.notice.channels.GEONWORKS=`. 예시 파일 두 곳에 `DISCORD_BOT_TOKEN`, `SWAGKEY_CHANNEL_ID`, `GEONWORKS_CHANNEL_ID`, `NOTICE_ENABLED`, `NOTICE_SUMMARY_ENABLED` 이름만(비밀값은 비우고, 기존 주석 스타일). `deploy/keycheck.env.example`에는 `NOTICE_ENABLED=true`, `NOTICE_SUMMARY_ENABLED=true`를 적는다.

**Interfaces:**
- Produces:
  - `@ConfigurationProperties("keycheck.notice") NoticeProperties` — `String botToken, String apiBaseUrl, String userAgent, Map<KoreanVendor, String> channels, Duration attachWindow, Summary summary`(중첩 `Summary { Duration wait; }`). `Map<KoreanVendor, String> configuredChannels()`(빈 값 뺌).
  - `DiscordConfig`: `@Bean DiscordClient discordClient(NoticeProperties)` — `GeekhackConfig`처럼 `JdkClientHttpRequestFactory`(연결·읽기 10초), `baseUrl`, `User-Agent`, `Authorization: Bot <token>` 기본 헤더.
  - `DiscordClient(RestClient restClient)`:
    - `List<DiscordMessage> messagesAfter(String channelId, String afterId)` — `GET /channels/{id}/messages?limit=100[&after=]`. **id 오름차순으로 정렬해** 돌려준다.
    - `Optional<DiscordMessage> message(String channelId, String messageId)` — 404면 empty.
    - 429 → `DiscordRateLimitedException(Duration retryAfter)`(본문 `retry_after` 초, 없으면 `Retry-After` 헤더, 둘 다 없으면 60초). 그 밖의 4xx/5xx·연결 실패 → `DiscordUnavailableException`(메시지에 토큰을 싣지 않는다).
  - `DiscordMessage.from(JsonNode node)` — `String id, int type, String channelId, String content, int flags, String referencedMessageId, List<DiscordAttachment> attachments, JsonNode raw`. `boolean isCrosspost()`(flags & 2), `String sourceMessageId()`(크로스포스트면 참조 id, 아니면 자기 id). `DiscordAttachment { String id; String filename; String url; }`.

- [ ] **Step 1: 실패하는 테스트 작성** (`MockRestServiceServer.bindTo(builder)`, `GeekhackClientTest` 방식, 응답 JSON은 손으로 쓴다)
  - `after가_없으면_최근_100개를_요청한다`: 요청 경로 `/channels/111/messages?limit=100`, 헤더 `Authorization: Bot test-token`.
  - `after 조회 결과를 오래된 순으로 돌려준다`: 응답 id `[30, 20, 10]` → 결과 id `[10, 20, 30]`.
  - `429면 기다릴 시간을 담아 던진다`: 본문 `{"retry_after": 1.5}` → `DiscordRateLimitedException.getRetryAfter()` == 1500ms.
  - `메시지가_없으면_empty`: 404 → `Optional.empty()`.
  - `서버_오류는_사용할_수_없음으로_던진다`: 503 → `DiscordUnavailableException`, 메시지에 `test-token`이 없다.
  - `DiscordMessageTest.크로스포스트는_원래_메시지_id를_쓴다`: flags 2, `message_reference.message_id = "999"` → `sourceMessageId()` == `"999"`; flags 0 → 자기 id.
- [ ] **Step 2: 실패 확인** — `./gradlew test --tests '*DiscordClientTest' --tests '*DiscordMessageTest'`
- [ ] **Step 3: 구현**
- [ ] **Step 4: 통과 확인** — 같은 명령, 그리고 `./gradlew test --tests '*OpsConfigTest' --tests '*KeycheckApplicationTests'`(설정 바인딩)
- [ ] **Step 5: `git add`**

### Task 4: 메시지 저장(중복·원래 id·딸린 메시지)과 채널별 수집

**Files:**
- Create: `notice/service/NoticeIngestService.java`, `notice/service/NoticeCollector.java`, `notice/scheduler/NoticeCollectScheduler.java`
- Test: `notice/service/NoticeIngestServiceTest.java`, `notice/service/NoticeCollectorTest.java`, `notice/scheduler/NoticeCollectSchedulerTest.java`

**Interfaces:**
- Consumes: Task 2 저장소·엔티티, Task 3 `DiscordMessage`·`DiscordClient`·`NoticeProperties`, 기존 `ThumbUrls.url(String key)`.
- Produces:
  - `NoticeIngestService.ingest(KoreanVendor vendor, long channelId, DiscordMessage message, NoticeOrigin origin): Optional<Notice>` (`@Transactional`):
    1. `type != 0` → empty.
    2. `sourceMessageId()`가 이미 있으면 empty.
    3. `postedAt = Snowflakes.time(sourceId)`.
    4. 본문 글자 판정: `content`에서 역할·사용자 멘션(`<@&123>`, `<@123>`, 줄 앞 `@KR` 같은 `@\S+`)과 공백을 지우고 남은 글자가 있으면 `hasText`.
    5. 원문 JSON의 각 첨부 `url`·`proxy_url`을 `thumbUrls.url(NoticeKeys.archive(id))`로 바꾼다(공개 주소가 없으면 빈 문자열). 디스코드 원래 주소는 첨부 행 `source_url`에(가져오기는 null).
    6. `hasText`가 아니고 첨부가 있으면 `findLatestTextNotice(vendor, postedAt - attachWindow, postedAt)`의 공지를 parent로, 자기 상태는 `DONE`. parent가 `DONE`이면 `PENDING`으로 되돌린다(다시 요약).
    7. `hasText`도 첨부도 없으면 `DONE`. 그 밖에는 `PENDING`.
    8. 봇 크로스포스트면 `channelMessageId = message.id`.
  - `NoticeCollector.collect()` — `configuredChannels()`마다: 커서(없으면 null)부터 `messagesAfter` 반복, 메시지마다 `ingest(vendor, channelId, m, BOT)`, 배치마다 커서를 받은 최대 id로 전진, 100개 미만이면 끝. `DiscordRateLimitedException` → `pausedUntil = now + retryAfter`로 이번 실행을 멈춤(다음 실행은 그때까지 건너뜀). `DiscordUnavailableException` → 경고 로그 후 다음 채널. 토큰이 비면 경고 한 번 후 아무것도 안 함.
  - `NoticeCollectScheduler` — `@ConditionalOnProperty(name="keycheck.notice.enabled", havingValue="true")`, `@Scheduled(fixedDelayString="${keycheck.notice.interval}") run()` → `collector.collect()`.

- [ ] **Step 1: 실패하는 테스트 작성** (`@SpringBootTest @Import(TestcontainersConfiguration.class)`, `MutableClock`, 메시지는 `DiscordMessage.from(JsonMapper...readTree("{...}"))`로 손으로 만든다)
  - `NoticeIngestServiceTest`:
    - `크로스포스트는_원래_id로_저장하고_사본_id를_남긴다`
    - `이미_있는_원래_id는_건너뛴다`: 가져오기(BACKFILL)로 저장된 id를 봇이 다시 받으면 empty, 행 1개.
    - `시스템_메시지는_버린다`: type 12 → empty.
    - `원문_첨부_주소를_R2_주소로_바꾼다`: 저장된 `raw`의 `attachments[0].url` == `https://img.test/notices/<첨부id>.webp`, 첨부 행 `source_url`은 디스코드 주소.
    - `사진만_있는_메시지는_앞_공지에_묶는다`: 글 공지(10:00) 뒤 `@KR`만 있고 첨부 2개인 메시지(10:01) → parent = 글 공지, 상태 DONE.
    - `요약이_끝난_공지에_사진이_오면_다시_요약_대상이_된다`: 글 공지 DONE → 딸린 메시지 저장 후 글 공지 PENDING.
    - `24시간이_넘으면_묶지_않는다`: 묶을 공지 없음 → parent null, 상태 DONE.
    - `본문도_첨부도_없으면_요약하지_않는다`: 상태 DONE.
  - `NoticeCollectorTest`(`@MockitoBean DiscordClient`):
    - `채널마다_커서부터_이어_받고_커서를_전진한다`: 1차 호출 after=null → [1,2]; 2차 실행 after="2".
    - `100개가_차면_이어서_더_받는다`
    - `429면_기다릴_시간까지_조회하지_않는다`: 1차 실행 429(30초) → 10초 뒤 `collect()`는 클라이언트를 부르지 않음, 31초 뒤는 부름.
    - `채널_id가_비면_그_판매처는_건너뛴다`
  - `NoticeCollectSchedulerTest`: `keycheck.notice.enabled=true, keycheck.notice.interval=100ms`, `@MockitoBean NoticeCollector` → `verify(timeout(5000).atLeast(2)).collect()`. 기본 설정에서는 빈이 없다(`KeycheckApplicationTests`에 단언 추가).
- [ ] **Step 2: 실패 확인** — `./gradlew test --tests '*NoticeIngestServiceTest' --tests '*NoticeCollectorTest' --tests '*NoticeCollectSchedulerTest'`
- [ ] **Step 3: 구현**
- [ ] **Step 4: 통과 확인** — 같은 명령
- [ ] **Step 5: `git add`**

### Task 5: 첨부를 R2에 세 크기로 보관

**Files:**
- Create: `notice/service/NoticeArchiver.java`, `notice/scheduler/NoticeArchiveScheduler.java`
- Modify: `image/service/ThumbnailMaker.java` — `public static final int ARCHIVE_WIDTH = 2048;` 상수만 추가
- Test: `notice/service/NoticeArchiverTest.java`, `notice/scheduler/NoticeArchiveSchedulerTest.java`

**Interfaces:**
- Consumes: 기존 `ImageDownloader.download(String): byte[]`(`ImageFetchException`), `ThumbnailMaker.make(byte[], int)`(`UnreadableImageException`), `ThumbStorage.put(String, byte[])`(`StorageUnavailableException`), `DiscordClient.message(...)`, Task 2 저장소.
- Produces:
  - `@Service @Conditional(StorageConfig.Configured.class) NoticeArchiver`:
    - `static final int MAX_FAILURES = 3;`, `static final Duration ERROR_PAUSE = Duration.ofMinutes(1);`
    - `public boolean archiveNext()` — 대상 1건(`findArchiveTargets(MAX_FAILURES, Limit.of(1))`). 실패 수를 먼저 올리고(`ThumbnailWorker`와 같은 이유), `source_url`로 받아 `store`. `ImageFetchException`이고 봇 공지(`channel_message_id` 있음)면 `discordClient.message(channelId, channelMessageId)`로 같은 첨부 id의 새 주소를 찾아 `updateSourceUrl`(다음 실행에서 다시 받음). 실패 수가 `MAX_FAILURES`에 닿으면 `FAILED`. 저장소 장애·그 밖의 예외는 1분 쉬고 실패 수를 되돌린다.
    - `public void store(long attachmentId, byte[] original)` — 2048 → 1280 → 640 순서로 `make` + `put`(`NoticeKeys`), 끝나면 `DONE`. `UnreadableImageException` → `FAILED`(되돌리지 않음). 가져오기도 이 메서드를 쓴다.
  - `NoticeArchiveScheduler` — `@ConditionalOnProperty("keycheck.notice.enabled")`, `ObjectProvider<NoticeArchiver>`(없으면 R2 설정 경고, `ThumbnailScheduler` 방식), `@Scheduled(fixedDelayString="${keycheck.notice.archive.interval}")`.

- [ ] **Step 1: 실패하는 테스트 작성** (`ThumbnailWorkerTest` 방식: `@MockitoBean ImageDownloader`, `@MockitoBean DiscordClient`, `new FakeThumbStorage()`, 작업은 직접 생성, 이미지 `png(w,h)`)
  - `세_크기를_올리고_보관_완료로_표시한다`: 3000×2000 PNG → 키 `notices/7.webp`(폭 2048), `notices/7-1280.webp`(1280), `notices/7-640.webp`(640), 상태 DONE.
  - `작은_원본은_키우지_않는다`: 500×400 → 세 파일 모두 폭 500.
  - `이미지가 아닌 첨부는 실패로 끝내고 다른 첨부는 보관한다`: mp4 바이트 첨부 FAILED, 같은 공지의 PNG 첨부 DONE.
  - `받기에_실패하면_봇_공지는_새_주소로_바꿔_다시_시도한다`: 1차 `ImageFetchException` → `updateSourceUrl` 호출, 2차 새 주소로 성공 → DONE.
  - `세_번_실패하면_실패로_끝낸다`
  - `저장소_장애는_실패로_세지_않고_쉰다`: `storage.unavailable = true` → failures 0, `archiveNext()`는 1분 동안 false.
  - `NoticeArchiveSchedulerTest`: 저장소 설정을 넣은 컨텍스트에서 `@MockitoBean NoticeArchiver`가 불린다.
- [ ] **Step 2: 실패 확인** — `./gradlew test --tests '*NoticeArchiverTest' --tests '*NoticeArchiveSchedulerTest'`
- [ ] **Step 3: 구현**
- [ ] **Step 4: 통과 확인** — 같은 명령, 그리고 `./gradlew test --tests '*ThumbnailMakerTest'`
- [ ] **Step 5: `git add`**

### Task 6: 공지 요약기(판매 건 나누기)

**Files:**
- Create: `notice/service/{NoticeSummarizer,FakeNoticeSummarizer,ClaudeNoticeSummarizer}.java`, `notice/dto/{NoticeSource,NoticeSummaryResult,SaleItemDraft,NoticeImage}.java`
- Test: `notice/service/ClaudeNoticeSummarizerTest.java`, `notice/service/FakeNoticeSummarizerTest.java`

**Interfaces:**
- Consumes: 기존 `AnthropicClient` 빈·`ClaudeProperties`(같은 모델 설정), `com.keycheck.summary.FakeAnthropicServer`(테스트).
- Produces:
  - `NoticeSource(KoreanVendor vendor, Instant postedAt, String content, List<SaleLink> links, List<NoticeImage> images)` — `NoticeImage(long attachmentId, String filename, String thumbUrl)`(thumbUrl은 640 공개 주소, 없으면 null). 링크는 본문 마크다운 `[글자](주소)`와 맨 주소에서 뽑아 Task 7이 채운다.
  - `SaleItemDraft` — `String productName, SaleCategory category, SaleType saleType, SalePhase phase, LocalDate startDate, LocalTime startTime, LocalDate endDate, LocalTime endTime, String shippingNote, List<String> summaryLines, List<SaleLink> links, List<Long> imageIds`.
  - `NoticeSummaryResult(List<SaleItemDraft> items, String summarizedBy)`.
  - `interface NoticeSummarizer { NoticeSummaryResult summarize(NoticeSource source); }`
  - `FakeNoticeSummarizer`(`keycheck.summarizer=fake`, matchIfMissing) — `ID = "fake"`. 글자가 있으면 판매 건 1개: 첫 `#` 제목 줄(없으면 첫 줄, 80자)을 이름, `KEYBOARD`·`SALE`·`OPEN`, 요약 `["[가짜 요약] " + 이름]`, 첫 링크, 첫 사진. 글자가 없으면 0건. (로컬 화면 확인용)
  - `ClaudeNoticeSummarizer`(`keycheck.summarizer=claude`):
    - 요청은 `ClaudeSummarizer.params`와 같은 모양(`model`, `maxTokens`, `system`, `outputConfig(effort, FORMAT)`, fallbacks 규칙). 사용자 메시지는 `addUserMessageOfContentBlockParams`: 텍스트 블록 하나 + 사진마다 `BetaContentBlockParam.ofImage(BetaUrlImageSource.of(thumbUrl))`(thumbUrl이 null이면 넣지 않음, 최대 20장).
    - 텍스트: `<vendor>`, `<posted_at>`(한국 시각 `2026-11-06 18:42 (금) KST`), `<body>`(본문의 `<t:초(:형식)?>`를 `2026-11-06 19:30 (금) KST`로 바꿈, 형식 R도 같은 절대 시각으로), `<links>`(`글자 | 주소`, 최대 150), `<images>`(`순번 | 첨부id | 파일 이름`).
    - 시스템 프롬프트에 정할 것(스펙 3.1·3.4): 판매 소식만(예약판매·드랍·판매 시작·래플·살 수 있는 재입고, 그 예고·일정 변경), 빼는 것 목록, 제품 범위와 `OTHER`, 판매 건 단위로 나누기, 링크는 `<links>`에서만, 사진은 `<images>` 순번이 아닌 **첨부 id**로, "금일·차주 화요일"은 공지 시각 기준, 날짜만 있으면 시각 null, 요약은 해요체 1~3줄, "해외" 금지.
    - `FORMAT` 스키마: `{"items": [{productName, category(enum), saleType(enum), phase(enum), startDate(date|null), startTime("HH:mm"|null), endDate, endTime, shippingNote(string|null), summary(array of string), links([{text,url}]), imageIds(array of string)}]}`, 모든 속성 required, `additionalProperties:false`.
    - 검증(`static NoticeSummaryResult toResult(BetaMessage, List<SaleLink> sentLinks, Set<Long> sentImageIds)`): 거절·미완료는 `IllegalStateException`(기존 문구 방식). 이름 공백·80자 초과 → 그 건 버림. 링크는 보낸 링크와 `urlKey`(strip, 끝 `/` 하나 제거)로 맞춰 원래 주소로, 없으면 버림. 사진 id는 보낸 id만. 날짜는 `LocalDate.parse`, 시각은 `HH:mm`이고 0~23시·0~59분만, 아니면 null(날짜 없이 시각만 있으면 시각도 null). 요약 줄은 공백 빼고 최대 3줄. 판매 건 최대 20.
    - 사용 토큰 로그(기존 방식).

- [ ] **Step 1: 실패하는 테스트 작성** (`ClaudeSummarizerTest` 방식: `FakeAnthropicServer`, `response(stopReason, text)` 도우미를 이 테스트에 같은 모양으로 둔다)
  - `시간_표기를_한국_시각_글자로_바꿔_보낸다`: 본문 `구매는 <t:1793961000:F> 부터` → 보낸 텍스트에 `2026-11-06 19:30 (금) KST`, `<t:` 없음.
  - `사진을_주소_블록으로_보낸다`: 보낸 `messages[0].content`에 `{"type":"image","source":{"type":"url","url":"https://img.test/notices/7-640.webp"}}`.
  - `공개 주소가 없으면 이미지 블록을 보내지 않는다`: thumbUrl null → image 블록 0개, `<images>`에는 파일 이름이 있다.
  - `보내지_않은_링크는_버린다`, `끝_슬래시만_다른_링크는_보낸_주소로_맞춘다`
  - `보내지_않은_사진_id는_버린다`
  - `읽을 수 없는 시각은 null로 둔다`: `startTime "24:00"` → null, `startDate`는 유지; 날짜 null·시각 `"19:30"` → 둘 다 null.
  - `이름이_빈_판매_건은_버린다`, `요약은_세_줄까지`
  - `판매가_아닌_공지는_0건`: 응답 `{"items":[]}` → 빈 목록.
  - `거절하면_예외`: stop_reason refusal → `IllegalStateException`.
  - `스키마의_열거값`: 보낸 `output_config.format.schema`의 `category.enum` == `[KEYBOARD, KEYCAP, SWITCH, PARTS, OTHER]`.
  - `FakeNoticeSummarizerTest.첫_제목을_이름으로_한_건을_만든다`, `글자가_없으면_0건`.
- [ ] **Step 2: 실패 확인** — `./gradlew test --tests '*NoticeSummarizerTest'`
- [ ] **Step 3: 구현**
- [ ] **Step 4: 통과 확인** — 같은 명령
- [ ] **Step 5: `git add`**

### Task 7: 공지 요약 작업

**Files:**
- Create: `notice/service/NoticeSummaryWorker.java`, `notice/scheduler/NoticeSummaryScheduler.java`
- Modify: `notice/repository/NoticeRepository.java`(`findSummaryTargets`), `SaleItemRepository`
- Test: `notice/service/NoticeSummaryWorkerTest.java`, `notice/scheduler/NoticeSummarySchedulerTest.java`

**Interfaces:**
- Consumes: Task 1 `DailySummaryBudget`, Task 6 `NoticeSummarizer`, `NoticeSource`, `ThumbUrls`, `NoticeProperties.getSummary().getWait()`.
- Produces:
  - `NoticeRepository.findSummaryTargets(Instant postedBefore, Limit limit)` — `summary_status = PENDING`, `parent is null`, `has_text = true`, `posted_at <= :postedBefore`, 그리고 자기·딸린 메시지 첨부 중 `PENDING`이 없는 것. 오래된 순.
  - `NoticeSummaryWorker.summarizeNext(): boolean` — `SummaryWorker`와 같은 틀: 쉬는 중이면 false; 대상 1건(`postedBefore = now - wait`); 가짜 요약기가 아니면 `budget.tryAcquire()`(실패하면 `pausedUntil = budget.nextReset()`); 원문 → `NoticeSource`(본문 링크 추출, 자기 첨부 뒤에 딸린 메시지 첨부를 시간 순으로, `DONE` 첨부만 사진으로); 요약기 호출은 트랜잭션 밖; 성공하면 한 트랜잭션에서 `deleteByNoticeId` → 판매 건 저장(position 0..) → `markSummarized(id, DONE, summarizedBy, now)`. 요약기 예외: API 사용 불가(`AnthropicException`이지만 `AnthropicInvalidDataException` 아님)면 상태 유지·1분 쉼·`AnthropicServiceException`이면 `budget.release()`; 그 밖에는 `FAILED`.
  - 링크 추출: `static List<SaleLink> links(String content)` — `[글자](http..)`는 `{글자, 주소}`, 그 밖의 맨 `http(s)://` 주소는 `{주소, 주소}`, 같은 주소는 한 번, 본문 순서.
  - `NoticeSummaryScheduler` — `@ConditionalOnProperty("keycheck.notice.summary.enabled")`, `@Scheduled(fixedDelayString="${keycheck.notice.summary.interval}")`.

- [ ] **Step 1: 실패하는 테스트 작성** (`SummaryWorkerTest` 방식: `@SpringBootTest @Import(TestcontainersConfiguration.class)`, 기록하는 가짜 요약기, `MutableClock`, 작업은 직접 생성)
  - `올라온_지_10분이_안_된_공지는_기다린다`: 09:55에 올라온 공지, 지금 10:00 → false; 10:05 → 요약.
  - `사진_보관이_끝나지_않은_공지는_기다린다`: 첨부 PENDING → false; DONE/FAILED로 바꾸면 요약.
  - `딸린_메시지의_사진을_시간_순으로_함께_보낸다`: 보낸 `images`의 첨부 id 순서 == [글 공지 첨부들..., 딸린 메시지 첨부들...], FAILED 첨부는 빠짐.
  - `본문_링크를_뽑아_보낸다`: `[판매 페이지](https://www.swagkey.kr/shop_view?idx=1912)`와 맨 주소 → 두 링크.
  - `다시_요약하면_판매_건을_새로_만든다`: 기존 2건 → 새 결과 1건이면 1건만 남는다.
  - `요약_실패는_FAILED`, `API를_쓸_수_없으면_상태를_두고_쉰다`
  - `하루_한도를_geekhack_요약과_같이_쓴다`: `dailyLimit=1`인 같은 `DailySummaryBudget`을 `SummaryWorker`와 공유 → geekhack 글 1건 요약 뒤 공지 작업은 false.
  - `가짜_요약기는_한도를_세지_않는다`
  - `NoticeSummarySchedulerTest`: `keycheck.notice.summary.enabled=true, interval=100ms`.
- [ ] **Step 2: 실패 확인** — `./gradlew test --tests '*NoticeSummaryWorkerTest' --tests '*NoticeSummarySchedulerTest'`
- [ ] **Step 3: 구현**
- [ ] **Step 4: 통과 확인** — 같은 명령
- [ ] **Step 5: `git add`**

### Task 8: 판매 API(목록·상세·하이라이트·사이트맵)

**Files:**
- Create: `notice/controller/SaleController.java`, `notice/service/{SaleQueryService,SaleHighlightService}.java`, `notice/dto/{SaleCardResponse,SaleListResponse,SaleDetailResponse,SaleImageResponse,SaleLinkResponse,SaleHighlightResponse,SalePickResponse,SaleSitemapItemResponse}.java`
- Modify: `notice/repository/SaleItemRepository.java`, `backend/src/test/java/com/keycheck/OpenApiTest.java`
- Test: `notice/controller/SaleApiTest.java`, `notice/service/SaleHighlightServiceTest.java`

**Interfaces:**
- Produces (JSON 필드 이름이 프론트 타입이 된다):
  - `SaleCardResponse` — `Long id, KoreanVendor vendor, String productName, SaleCategory category, SaleType saleType, SalePhase phase, LocalDate startDate, String startTime("HH:mm"|null), LocalDate endDate, String endTime, String summaryFirstLine, String thumbUrl(640), String largeUrl(1280), Instant postedAt`. `static from(SaleItem, ThumbUrls)` — 사진은 `imageIds` 첫째이고 그 첨부가 `DONE`일 때만, 아니면 null.
  - `SaleListResponse { List<SaleCardResponse> items; String nextCursor; boolean hasMore; }` — `GET /api/sales?vendor=all|swagkeys|geonworks&category=all|keyboard|keycap|switch|parts&cursor=`. 정렬 `posted_at desc, position asc, id desc`, 쪽 크기 20, 커서 `"<postedAt 밀리초>_<id>"`(잘못된 값·필터는 400, 기존 `"허용되지 않는 값: "`·`"허용되지 않는 cursor: "` 문구). 숨김·`OTHER`·요약 전(`summary_status != DONE`인 공지의 건) 제외.
  - `SaleDetailResponse` — 카드 필드 + `List<String> summaryLines, String shippingNote, List<SaleLinkResponse> links{text,url}, List<SaleImageResponse> images{url(2048), largeUrl(1280), thumbUrl(640)}`(DONE 첨부만, imageIds 순서). `GET /api/sales/{id}`, 없거나 숨김·OTHER면 404.
  - `SaleHighlightResponse { List<SalePickResponse> picks; }`, `SalePickResponse { String reason(UPCOMING|CLOSING|RECENT); SaleCardResponse sale; }` — `GET /api/sales/highlights`.
  - `SaleSitemapItemResponse { Long id; Instant lastModified(공지 summarized_at); }` — `GET /api/sales/sitemap`.
  - `SaleHighlightService.pick(Instant now): List<SalePickResponse>` — 스펙 3.8: 대상은 숨김 아님·OTHER 아님·대표 사진 DONE. 한국 시각으로 시작·마감 순간을 계산(날짜만이면 시작은 그날 00:00, 마감은 23:59:59; 같은 날 안에서는 시각 아는 건을 먼저). 1) 시작이 `(now, now+7일]` 이른 순 2) 판매 중(시작 ≤ now 또는 시작 없음+`phase != ANNOUNCE`)이고 마감이 `(now, now+7일]` 이른 순 3) `posted_at`이 14일 안, 최근 순. 차례로 채워 최대 2개, 같은 건 한 번.

- [ ] **Step 1: 실패하는 테스트 작성** (`PostSitemapApiTest` 방식: `@SpringBootTest @AutoConfigureMockMvc @Import({TestcontainersConfiguration.class, ClockConfig.class})`, 판매 건은 저장소로 직접 넣는다)
  - `SaleApiTest`:
    - `최근_공지_순으로_20개씩_준다`: 25건 → 1쪽 20건, `hasMore` true, 2쪽 5건.
    - `판매처와_분류로_거른다`: `vendor=geonworks&category=keycap`.
    - `숨김_OTHER_요약_전은_내보내지_않는다`
    - `잘못된_필터와_커서는_400`
    - `상세는_사진과_링크를_준다`: `images[0].url` == `https://img.test/notices/7.webp`, `largeUrl` `-1280`, `thumbUrl` `-640`; FAILED 첨부는 없음.
    - `없는_판매_건은_404`
    - `사이트맵은_id와_요약_시각`
  - `SaleHighlightServiceTest`(지금 `2026-11-08T06:00:00Z` = 15:00 KST):
    - `곧_시작이_먼저`: 오늘 20:30 시작 건 + 3일 뒤 마감 판매 중 건 → reason [UPCOMING, CLOSING].
    - `곧_시작이_둘이면_이른_순`
    - `날짜만_아는_시작은_그날_시각_아는_건_뒤`: 내일(날짜만) vs 내일 10:00 → 10:00 건이 먼저.
    - `없으면_최근_소식`: 시각 없는 건만 → RECENT, 15일 전 건은 제외.
    - `사진_없는_건과_OTHER와_숨김은_고르지_않는다`
    - `같은_건은_한_번만`
  - `OpenApiTest`에 `$.paths['/api/sales'].get.summary` 존재 단언.
- [ ] **Step 2: 실패 확인** — `./gradlew test --tests '*SaleApiTest' --tests '*SaleHighlightServiceTest' --tests '*OpenApiTest'`
- [ ] **Step 3: 구현** (컨트롤러는 `@Tag`/`@Operation`/`@Parameter(schema=@Schema(allowableValues=...))` 기존 방식)
- [ ] **Step 4: 통과 확인** — 같은 명령
- [ ] **Step 5: `git add`**

### Task 9: 지난 공지 가져오기(일회성)와 운영 문서

**Files:**
- Create: `notice/config/NoticeImportProperties.java`, `notice/service/NoticeImporter.java`, `notice/runner/NoticeImportRunner.java`, `backend/src/main/resources/application-notice-import.yml`
- Modify: `README.md`(로컬 실행 환경변수 표에 새 이름 다섯 개, R2 `notices/` 접두어, 자동 테스트가 디스코드를 부르지 않는다는 줄, 가져오기 절차), `deploy/README.md`(운영 가져오기 절차)
- Test: `notice/service/NoticeImporterTest.java`

**Interfaces:**
- Consumes: Task 4 `NoticeIngestService.ingest(...)`, Task 5 `NoticeArchiver.store(long, byte[])`, Task 3 `DiscordMessage.from(JsonNode)`.
- Produces:
  - `@ConfigurationProperties("keycheck.notice.import") NoticeImportProperties { String dir; String vendor; }`
  - `NoticeImporter.importDir(Path dir, KoreanVendor vendor): ImportReport` — `dir/messages.json`(Task 범위 밖 스크립트가 만든 디스코드 API 모양 목록: `id, type, channel_id, timestamp, content, attachments[{id, filename, url, local_file}]`)을 id 오름차순으로: `ingest(vendor, channelId, message, BACKFILL)`(첨부 `source_url`은 null) → 첨부마다 `dir/attachments/<local_file>`이 있으면 `store(id, bytes)`, 없으면 `FAILED`. 이미 있는 id는 건너뜀. `ImportReport { int imported; int skipped; int attachmentsStored; int attachmentsFailed; }`.
  - `NoticeImportRunner` — `@Component @Profile("notice-import") implements ApplicationRunner`: 설정이 비면 `IllegalStateException`, 끝나면 보고를 `log.info`로(원문 내용은 찍지 않는다). `NoticeArchiver`가 없으면(R2 미설정) 시작하지 않고 예외.
  - `application-notice-import.yml`: `spring.main.web-application-type: none`, `banner-mode: off`, `keycheck.crawl.enabled/prefetch.enabled/thumbnail.enabled/summary.enabled/notice.enabled/notice.summary.enabled: false`(서버 env_file의 `CRAWL_ENABLED=true` 등이 있어도 꺼지는지 확인 — placeholder보다 프로필 파일 값이 이긴다).

- [ ] **Step 1: 실패하는 테스트 작성** (`@SpringBootTest` + 저장소 설정 속성(가짜 엔드포인트) + `@MockitoBean ThumbStorage` 또는 `FakeThumbStorage` 빈 덮어쓰기, `@TempDir`에 손으로 쓴 `messages.json`과 `png()` 파일)
  - `오래된_순으로_넣고_첨부를_보관한다`: 메시지 2개(첨부 1개씩) → notice 2행 `BACKFILL`, 첨부 DONE, 키 세 개씩.
  - `사진만_있는_메시지는_앞_공지에_묶인다`(수집과 같은 규칙)
  - `이미_있는_id는_건너뛴다`: 두 번 실행 → 두 번째 `skipped == 2`.
  - `파일이_없는_첨부는_실패로_둔다`
  - 프로필 확인: `@SpringBootTest(properties="spring.profiles.active=notice-import")` 컨텍스트에서 `CrawlScheduler`, `SummaryScheduler`, `NoticeCollectScheduler`, `NoticeSummaryScheduler` 빈이 없다(`CRAWL_ENABLED=true` 시스템 속성을 줘도).
- [ ] **Step 2: 실패 확인** — `./gradlew test --tests '*NoticeImporterTest'`
- [ ] **Step 3: 구현과 문서** — 운영 절차(deploy/README): 타이머 멈춤(`sudo systemctl stop keycheck-update.timer`) → 묶음을 `/opt/keycheck/notice-import/<vendor>/`로 복사(다른 사용자도 읽기 가능) → **2GB 서버라 `app`을 잠깐 멈추고**(`sudo docker compose stop app`) → `sudo docker compose run --rm -e SPRING_PROFILES_ACTIVE=notice-import -e NOTICE_IMPORT_DIR=/import -e NOTICE_IMPORT_VENDOR=SWAGKEYS -v /opt/keycheck/notice-import/swagkeys:/import:ro app` → `app` 시작 → 타이머 다시 켬. (Dockerfile ENTRYPOINT가 인자를 버리므로 설정은 `-e`로만.) 로컬: `NOTICE_IMPORT_DIR=... NOTICE_IMPORT_VENDOR=SWAGKEYS ./gradlew bootRun --args='--spring.profiles.active=notice-import'`.
- [ ] **Step 4: 통과 확인** — `./gradlew test` 전체 PASS
- [ ] **Step 5: `git add`**

### Task 9b: 사진 크기 저장과 넓은 자리용 2048 주소 (2026-11-08 시안 확인 뒤 추가)

**Files:**
- Create: `backend/src/main/resources/db/migration/V14__notice_attachment_size.sql` (`notice_attachment`에 `width INTEGER`, `height INTEGER`, 둘 다 NULL 허용, 한국어 주석)
- Modify: `image/service/ThumbnailMaker.java`(원본 크기 읽기), `notice/domain/NoticeAttachment.java`, `notice/repository/NoticeAttachmentRepository.java`, `notice/service/NoticeArchiver.java`, `notice/dto/SaleCardResponse.java`(+ 상세 응답이 카드 필드를 쓰면 함께), `notice/service/SaleHighlightService.java`, `frontend/src/lib/api.ts`(`SaleCard.imageUrl`)
- Test: `ThumbnailMakerTest`, `NoticeArchiverTest`, `SaleApiTest`, `SaleHighlightServiceTest`

**Interfaces:**
- Produces:
  - `ThumbnailMaker.originalSize(byte[] original): ImageSize`(`ImageSize { int width; int height; }`, `private final` + `@Getter`) — 디코드하지 않고 읽기 도구의 크기를 읽고, JPEG EXIF 방향이 5~8(90°/270° 회전)이면 폭·높이를 바꾼다. 읽을 수 없으면 `UnreadableImageException`.
  - `NoticeArchiver.store(...)`는 세 크기를 올린 뒤 `DONE`과 함께 원본 폭·높이를 저장한다(`markArchived`와 같은 트랜잭션 또는 하나의 업데이트).
  - `SaleCardResponse`에 `String imageUrl`(대표 첨부가 DONE이면 `NoticeKeys.archive` 주소, 아니면 null) 추가. 프론트 `SaleCard`에 `imageUrl: string | null`.
  - `SaleHighlightService`: 대상 조건에 "대표 첨부의 `width`가 1024 이상"(NULL이면 제외)을 더한다. `static final int MIN_HIGHLIGHT_WIDTH = 1024;`

- [ ] **Step 1: 실패하는 테스트 작성**
  - `ThumbnailMakerTest.원본_크기를_읽는다`: 3000×2000 PNG → 3000, 2000. EXIF 방향 6인 JPEG가 기존 테스트 도구로 만들어지면 폭·높이가 바뀌는지도.
  - `NoticeArchiverTest.보관하면_원본_크기를_저장한다`: 3000×2000 → width 3000, height 2000.
  - `SaleApiTest`: 카드 `imageUrl` == `https://img.test/notices/7.webp`, 대표 첨부가 FAILED면 null.
  - `SaleHighlightServiceTest`: 대표 사진 폭 800 → 고르지 않음, 1024 → 고름, NULL → 고르지 않음.
- [ ] **Step 2: 실패 확인** — `cd backend && ./gradlew test --tests '*ThumbnailMakerTest' --tests '*NoticeArchiverTest' --tests '*SaleApiTest' --tests '*SaleHighlightServiceTest'`
- [ ] **Step 3: 구현**
- [ ] **Step 4: 통과 확인** — 같은 명령과 `./gradlew test` 전체, `cd frontend && npm test && npm run lint`
- [ ] **Step 5: 커밋**

---

## 프론트

### Task 10: 시안 확인(코드 없음, 사용자 확인 필요)

**결과(2026-11-08, 시안 v4 — `.superpowers/brainstorm/49794-1794108726/content/sales-screens-v4.html`):**
- 목록 설명은 "Swagkeys·GeonWorks 디스코드 공지에서 판매 소식만 골라 정리했어요."
- 상세 사진 모음에는 그 판매 건으로 분류된 사진만, 2장 이상일 때만. 위 큰 사진은 `imageUrl`(2048), 없으면 `largeUrl`.
- 띠는 밝은 카드(A): 왼쪽 3:2 사진(데스크톱 폭 약 420px, `imageUrl`, `loading="lazy"`), 오른쪽 키커(강조색 고정폭 글꼴)·이름(22px 굵게)·배지 + `판매처 · 구분 · 판매 시각`, 오른쪽 끝 "자세히 →". 흰 바탕·테두리·모서리 14~16px. 모바일에서는 사진 위, 글 아래로 쌓는다.
- 큰 칸(국내 판매)은 `imageUrl`을 쓰고, 배지 줄에 상태 배지와 "국내 판매" 칩, 키커·이름·요약 첫 줄, 장부 두 칸("판매 시작"/마감 시각, "판매처 · 구분").
- 가로 줄·목록 카드는 시안 그대로(640).

- [ ] **Step 1:** 시각 도구(brainstorming visual companion, `.superpowers/brainstorm/`)에 기존 색·글꼴·배지 크기로 시안을 띄운다: (a) `/sales` 목록(제목·설명·판매처 칩·분류 칩·카드 격자, 카드는 확정된 A안), (b) `/sales/[id]` 상세(사진, 배지·판매처·구분, 이름, 요약, 정보 표, 구매 링크 버튼, 사진 모음, 공지 시각 글자), (c) 메인 하이라이트 아래 국내 판매 띠, (d) 큰 칸이 국내 판매일 때의 모양(판매처·구분·판매 시각이 geekhack 반응 자리에), (e) 메인 "03 국내 판매" 가로 줄 카드. 샘플은 `.superpowers/work/discord/backfill/swagkeys/`의 실제 공지·사진을 쓴다(시안 폴더 안에서만, 저장소에 넣지 않음).
- [ ] **Step 2:** 사용자 확인을 받고, 바뀐 점을 이 계획의 Task 12~15 해당 단계에 적는다. **확인 전에는 Task 12~15를 시작하지 않는다.**

### Task 11: API 타입·캐시·주소·상태 배지(순수 함수)

**Files:**
- Modify: `frontend/src/lib/api.ts`, `lib/cachedApi.ts`, `lib/bot.ts`(`export const CRAWLER_UA`), `lib/feed.ts`(제네릭), `lib/query.ts`(`salesNavCurrent`)
- Create: `frontend/src/lib/saleQuery.ts`, `lib/saleStatus.ts`
- Test: `lib/saleStatus.test.ts`, `lib/saleQuery.test.ts`, `lib/api.test.ts`(표 테스트에 새 함수 추가), `lib/feed.test.ts`(타입만 바뀌고 그대로 통과), `lib/query.test.ts`

**Interfaces:**
- Produces:
  - 타입(`api.ts`): `Vendor = "SWAGKEYS" | "GEONWORKS"`, `SaleCategory = "KEYBOARD"|"KEYCAP"|"SWITCH"|"PARTS"`, `SaleType = "SALE"|"PREORDER"|"RESTOCK"|"DROP"|"RAFFLE"`, `SalePhase = "ANNOUNCE"|"OPEN"|"CHANGE"`, `SaleCard`(Task 8 필드 그대로, 날짜는 `"YYYY-MM-DD"` 문자열, 시각 `"HH:mm"`), `SaleBatch { items: SaleCard[]; nextCursor: string | null; hasMore: boolean }`, `SaleDetail`, `SaleImage { url; largeUrl: string | null; thumbUrl: string | null }`, `SalePick { reason: "UPCOMING"|"CLOSING"|"RECENT"; sale: SaleCard }`, `SaleVendorFilter = "all"|"swagkeys"|"geonworks"`, `SaleCategoryFilter = "all"|"keyboard"|"keycap"|"switch"|"parts"`.
  - 함수(`api.ts`): `getSales(vendor, category, cursor?): Promise<SaleBatch>`, `getSale(id, signal?): Promise<SaleDetail | null>`(404 → null), `getSaleForPage = cache((id: number) => getSale(id))`, `getSaleHighlights(): Promise<SalePick[]>`, `getSaleSitemap(): Promise<SitemapPost[]>`.
  - `cachedApi.ts`: `getFirstSaleBatch(vendor, category): Promise<{ batch: SaleBatch; fetchedAt: string } | null>`(`getFirstBatch`와 같은 cacheLife), `getCachedSaleHighlights(): Promise<{ picks: SalePick[]; fetchedAt: string } | null>`(`getCachedHighlights`와 같은 cacheLife).
  - `feed.ts`: `FeedState<T extends { id: number }>`, `applyBatch<T>(state, batch: { items: T[]; nextCursor; hasMore })`, `startFeed<T>`, `fromSnapshot<T>` — 기존 호출부(`PostFeed`)는 타입 인자 추론으로 그대로.
  - `saleQuery.ts`: `SaleListQuery { vendor: SaleVendorFilter; category: SaleCategoryFilter }`, `parseSaleListQuery(params): SaleListQuery`(잘못된 값은 `all`), `saleListHref(q): string`(`/sales`, 기본값은 뺌, `?vendor=…&category=…`), `saleHref(id: number, from?: SaleListQuery | null): string`, `saleBackHref(from): string`(목록 또는 `/`), `parseSaleDetailQuery(params): SaleListQuery | null`.
  - `query.ts`: `salesNavCurrent(pathname: string): "page" | "true" | undefined` — `/sales` → `"page"`, `/sales/…` → `"true"`.
  - `saleStatus.ts`:
    - `type BadgeTone = "fill" | "line" | "soft" | "dash"`
    - `saleBadge(sale: Pick<SaleCard,"phase"|"startDate"|"startTime"|"endDate"|"endTime"|"postedAt">, now: Date): { label: string; tone: BadgeTone } | null` — 스펙 3.6 표와 날짜만 규칙.
    - `saleKindLabel(saleType: SaleType, phase: SalePhase): string` — 이름(판매·예약판매·재입고·드랍·래플), `ANNOUNCE` → `+" 예정"`, `CHANGE` → `+" 일정 변경"`, `SALE`+`OPEN` → `"판매 시작"`.
    - `saleWhenText(sale, now: Date): string` — 아래 테스트 문구.
    - `vendorLabel(v: Vendor): string` — `Swagkeys` / `GeonWorks`.
    - `formatKstTime(iso: string): string` — `"18:52"`(공지 시각 표시용, `format.ts`에 둔다).

- [ ] **Step 1: 실패하는 테스트 작성** (`TZ=UTC`, `now = new Date("2026-11-08T06:00:00Z")` = 11/8(일) 15:00 KST)
  - `saleBadge`(`it.each`):
    - 시작 `2026-11-08 20:30` → `{ "5시간 뒤 시작", fill }`; 시작 `2026-11-08 15:40` → `{ "40분 뒤 시작", fill }`
    - 시작 `2026-11-09 16:00`(25시간 뒤) → `{ "D-1 시작", soft }`; 시작 `2026-11-12 21:00` → `{ "D-4 시작", soft }`
    - 시작 날짜만 `2026-11-08` → `{ "오늘 시작", fill }`; `2026-11-12` → `{ "D-4 시작", soft }`
    - 판매 중, 마감 `2026-11-08 20:00` → `{ "5시간 뒤 마감", fill }`; 마감 `2026-11-10 08:00`(41시간) → `{ "D-2 마감", fill }`; 마감 `2026-11-12 23:59` → `{ "판매 중", line }`
    - 마감 날짜만 `2026-11-08` → `{ "오늘 마감", fill }`; `2026-11-09` → `{ "D-1 마감", fill }`; `2026-11-07` → `{ "판매 종료", soft }`
    - 마감 `2026-11-08 14:00` → `{ "판매 종료", soft }`
    - `OPEN`, 시작·마감 없음, 공지 3일 전 → `{ "판매 중", line }`; 공지 8일 전 → `null`
    - `ANNOUNCE`, 시작 없음 → `{ "일정 미정", dash }`
  - `saleKindLabel`: (RESTOCK, ANNOUNCE) → "재입고 예정", (SALE, OPEN) → "판매 시작", (PREORDER, CHANGE) → "예약판매 일정 변경", (RAFFLE, OPEN) → "래플".
  - `saleWhenText`: 미래 시작 시각 → "판매 시작 11/8(일) 20:30"; 날짜만 → "판매 시작 11/12(목)"; 판매 중·마감 있음 → "11/10(화) 23:59까지"; 판매 중·마감 없음 → "11/6(금) 19:30부터"; 끝남 → "11/7(토) 마감"; 일정 없음 → "판매 일정 미정".
  - `saleQuery`: `saleListHref({all, all})` → `/sales`, `{geonworks, keycap}` → `/sales?vendor=geonworks&category=keycap`; `parseSaleListQuery({vendor: "x"})` → `{all, all}`. 상세 주소는 목록에서 열었는지를 `list=1`로 남긴다: `saleHref(3)` → `/sales/3`(메인에서 연 경우), `saleHref(3, {all, all})` → `/sales/3?list=1`, `saleHref(3, {geonworks, all})` → `/sales/3?list=1&vendor=geonworks`; `parseSaleDetailQuery({list: "1", vendor: "geonworks"})` → `{geonworks, all}`, `list`가 없으면 null; `saleBackHref(null)` → `/`, `saleBackHref({geonworks, all})` → `/sales?vendor=geonworks`.
  - `salesNavCurrent`: `/sales` → "page", `/sales/12` → "true", `/ic` → undefined.
  - `api.test.ts` "백엔드 호출 함수" 표에 `getSales`, `getSale`, `getSaleHighlights`, `getSaleSitemap` 추가; `getSale` 404 → null.
- [ ] **Step 2: 실패 확인** — `cd frontend && npm test`
- [ ] **Step 3: 구현**
- [ ] **Step 4: 통과 확인** — `npm test && npm run lint`
- [ ] **Step 5: `git add`**

### Task 12: `/sales` 목록 페이지와 헤더 메뉴

**Files:**
- Create: `components/sale/{SaleBadge,SaleCard,SaleFeed,SaleFilterBar,SalesListPage}.tsx` + `.module.css`, `app/sales/page.tsx`
- Modify: `components/BoardNav.tsx`(세 번째 메뉴 "국내 판매"), `lib/format.ts`(필요하면 `SALE_LIST_INFO`)

**Interfaces:**
- Consumes: Task 11 전부, 기존 `RemoteImage`, `LoadFailed`, `FreshListLink`, `imageSources`, `PostFeed`/`FilterBar`/`BoardListPage`(구조를 따른다).
- Produces:
  - `SaleBadge({ badge }: { badge: { label: string; tone: BadgeTone } })` — `BoardBadge` 크기(12px, 1px 테두리, 999px), fill=강조색 채움, line=진한 테두리, soft=회색 테두리, dash=회색 점선.
  - `SaleCard({ sale, href, now, eager })` — 스펙 3.7 카드: 사진(640, 없으면 "이미지 없음"), 배지 줄(`saleBadge` + `vendorLabel · saleKindLabel`), 이름, 요약 첫 줄, `saleWhenText`, 공지 날짜(`formatShortDate(postedAt)`).
  - `SaleFeed({ query, initial, renderedAt })` — `PostFeed`와 같은 이어 받기·스냅숏(키 `saleListHref(query)`), 제네릭 `feed.ts` 사용.
  - `SaleFilterBar({ query })` — 판매처 칩(전체·Swagkeys·GeonWorks), 분류 칩(전체·키보드·키캡·스위치·부품). `FilterBar` CSS 규칙.
  - `app/sales/page.tsx` — `metadata`: 제목 "국내 판매", 설명 "Swagkeys·GeonWorks 디스코드 공지에서 판매 소식만 골라 한국어로 정리했어요.", canonical `/sales`, `openGraph: siteOpenGraph("/sales")`. 본문은 `BoardListPage`처럼 머리(바로) + Suspense 안 `getFirstSaleBatch` → 실패 시 `connection()` 후 `LoadFailed`.
  - `BoardNav` 메뉴 `[IC, 공동구매, 국내 판매]`, 국내 판매는 `salesNavCurrent(pathname)`로 표시. 모바일 세 칸은 기존 `flex: 1 1 0` 규칙 그대로.

- [ ] **Step 1:** Task 10에서 확정한 시안대로 구현(시안과 다른 점이 있으면 시안을 따른다).
- [ ] **Step 2: 확인** — `npm test && npm run lint && npm run build`(빌드 표에 `/sales`가 부분 prerender로 나온다), 로컬(`npm run dev`, 백엔드에 Task 17 샘플이 있으면)에서 `/sales`·필터·더 불러오기를 눈으로 본다.
- [ ] **Step 3: `git add`**

### Task 13: `/sales/[id]` 상세 페이지

**Files:**
- Create: `app/sales/[id]/page.tsx` + `page.module.css`
- Modify: `lib/seo.ts`(`saleMetadata(sale: SaleDetail): Metadata`), `lib/seo.test.ts`

**Interfaces:**
- Consumes: `getSaleForPage`, `parsePostId`(id 파싱 재사용), `parseSaleDetailQuery`, `saleBackHref`, 기존 `Gallery({ images: PostImage[] })`(사진을 `{ url: image.url, thumbUrl: image.thumbUrl }`로 넘김), `RemoteImage`, `LoadFailed`, `notFound()`.
- Produces:
  - `saleMetadata` — 제목 `productName`, 설명 요약 첫 줄(120자), canonical `/sales/{id}`, openGraph 사이트 기본 카드(`siteOpenGraph`).
  - 페이지 — 스펙 3.7 상세: 위 사진은 1280(fallback 2048), 배지·판매처·구분, `h1` 이름, "핵심 요약" 목록, 정보 `dl`(판매처, 구분, 판매 시작, 마감, 배송 일정 — 값이 없으면 그 줄 생략), 구매 링크 버튼들(`target="_blank" rel="noopener noreferrer"`, 글자는 링크 text), 사진 모음(`Gallery`, 별도 Suspense), "디스코드 공지 11/6(금) 18:52", 목록으로/메인으로.

- [ ] **Step 1: 실패하는 테스트 작성** — `seo.test.ts`: `saleMetadata`의 제목·canonical·설명 120자 자르기.
- [ ] **Step 2: 실패 확인** — `npm test`
- [ ] **Step 3: 구현**(시안대로)
- [ ] **Step 4: 확인** — `npm test && npm run lint && npm run build`, 없는 id는 not-found 화면.
- [ ] **Step 5: `git add`**

### Task 14: 메인 "03 국내 판매" 가로 줄

**Files:**
- Create: `components/home/SaleRail.tsx`, `SaleRailCard.tsx` + `.module.css`
- Modify: `app/page.tsx`(공동구매 줄 아래 `<SaleRail />`, 바로 가기 줄에 `국내 판매` → `#home-sales`, 소개 글을 "geekhack IC·공동구매 글과 국내 판매 소식에서 지금 볼 만한 것을 골랐어요. 아래로 내리면 목록이 이어져요."로), `page.module.css` 주석

**Interfaces:**
- Consumes: `getFirstSaleBatch("all","all")`, 기존 `RailTrack({ board, name, placeholder, children })`(버튼 위치용으로 `board="gb"`를 넘긴다 — 사진 비율 3/2와 같음, 주석으로 이유를 남김), `saleBadge`, `saleHref(id)`.
- Produces: `SaleRail()` — `BoardRail` 머리 구조(번호 "03", 제목 "국내 판매", 출처 "Swagkeys · GeonWorks 디스코드 공지", "새로 올라온 순", "전체 보기 →" `/sales`), 최근 10건, 실패·빈 상태 문구는 `BoardRail`과 같게("아직 모은 판매 소식이 없어요"). `SaleRailCard({ sale, now })` — 공동구매 카드 크기·사진 3/2, 사진 위 칩 줄에 상태 배지, 아래 이름·요약 첫 줄·`판매처 · 구분`.

- [ ] **Step 1:** 시안대로 구현
- [ ] **Step 2: 확인** — `npm test && npm run lint && npm run build`(빌드 표의 `/`가 여전히 `○`(정적) 1m 15m)
- [ ] **Step 3: `git add`**

### Task 15: 하이라이트 국내 판매 띠와 큰 칸 무작위

**Files:**
- Create: `lib/homePick.ts`, `lib/homePick.test.ts`, `components/home/InlineScript.tsx`, `SaleBand.tsx`, `SaleFeatureCell.tsx` + `.module.css`
- Modify: `components/home/HighlightBento.tsx`(+ `.module.css`)

**Interfaces:**
- Consumes: `getCachedHighlights`, `getCachedSaleHighlights`, `bentoLayout`, `BentoCell`, `saleBadge`, `saleKindLabel`, `saleWhenText`, `vendorLabel`, `CRAWLER_UA`, `HTML_LIMITED_BOT_UA_RE`.
- Produces:
  - `homePickScript(): string` — 즉시 실행 함수 글자: `document.currentScript.previousElementSibling`을 잡고, `navigator.userAgent`가 비었거나 `CRAWLER_UA`·`HTML_LIMITED_BOT_UA_RE`(`.source`, `i`)에 걸리면 아무것도 안 함, 아니면 `Math.random() < 0.5`일 때 그 요소의 `data-pick`을 `"kr"`로.
  - `homeVariants(slots, picks, layout): { krFeature: SalePick | null; bandGk: SalePick | null; bandKr: SalePick | null }` — 순수 함수: 벤토 배치이고 picks ≥ 1이면 `krFeature = picks[0]`, `bandKr = picks[1] ?? null`; 늘 `bandGk = picks[0] ?? null`. 격자 배치(칸 부족)면 `krFeature = null`.
  - `InlineScript({ code })` — Next 가이드의 `type={typeof window === "undefined" ? "text/javascript" : "text/plain"}` + `suppressHydrationWarning` + `dangerouslySetInnerHTML`.
  - `HighlightBento` — 두 데이터를 함께 받는다(하이라이트 실패면 기존 문구, 판매 실패면 판매 부분만 뺌). `krFeature`가 있으면 `<div className={styles.pick} data-pick="gk" suppressHydrationWarning>`로 감싸고: 벤토 큰 칸 자리에 `<div className={styles.gkOnly}>BentoCell FEATURE</div><div className={styles.krOnly}><SaleFeatureCell pick={krFeature} now={new Date(fetchedAt)} /></div>`(둘 다 `display: contents`, 숨길 쪽만 `display: none`), 벤토 아래 `<SaleBand>`를 gk용(bandGk)·kr용(bandKr, 없으면 안 그림) 각각 같은 방식으로. 감싼 요소 바로 뒤에 `<InlineScript code={homePickScript()} />`. `krFeature`가 없으면 감싸지 않고 띠(bandGk)만.
  - kr 큰 칸 이미지는 `loading="lazy"`(기본으로 숨겨져 받지 않음), geekhack 큰 칸은 지금처럼 eager.
  - `SaleBand({ pick, now })` — 시안대로 넓은 띠: 사진(1280), 키커(UPCOMING "곧 시작하는 국내 판매" / CLOSING "곧 마감하는 국내 판매" / RECENT "최근 국내 판매 소식"), 이름, 상태 배지, `vendorLabel · saleKindLabel`, `saleWhenText`. `saleHref(id)`로 링크.
  - `SaleFeatureCell({ pick, now })` — 기존 FEATURE 칸 모양(`BentoCell.module.css` 규칙 재사용 또는 같은 값), 반응 자리(Ledger)에 판매처·구분·판매 시각.

- [ ] **Step 1: 실패하는 테스트 작성** — `homePick.test.ts`(`new Function("document", "navigator", "Math", homePickScript())`에 가짜 객체를 넣어 실행):
  - `무작위가_0.5보다_작으면_kr을_고른다`: `random → 0.3` → 요소 `data-pick == "kr"`.
  - `무작위가_0.5_이상이면_그대로`: `0.7` → 바뀌지 않음.
  - `봇 UA면 고르지 않는다`: `Googlebot/2.1` → 바뀌지 않음(random 0.1이어도).
  - `UA가 비면 고르지 않는다`
  - `homeVariants`: picks 2개·벤토 → `{krFeature: p1, bandGk: p1, bandKr: p2}`; picks 1개 → `bandKr: null`; picks 0개 → 모두 null; 격자 배치 → `krFeature: null, bandGk: p1`.
- [ ] **Step 2: 실패 확인** — `npm test`
- [ ] **Step 3: 구현**(시안대로)
- [ ] **Step 4: 확인** — `npm test && npm run lint && npm run build`: 빌드 표의 `/`가 `○ / 1m 15m`(정적 유지). 로컬에서 새로고침을 여러 번 해 두 모양이 번갈아 나오고 깜빡임이 없는지, 개발자 도구로 JS를 끄면 geekhack 쪽만 보이는지 본다.
- [ ] **Step 5: `git add`**

### Task 16: 사이트맵

**Files:**
- Modify: `lib/seo.ts`(`sitemapEntries(posts: SitemapPost[], sales: SitemapPost[])` — `/sales`와 `/sales/{id}`(lastModified) 추가), `lib/seo.test.ts`, `app/sitemap.ts`(`Promise.all([getSitemapPosts(), getSaleSitemap()])`)

- [ ] **Step 1: 실패하는 테스트로 바꾸기** — `sitemapEntries([], [])` == `[SITE_URL, /ic, /gb, /sales]`; 판매 `{id: 3, lastModified: "2026-11-08T00:00:00Z"}` → `/sales/3` 항목.
- [ ] **Step 2: 실패 확인 → 구현 → 통과 확인** — `npm test && npm run lint && npm run build`
- [ ] **Step 3: `git add`**

---

## 마무리

### Task 17: 샘플 공지로 로컬 확인(사용자 결정: 6/1~11/6 Swagkeys 145개)

- [ ] **Step 1: 가져오기** — 로컬 PostgreSQL(`docker compose up -d`)과 개발 R2(`backend/.env`)로 `cd backend && NOTICE_IMPORT_DIR=../.superpowers/work/discord/backfill/swagkeys NOTICE_IMPORT_VENDOR=SWAGKEYS ./gradlew bootRun --args='--spring.profiles.active=notice-import'`. 기대: imported 145, 첨부 대부분 저장, mp4 1개는 실패가 정상.
- [ ] **Step 2: 가짜 요약으로 화면 확인** — `KEYCHECK_SUMMARY_ENABLED=false NOTICE_SUMMARY_ENABLED=true SUMMARIZER=fake ./gradlew bootRun`(geekhack 요약은 끄고 공지 요약만; 수집 `NOTICE_ENABLED`는 끈 채) → 프론트 `npm run dev`로 `/sales`, 상세, 메인 줄·띠·큰 칸을 본다. 가짜 요약 결과는 확인 뒤 `update notice set summary_status='PENDING' ...; delete from sale_item;`로 되돌린다.
- [ ] **Step 3: 비용 보고 후 Claude 요약** — 요약 대상 건수와 예상 비용(Sonnet 5.5, 공지당 입력 약 6천·출력 약 1천 토큰 기준 대략 $0.02 → 145건 약 $3)을 사용자에게 보고하고 **확인받은 뒤** `KEYCHECK_SUMMARY_ENABLED=false NOTICE_SUMMARY_ENABLED=true SUMMARIZER=claude ./gradlew bootRun`. 끝나면 판매 건 수, 판매 아닌 공지 수, 몇 건 표본(이름·구분·시각·링크·사진)을 보고하고 사용자와 결과를 같이 본다. 프롬프트를 고쳐야 하면 Task 6으로 돌아간다.
- [ ] **Step 4: 봇 수집 확인** — `NOTICE_ENABLED=true`로 잠깐 띄워 두 채널의 최근 공지(11/5 이후)가 들어오고, 11/6 Neo80 공지가 가져오기분과 겹쳐 한 번만 저장되는지 본다. 확인 뒤 끈다(로컬 DB 상태 주의).

### Task 18: 전체 검토와 마무리

- [ ] **Step 1:** `cd backend && ./gradlew test`, `cd frontend && npm test && npm run lint && npm run build` 모두 통과.
- [ ] **Step 2:** 브랜치 전체 리뷰(superpowers:requesting-code-review). 지적 사항 반영.
- [ ] **Step 3:** 사용자에게 결과 보고, 커밋·PR(`feature/discord-notices` → `dev`)은 사용자 지시에 따라.
- [ ] **Step 4:** 운영 채우기(스펙 3.10)는 배포 뒤 사용자와 함께: 빠진 8/20·11/7 이후 Swagkeys와 GeonWorks를 새로 복사 → 변환·내려받기(`.superpowers/work/discord/scripts/`) → 비용 확인 → 서버 가져오기(Task 9 절차).

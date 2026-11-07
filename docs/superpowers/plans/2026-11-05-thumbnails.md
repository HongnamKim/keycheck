# 수집 기간·틀 글 미리 읽기·이미지 축소본 구현 계획

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 2020년 이후 글만 다루고, 틀 글을 백그라운드에서 미리 채우고, 이미지 축소본(WebP)을 R2에 만들어 화면이 Vercel 이미지 변환 없이 축소본을 직접 읽게 한다.

**Architecture:** 세 덩어리로 나눈다. A(수집 기간): 설정 하나(`since-topic-id`)를 조회·수집·과거 글 확보에 건다. B(미리 읽기): `RequestGate`에 빈 간격만 쓰는 획득을 더하고, 요약 작업과 같은 모양의 작업이 틀 글을 하나씩 채운다. C(축소본): 작업이 이미지를 하나씩 받아 640px(대표 이미지는 1280px도) WebP로 줄여 R2에 올리고, DB에는 객체 이름만 둔다. 응답은 공개 주소를 붙여 주고, 프론트는 `next/image`를 최적화 없이(`unoptimized`) 쓴다.

**Tech Stack:** Spring Boot 4.1.2 / Java 21, Flyway, JPA, Testcontainers(PostgreSQL), AWS SDK v2 S3, WebP ImageIO 플러그인, Next.js 16, vitest.

**Spec:** `docs/superpowers/specs/2026-11-04-thumbnails-design.md`

## Global Constraints

- 커밋은 사용자가 말할 때만 한다. 각 덩어리(A·B·C)가 끝나면 테스트 결과를 보고하고 멈춘다. 새 파일은 만들 때 바로 `git add`.
- Java: `record` 금지. DTO·값 타입은 `private final` + Lombok `@Getter`, 엔티티는 `@Getter`·`@NoArgsConstructor(access = PROTECTED)`·정적 `create`. 들여쓰기 2칸. 테스트는 `@DisplayName`(한국어)과 AssertJ. 패키지는 도메인 아래 역할 폴더.
- 자동 테스트는 실제 geekhack, 실제 이미지 호스트, 실제 R2, 실제 Anthropic을 부르지 않는다. `backend/.env`에 R2 키가 있어도 테스트가 쓰지 않도록 `build.gradle` test 블록에서 덮는다(Task 6).
- 비밀 키를 명령줄·로그·에러 메시지에 싣지 않는다. `backend/.env`를 읽거나 출력하지 않는다.
- geekhack 요청 간격(`keycheck.geekhack.min-interval`, 2s)을 우회하지 않는다. geekhack 첨부 이미지 받기도 `RequestGate`를 거친다.
- 백그라운드 작업(미리 읽기·축소본)은 사용자 요청을 늦추지 않는다. 최악의 경우 한 간격(2초)만 더 기다리게 한다.
- 화면 문구에 "해외"를 쓰지 않는다.
- 축소본: WebP, 가로 최대 640px(대표 이미지는 추가로 1280px), 원본보다 키우지 않음, 비율 유지. 객체 이름 `thumbs/<post_image.id>.webp`, `thumbs/<post_image.id>-1280.webp`. `Cache-Control: public, max-age=31536000, immutable`, `Content-Type: image/webp`.
- 수집 기간 경계 글 번호 이상(`>=`)만 다룬다.
- Next.js 코드를 쓰기 전에 `frontend/node_modules/next/dist/docs/`의 해당 문서를 확인한다(`frontend/AGENTS.md`). Node 22를 PATH 앞에 둔다.
- 검증 명령: 백엔드 `cd backend && ./gradlew test`, 프론트 `cd frontend && npm test && npm run lint && npm run build`.

## Review Focus

1. **원본이 ImageIO가 못 읽는 형식(AVIF, 깨진 파일, HTML 오류 페이지)** → 그 이미지는 실패로 세고(3번 뒤 멈춤) 화면은 원본을 보여준다. Task 5·7 테스트.
2. **R2에서 축소본이 사라졌는데 DB는 완료 상태(배포 전 버킷 정리 후 로컬 DB 그대로)** → 브라우저가 축소본 404를 받으면 원본으로 바꿔 보여준다. Task 9 `imageSources` 테스트.
3. **크기는 작지만 픽셀이 아주 큰 이미지(예: 압축 잘 된 12000×9000 PNG)** → 읽을 때 서브샘플링해 메모리를 지킨다. Task 5 `subsampling` 테스트.
4. **한 글에 같은 주소 이미지가 대표·본문으로 두 번** → 축소본은 한 번만 만들고 두 행이 같은 객체를 가리키며, 대표 행만 1280px를 가진다. Task 7 테스트.
5. **경계 글 번호 바로 그 글(`== since`)과 바로 아래 글** → 그 글은 보이고 아래 글은 목록·하단 목록·상세·수집에서 빠진다. Task 1·2 테스트.

---

## 덩어리 A: 수집 기간 (2020-01-01)

### Task 1: 경계 설정과 조회 조건

**Files:**
- Create: `backend/src/main/java/com/keycheck/crawl/config/CollectProperties.java`
- Modify: `backend/src/main/resources/application.yml` (`keycheck.collect`)
- Modify: `backend/src/main/java/com/keycheck/post/repository/PostRepository.java` (`findByViews`, `findByViewsAbove`)
- Modify: `backend/src/main/java/com/keycheck/post/service/PostQueryService.java` (`findInCoverage`, `getByViews`, `getNeighbors`, `getPost`)
- Test: `backend/src/test/java/com/keycheck/post/controller/PostSinceApiTest.java`

**Interfaces:**
- Produces: `CollectProperties#getSinceTopicId(): long` (`@ConfigurationProperties("keycheck.collect")`, 필드 `private final long sinceTopicId`). 이후 모든 Task가 이 빈으로 경계를 읽는다.

- [ ] **Step 1: 실패하는 테스트 작성** — `PostSinceApiTest`(`@SpringBootTest(properties = "keycheck.collect.since-topic-id=1000")`, MockMvc, `GeekhackClient`·`RequestGate`는 `@MockitoBean`, `PostListApiTest`의 준비 방식을 따른다. IC 확보 상태는 `exhausted = true, floor_topic_id = 0`으로 둬 과거 글 확보가 일어나지 않게 한다). 글 999, 1000, 1001을 저장한다.
  - `recentListExcludesBeforeSince`: `GET /api/posts?board=ic&sort=recent` → `items[*].topicId`… 응답에 글 번호가 없으므로 제목으로 확인: `$.items[*].title` == `["글 1001", "글 1000"]`, `$.hasMore` == false.
  - `viewsListExcludesBeforeSince`: 999의 조회수를 가장 크게 두고 `sort=views` → 999가 없다.
  - `neighborsExcludeBeforeSince`: `GET /api/posts/{1001의 id}/neighbors?board=ic&sort=recent&size=5` → `$.after[*].title` == `["글 1000"]`. `sort=views`에서도 999가 없다.
  - `detailOfBeforeSinceIs404`: `GET /api/posts/{999의 id}` → 404. 1000은 200.
- [ ] **Step 2: 실패 확인** — `cd backend && ./gradlew test --tests '*PostSinceApiTest'` → FAIL (999가 보이고 상세가 200).
- [ ] **Step 3: 구현**
  - `application.yml`에 `keycheck.collect.since-topic-id: 104300`과 주석 "2020-01-01 무렵 첫 글 번호. DB의 작성일 기준점(102599 = 2019-09-24, 112165 = 2021-04-07)으로 보간한 추정값이며 며칠 오차는 괜찮다".
  - `findInCoverage`는 `query.apply`에 넘기는 floor를 `Math.max(floor, since)`로 바꾼다(두 분기 모두). 최근순 목록과 최근순 하단 목록이 함께 걸린다.
  - `findByViews(int views, long topicId, long sinceTopicId, Board, Category, Subcategory, Limit)`, `findByViewsAbove(같은 순서)`: 쿼리에 `and p.topicId >= :sinceTopicId`. 호출부(`getByViews`, `getNeighbors`)가 경계를 넘긴다.
  - `visible(...)`: 숨김뿐 아니라 `topicId < since`도 404. `getNeighbors`의 기준 글에도 같이 적용된다.
- [ ] **Step 4: 통과 확인** — 기존 테스트의 글 번호(2000, 3000 등)는 기본 경계 104300보다 작으므로 `build.gradle` test 블록에 `systemProperty 'keycheck.collect.since-topic-id', '0'`을 두고, 경계 테스트만 `@SpringBootTest(properties = …)`로 덮는다. 같은 명령 PASS, 이어 `./gradlew test` 전체 PASS.

### Task 2: 수집·과거 글 확보의 경계

**Files:**
- Modify: `backend/src/main/java/com/keycheck/crawl/domain/BoardBackfill.java` (`recordPage`, 새 `markExhausted`)
- Modify: `backend/src/main/java/com/keycheck/crawl/service/CrawlStore.java` (`recordBoardPage`, 새 `markBackfillExhausted`)
- Modify: `backend/src/main/java/com/keycheck/crawl/service/CrawlService.java` (`process`, `backfillNextPage`)
- Test: `backend/src/test/java/com/keycheck/crawl/domain/BoardBackfillTest.java`, `backend/src/test/java/com/keycheck/crawl/service/CrawlServiceTest.java`

**Interfaces:**
- Consumes: `CollectProperties#getSinceTopicId()` (Task 1)
- Produces: `BoardBackfill#recordPage(int page, int shownPage, List<Long> topicIds, long sinceTopicId)`, `BoardBackfill#markExhausted()`, `CrawlStore#markBackfillExhausted(Board)`

- [ ] **Step 1: 실패하는 테스트 작성**
  - `BoardBackfillTest.reachingSinceExhausts`: `recordPage(0, 0, List.of(1010L, 990L), 1000)` → `assertState(1, 990L, true)`.
  - `BoardBackfillTest.pageAtSinceDoesNotExhaust`: `recordPage(0, 0, List.of(1010L, 1000L), 1000)` → `assertState(1, 1000L, false)`.
  - 기존 `recordPage` 호출은 마지막 인자 `0`을 더한다.
  - `CrawlServiceTest`(경계를 `2500`으로 두는 새 중첩 클래스 또는 별도 클래스 `CrawlServiceSinceTest`, 픽스처 글 2000·3000 사용):
    - `crawlSkipsNewTopicBeforeSince`: `crawl()` 뒤 글 3000만 저장되고 2000은 없다. `client`가 2000 글 페이지를 요청하지 않았다.
    - `backfillSavesNoSkeletonBeforeSince`: 작성순 0쪽에 2000·3000 → 3000만 틀 글로 저장, IC 확보 상태 `exhausted == true`.
    - `backfillAlreadyBelowSinceExhaustsWithoutRequest`: IC 확보 상태 `floor_topic_id = 2400` → `backfillNextPage(IC, 0s)`가 false, `exhausted == true`, 목록 요청 0건.
- [ ] **Step 2: 실패 확인** — `./gradlew test --tests '*BoardBackfillTest' --tests '*CrawlService*'` → FAIL(컴파일 오류 포함).
- [ ] **Step 3: 구현**
  - `recordPage`: 커버리지를 넓힌 뒤 `min < sinceTopicId`면 `exhausted = true`.
  - `CrawlService.process`: `topic.getTopicId() < since`이고 기존 글이 아니면 저장하지 않고 돌아간다. 기존 글은 지금처럼 조회수만 갱신하되 틀 글 채우기(`readTopics` 분기)는 하지 않는다.
  - `backfillNextPage`: `backfill.getFloorTopicId() != null && floor < since`면 `store.markBackfillExhausted(board)` 후 false.
  - `CrawlStore.recordBoardPage`는 경계를 받아 넘긴다.
- [ ] **Step 4: 통과 확인** — 같은 명령 PASS, 전체 `./gradlew test` PASS.
- [ ] **Step 5: 덩어리 A 보고** — 테스트 수를 보고하고 커밋 여부를 묻는다.

---

## 덩어리 B: 틀 글 미리 읽기

### Task 3: 빈 간격만 쓰는 게이트 획득

**Files:**
- Modify: `backend/src/main/java/com/keycheck/crawl/client/RequestGate.java`
- Test: `backend/src/test/java/com/keycheck/crawl/client/RequestGateTest.java`

**Interfaces:**
- Produces: `RequestGate#tryAcquireIdle(): boolean` — 기다리는 요청(`tryAcquire(maxWait)`·`acquire()` 안에서 기다리는 중)이 없고 마지막 획득 뒤 `minInterval × 2`가 지났을 때만 획득하고 true. 기다리지 않는다.

- [ ] **Step 1: 실패하는 테스트 작성** (`RequestGateTest`, 간격 2s)
  - `idleNeedsTwiceInterval`: `acquire()` → 3.999s 뒤 `tryAcquireIdle()` false → 4s 뒤 true → 곧바로 `tryAcquire()` false.
  - `idleFirstCallPasses`: 처음 `tryAcquireIdle()` true.
  - `idleYieldsToWaiter`: sleeper를 "기다리는 동안 `tryAcquireIdle()`을 불러 결과를 기록하고 시계를 4s 넘게 민다"로 둔 게이트에서 `acquire()` 직후 `acquire()` → 기록된 결과가 false이고 두 번째 `acquire()`는 통과.
- [ ] **Step 2: 실패 확인** — `./gradlew test --tests '*RequestGateTest'` → FAIL(컴파일 오류).
- [ ] **Step 3: 구현** — `private int waiting`을 `acquireBefore`가 기다리기 시작할 때 늘리고 `finally`에서 줄인다(동기화 블록 안). `tryAcquireIdle`은 `synchronized`. 클래스 주석의 목록에 "백그라운드(미리 읽기·축소본): tryAcquireIdle()"을 더한다.
- [ ] **Step 4: 통과 확인** — 같은 명령 PASS.

### Task 4: 미리 읽기 작업

**Files:**
- Create: `backend/src/main/resources/db/migration/V7__post_prefetch_failures.sql`
- Modify: `backend/src/main/java/com/keycheck/post/domain/Post.java` (`prefetchFailures`)
- Modify: `backend/src/main/java/com/keycheck/post/repository/PostRepository.java` (`findPrefetchTargets`, `incrementPrefetchFailures`)
- Modify: `backend/src/main/java/com/keycheck/crawl/service/CrawlService.java` (`prefetchSkeleton`, `readAndFill`가 성공 여부를 돌려줌)
- Create: `backend/src/main/java/com/keycheck/crawl/service/PrefetchWorker.java`
- Create: `backend/src/main/java/com/keycheck/crawl/scheduler/PrefetchScheduler.java`
- Modify: `backend/src/main/resources/application.yml` (`keycheck.prefetch`), `backend/build.gradle`(test: `keycheck.prefetch.enabled=false`), `README.md` 환경변수 표
- Test: `backend/src/test/java/com/keycheck/crawl/service/PrefetchWorkerTest.java`

**Interfaces:**
- Consumes: `RequestGate#tryAcquireIdle()` (Task 3), `CollectProperties` (Task 1)
- Produces: `PrefetchWorker#prefetchNext(): boolean` (대상이 있었으면 true), `CrawlService#prefetchSkeleton(Post): PrefetchResult`(`enum PrefetchResult { FILLED, FAILED, NO_SLOT }`, `crawl/dto`), `PrefetchWorker.MAX_FAILURES = 3`

- [ ] **Step 1: 마이그레이션** — `ALTER TABLE post ADD COLUMN prefetch_failures INTEGER NOT NULL DEFAULT 0;` 주석: "백그라운드 미리 읽기가 틀 글을 읽지 못한 횟수. MAX_FAILURES에 닿으면 미리 읽기에서 뺀다(상세 열기는 계속 채운다)".
- [ ] **Step 2: 실패하는 테스트 작성** — `PrefetchWorkerTest`(`@SpringBootTest`, `CrawlServiceTest`의 `FakeGeekhackClient` 방식, 게이트는 `@MockitoBean`; 경계 `since-topic-id=1000`)
  - `fillsLargestTopicFirst`: 틀 글 2000·3000 → `prefetchNext()` 한 번에 3000만 채워진다(`detailLoaded`), 두 번째에 2000.
  - `skipsWhenNoIdleSlot`: `gate.tryAcquireIdle()` false → `prefetchNext()` true를 돌려도 글은 틀 그대로, `prefetch_failures` 0, 글 요청 0건.
  - `countsFailureAndStopsAfterThree`: 글 페이지 요청이 예외 → 세 번 부른 뒤 `prefetch_failures == 3`, 네 번째 `prefetchNext()`는 false(대상 없음).
  - `skipsHiddenAndBeforeSince`: 숨긴 틀 글, 글 번호 999 틀 글은 대상이 아니다(`prefetchNext()` false).
- [ ] **Step 3: 실패 확인** — `./gradlew test --tests '*PrefetchWorkerTest'` → FAIL.
- [ ] **Step 4: 구현**
  - `findPrefetchTargets(long sinceTopicId, int maxFailures, Limit)`: `hidden = false and detailFetchedAt is null and topicId >= :sinceTopicId and prefetchFailures < :maxFailures order by topicId desc`.
  - `incrementPrefetchFailures(Long id)`: `@Modifying` update로 그 칸만 +1.
  - `prefetchSkeleton`: `tryAcquireIdle()` 실패면 `NO_SLOT`; 읽고 `store.fillSkeleton`; 예외면 `FAILED`. `fillSkeleton`이 false(사용자가 먼저 채움)여도 `FILLED`로 본다.
  - `PrefetchWorker`: 대상 1건 → `FAILED`면 `incrementPrefetchFailures`.
  - `PrefetchScheduler`: `@ConditionalOnProperty(name = "keycheck.prefetch.enabled", havingValue = "true")`, `@Scheduled(fixedDelayString = "${keycheck.prefetch.interval}")`.
  - `application.yml`: `keycheck.prefetch.enabled: ${PREFETCH_ENABLED:false}`, `interval: 10s`, 주석 "켜면 틀 글을 하나씩 실제 geekhack에서 읽는다. 채운 글은 요약 작업이 요약하므로 SUMMARIZER=claude면 글당 약 1.1센트". 스케줄러 풀 크기 `2 → 4`(수집·요약·미리 읽기·축소본이 서로 기다리지 않게).
  - README 환경변수 표에 `PREFETCH_ENABLED` 한 줄.
- [ ] **Step 5: 통과 확인** — 같은 명령 PASS, 전체 `./gradlew test` PASS.
- [ ] **Step 6: 덩어리 B 보고** — 테스트 수와 "켜면 지금 틀 글 수 × 약 1.1센트가 든다"(DB에서 틀 글 수를 세어)를 보고하고, 커밋과 로컬에서 켤지를 묻는다. 사용자 승인 전에는 `PREFETCH_ENABLED=true`로 실행하지 않는다.

---

## 덩어리 C: 이미지 축소본

### Task 5: 축소본 만들기 (순수 변환)

**Files:**
- Modify: `backend/build.gradle` (WebP ImageIO 플러그인)
- Create: `backend/src/main/java/com/keycheck/image/service/ThumbnailMaker.java`
- Create: `backend/src/main/java/com/keycheck/image/service/UnreadableImageException.java`
- Test: `backend/src/test/java/com/keycheck/image/service/ThumbnailMakerTest.java`

**Interfaces:**
- Produces: `ThumbnailMaker#make(byte[] original, int maxWidth): byte[]` (WebP 바이트). 읽을 수 없는 이미지면 `UnreadableImageException`(RuntimeException). `ThumbnailMaker.SMALL_WIDTH = 640`, `LARGE_WIDTH = 1280`. 패키지 공개 `static int subsampling(int width, int height, int maxWidth)`.

- [ ] **Step 1: 의존성 확인(스파이크)** — Maven Central에서 `com.github.usefulness:webp-imageio` 최신 버전을 넣는다(맥 arm64·리눅스 x86_64/arm64 네이티브 포함, WebP 읽기·쓰기). 맥에서 작은 테스트로 인코딩이 되는지 확인하고, Docker `eclipse-temurin:21-jre`(리눅스 x86_64) 컨테이너에서도 `./gradlew test --tests '*ThumbnailMakerTest'`가 도는지 확인한다. 둘 중 하나라도 안 되면 멈추고 사용자에게 알린다(JPEG 대안, 설계 3.1).
- [ ] **Step 2: 실패하는 테스트 작성** (테스트 안에서 `BufferedImage`로 원본을 만든다)
  - `shrinksToMaxWidthKeepingRatio`: 2000×1000 PNG, `make(…, 640)` → 결과를 ImageIO로 읽어 640×320, 앞 4바이트 `RIFF`, 8~11바이트 `WEBP`.
  - `doesNotEnlargeSmallImage`: 300×200 → 300×200.
  - `largeWidth`: 3000×1500, `make(…, 1280)` → 1280×640.
  - `firstFrameOfAnimatedGif`: 프레임 2개(빨강, 파랑) GIF → 결과 가운데 픽셀이 빨강 쪽.
  - `keepsTransparency`: 투명 배경 PNG → 결과 (0,0) 알파 0.
  - `readsWebpOriginal`: WebP 원본(테스트에서 이 클래스로 만든 것)도 줄인다.
  - `unreadableThrows`: `"<html>".getBytes()` → `UnreadableImageException`.
  - `subsampling`: `subsampling(12000, 9000, 640) == 9`(가로가 maxWidth×2 이상이 되도록 남기는 가장 큰 정수 배율: `max(1, width / (maxWidth * 2))`), `subsampling(1000, 800, 640) == 1`.
- [ ] **Step 3: 실패 확인** — `./gradlew test --tests '*ThumbnailMakerTest'` → FAIL.
- [ ] **Step 4: 구현** — `ImageIO.createImageInputStream` → 첫 reader로 크기만 먼저 읽고 `ImageReadParam.setSourceSubsampling(s, s, 0, 0)`로 첫 프레임(index 0)을 읽는다. 줄이기는 `Graphics2D` + `RenderingHints.VALUE_INTERPOLATION_BICUBIC`(서브샘플링으로 2배 이상 남긴 뒤 한 번에). 알파가 있으면 `TYPE_INT_ARGB`, 없으면 `TYPE_INT_RGB`. WebP 손실 압축 품질 0.8(값은 상수).
- [ ] **Step 5: 통과 확인** — 같은 명령 PASS.

### Task 6: 저장소와 설정

**Files:**
- Modify: `backend/build.gradle` (`software.amazon.awssdk:s3` — Spring Boot BOM에 없으므로 `platform('software.amazon.awssdk:bom:<최신 2.x>')`; test 블록에 아래 시스템 속성)
- Create: `backend/src/main/java/com/keycheck/image/config/StorageProperties.java`
- Create: `backend/src/main/java/com/keycheck/image/config/StorageConfig.java`
- Create: `backend/src/main/java/com/keycheck/image/storage/ThumbStorage.java` (interface)
- Create: `backend/src/main/java/com/keycheck/image/storage/S3ThumbStorage.java`
- Create: `backend/src/main/java/com/keycheck/image/storage/StorageUnavailableException.java`
- Create: `backend/src/main/java/com/keycheck/image/service/ThumbUrls.java`
- Modify: `backend/src/main/resources/application.yml`, `backend/.env.example`, `README.md`
- Test: `backend/src/test/java/com/keycheck/image/service/ThumbUrlsTest.java`, `backend/src/test/java/com/keycheck/image/storage/FakeThumbStorage.java`(테스트용 메모리 구현)

**Interfaces:**
- Produces:
  - `ThumbStorage#put(String key, byte[] webp): void` — 실패하면 `StorageUnavailableException`(접속·인증·서버 오류 모두. 이미지 탓이 아니다).
  - `StorageProperties`(`keycheck.storage`): `endpoint`, `bucket`, `accessKeyId`, `secretAccessKey`, `publicUrl` (모두 String, 빈 값 가능). `boolean isConfigured()`: 앞의 넷이 모두 비어 있지 않음.
  - `ThumbStorage` 빈은 `isConfigured()`일 때만 만든다(`StorageConfig`에서 조건부 `@Bean`. 조건은 `@ConditionalOnExpression` 또는 `Optional` 주입 중 간단한 쪽).
  - `ThumbUrls#url(String key): String` — key가 null이거나 `publicUrl`이 비었으면 null, 아니면 `publicUrl`(끝 `/` 제거) + `/` + key.
  - `FakeThumbStorage`: `Map<String, byte[]> objects`, `boolean unavailable`(true면 put이 `StorageUnavailableException`).
- [ ] **Step 1: 실패하는 테스트 작성** — `ThumbUrlsTest`: `url("thumbs/1.webp")` == `"https://img.test/thumbs/1.webp"`(publicUrl `https://img.test/`), key null → null, publicUrl 빈 값 → null.
- [ ] **Step 2: 실패 확인** — `./gradlew test --tests '*ThumbUrlsTest'` → FAIL.
- [ ] **Step 3: 구현**
  - `application.yml`: `keycheck.storage.endpoint: ${R2_ENDPOINT:}`, `bucket: ${R2_BUCKET:}`, `access-key-id: ${R2_ACCESS_KEY_ID:}`, `secret-access-key: ${R2_SECRET_ACCESS_KEY:}`, `public-url: ${R2_PUBLIC_URL:}`. 주석: "Cloudflare R2(S3 호환). 비면 축소본 작업이 돌지 않고 화면은 원본을 쓴다".
  - `S3ThumbStorage`: `S3Client`(region `auto`, `endpointOverride(endpoint)`, `forcePathStyle(true)`, `StaticCredentialsProvider`), `PutObject`에 `contentType("image/webp")`, `cacheControl("public, max-age=31536000, immutable")`. `SdkException`을 `StorageUnavailableException`으로 바꾸되 메시지에 키를 싣지 않는다(SDK 메시지는 키를 담지 않지만, 원인 예외만 붙이고 직접 문자열을 만들지 않는다).
  - `build.gradle` test 블록: `systemProperty 'keycheck.storage.endpoint', ''`, `'keycheck.storage.bucket', ''`, `'keycheck.storage.access-key-id', ''`, `'keycheck.storage.secret-access-key', ''`, `'keycheck.storage.public-url', 'https://img.test'`, `'keycheck.thumbnail.enabled', 'false'`. 주석: ".env의 R2 키가 있어도 테스트는 R2를 부르지 않는다".
  - `.env.example`에 R2 다섯 줄(값은 자리표시), README 환경변수 표에 다섯 줄과 "배포 전 정리"(버킷 `thumbs/` 비우기, 배포 뒤 로컬은 개발용 버킷), 그리고 "로컬 DB를 배포 DB로 옮겼는데 버킷을 비웠다면" SQL 한 줄(Task 7의 칸 이름으로: `update post_image set thumb_key = null, thumb_large_key = null, thumb_failures = 0;`).
- [ ] **Step 4: 통과 확인** — `./gradlew test` 전체 PASS(앱이 키 없이 뜬다).

### Task 7: 축소본 작업

**Files:**
- Create: `backend/src/main/resources/db/migration/V8__post_image_thumbs.sql`
- Modify: `backend/src/main/java/com/keycheck/post/domain/PostImage.java`
- Modify: `backend/src/main/java/com/keycheck/post/repository/PostImageRepository.java`
- Create: `backend/src/main/java/com/keycheck/image/client/ImageDownloader.java`
- Create: `backend/src/main/java/com/keycheck/image/client/ImageFetchException.java`
- Create: `backend/src/main/java/com/keycheck/image/service/ThumbnailWorker.java`
- Create: `backend/src/main/java/com/keycheck/image/scheduler/ThumbnailScheduler.java`
- Create: `backend/src/main/java/com/keycheck/image/config/ImageConfig.java` (`ImageDownloader` 빈: 연결·읽기 시간 제한 15s, User-Agent는 `keycheck.geekhack.user-agent`)
- Modify: `backend/src/main/resources/application.yml` (`keycheck.thumbnail`), `README.md`
- Test: `backend/src/test/java/com/keycheck/image/service/ThumbnailWorkerTest.java`

**Interfaces:**
- Consumes: `ThumbnailMaker` (Task 5), `ThumbStorage`·`FakeThumbStorage`·`StorageUnavailableException` (Task 6), `RequestGate#tryAcquireIdle()` (Task 3), `CollectProperties` (Task 1)
- Produces:
  - DB 칸(`post_image`): `thumb_key TEXT`, `thumb_large_key TEXT`, `thumb_failures INTEGER NOT NULL DEFAULT 0`. 완료 = `thumb_key is not null`. 실패로 멈춤 = `thumb_failures >= 3`.
  - `PostImage#getThumbKey()`, `getThumbLargeKey()`, `getThumbFailures()`.
  - `ImageDownloader#download(String url): byte[]` — 25MB(`MAX_BYTES = 25 * 1024 * 1024`) 넘으면 읽기를 끊고 `ImageFetchException`. 4xx·5xx·시간 초과도 `ImageFetchException`(이미지 탓으로 센다).
  - `ThumbnailWorker#makeNext(): boolean` (대상이 있었으면 true), `ThumbnailWorker.MAX_FAILURES = 3`, `ERROR_PAUSE = Duration.ofMinutes(1)`.
- [ ] **Step 1: 마이그레이션** — 세 칸 추가와 주석, `CREATE INDEX idx_post_image_thumb_pending ON post_image (post_id) WHERE thumb_key IS NULL AND excluded_reason IS NULL;`
- [ ] **Step 2: 실패하는 테스트 작성** — `ThumbnailWorkerTest`(`@SpringBootTest`, 경계 1000. `ImageDownloader`는 `@MockitoBean`으로 주소별 바이트(테스트에서 만든 PNG)를 돌려준다. 게이트는 `@MockitoBean`. 워커는 테스트가 `new ThumbnailWorker(...)`로 `FakeThumbStorage`·`MutableClock`을 넣어 만든다(`SummaryWorkerTest` 방식)).
  - `makesSmallThumb`: 본문 이미지 1장(2000×1000) → `makeNext()` 뒤 `thumb_key == "thumbs/<id>.webp"`, `thumb_large_key` null, 저장소에 그 키 객체(가로 640).
  - `representativeAlsoGetsLarge`: 대표 이미지 → `thumb_large_key == "thumbs/<id>-1280.webp"`, 저장소에 두 객체(가로 640, 1280).
  - `representativeFirstThenRecentPost`: 글 2000(이미지 2장, 둘째가 대표), 글 3000(이미지 1장) → 처리 순서가 3000의 이미지, 2000의 대표, 2000의 첫 이미지.
  - `duplicateUrlInPostMadeOnce`: 같은 글에 같은 주소 2행(하나가 대표) → 다운로드 1번, 두 행 모두 `thumb_key`가 대표 행의 키, 대표 행만 `thumb_large_key`.
  - `skipsExcludedHiddenSkeletonAndBeforeSince`: 제외 사유가 있는 이미지, 숨긴 글, 글 번호 999의 이미지는 대상이 아니다(`makeNext()` false).
  - `unreadableCountsFailureAndStops`: 다운로드가 `"<html>"` 바이트 → 세 번 뒤 `thumb_failures == 3`, 네 번째 `makeNext()` false.
  - `storageDownPausesWithoutCounting`: `FakeThumbStorage.unavailable = true` → `thumb_failures == 0`, 같은 시각에 다시 부르면 false(쉬는 중), 시계를 1분 밀고 저장소를 살리면 완료.
  - `attachmentWaitsForIdleGate`: `source = ATTACHMENT` 이미지, `gate.tryAcquireIdle()` false → 다운로드 0번, `thumb_failures == 0`, `makeNext()` true. true로 바꾸면 완료.
- [ ] **Step 3: 실패 확인** — `./gradlew test --tests '*ThumbnailWorkerTest'` → FAIL.
- [ ] **Step 4: 구현**
  - `PostImageRepository#findThumbTargets(long sinceTopicId, int maxFailures, Limit)`: `select i from PostImage i join i.post p where i.thumbKey is null and i.excludedReason is null and i.thumbFailures < :maxFailures and p.hidden = false and p.detailFetchedAt is not null and p.topicId >= :sinceTopicId order by p.detailFetchedAt desc, p.topicId desc, case when i.id = p.representativeImageId then 0 else 1 end, i.position`.
  - `@Modifying` 업데이트: `markThumbs(Long postId, String url, String key)`(같은 글·같은 주소 행 모두 `thumb_key`), `setThumbLargeKey(Long imageId, String key)`, `incrementThumbFailures(Long imageId)`. 1280 키를 먼저 저장하고 640 키를 마지막에 저장해 "thumb_key가 있으면 대표의 1280도 있다"를 지킨다.
  - 대표 여부는 `image.getId().equals(post.getRepresentativeImageId())`. 대상 순서가 글 안에서 대표 먼저이므로, 같은 주소의 대표 행이 늘 먼저 처리되어 1280도 함께 만든다.
  - geekhack 첨부 판단: `source == ATTACHMENT` 또는 주소 호스트가 `geekhack.org`로 끝남. 그럴 때만 `gate.tryAcquireIdle()`, 실패면 아무것도 바꾸지 않고 true.
  - `ImageFetchException`·`UnreadableImageException` → `incrementThumbFailures`, 경고 로그(주소와 이미지 ID). `StorageUnavailableException` → `pausedUntil = now + ERROR_PAUSE`, 에러 로그.
  - `ThumbnailScheduler`: `@ConditionalOnProperty(name = "keycheck.thumbnail.enabled", havingValue = "true")` 그리고 `ThumbStorage` 빈이 있을 때만(`ObjectProvider<ThumbStorage>`로 없으면 아무것도 안 함). `fixedDelayString = "${keycheck.thumbnail.interval}"`.
  - `application.yml`: `keycheck.thumbnail.enabled: ${THUMBNAIL_ENABLED:false}`, `interval: 3s`. README 표에 `THUMBNAIL_ENABLED`.
- [ ] **Step 5: 통과 확인** — 같은 명령 PASS, 전체 `./gradlew test` PASS.

### Task 8: API 응답에 축소본 주소

**Files:**
- Create: `backend/src/main/java/com/keycheck/post/dto/PostImageResponse.java`
- Modify: `backend/src/main/java/com/keycheck/post/dto/PostCardResponse.java`, `PostDetailResponse.java`
- Modify: `backend/src/main/java/com/keycheck/post/service/PostQueryService.java` (`representativeImageUrls` → 대표 이미지 엔티티 맵)
- Test: `backend/src/test/java/com/keycheck/post/controller/PostThumbApiTest.java`, 기존 `imageUrls`를 보는 테스트 수정

**Interfaces:**
- Consumes: `ThumbUrls#url` (Task 6), `PostImage#getThumbKey/getThumbLargeKey` (Task 7)
- Produces (JSON, 프론트 Task 9가 그대로 쓴다):
  - 카드: 기존 필드 + `representativeThumbUrl: string | null`.
  - 상세: `imageUrls` 제거 → `images: [{ url: string, thumbUrl: string | null }]`(규칙에서 빠지지 않은 후보, 본문 순서, 주소 중복 제외). 기존 `representativeImageUrl` 유지 + `representativeThumbUrl`, `representativeLargeUrl`(1280, 없으면 null).
  - `PostImageResponse`: `private final String url; private final String thumbUrl;`
- [ ] **Step 1: 실패하는 테스트 작성** — `PostThumbApiTest`(테스트 public-url `https://img.test`): 대표 이미지에 `thumb_key = 'thumbs/5.webp'`, `thumb_large_key = 'thumbs/5-1280.webp'`, 다른 이미지는 축소본 없음.
  - 목록: `$.items[0].representativeThumbUrl == "https://img.test/thumbs/5.webp"`.
  - 상세: `$.representativeLargeUrl == "https://img.test/thumbs/5-1280.webp"`, `$.images[0].thumbUrl`이 대표면 위 주소, `$.images[1].thumbUrl` null, `$.images[1].url`은 원본.
  - 하단 목록 카드도 `representativeThumbUrl`을 준다.
- [ ] **Step 2: 실패 확인** — `./gradlew test --tests '*PostThumbApiTest'` → FAIL.
- [ ] **Step 3: 구현** — `representativeImageUrls`를 `Map<Long, PostImage> representativeImages(List<Post>)`로 바꾸고 `PostCardResponse.from(post, PostImage representative, Instant estimate, ThumbUrls urls)`, `PostDetailResponse.from(post, PostImage representative, Instant estimate, ThumbUrls urls)`로 시그니처를 바꾼다. 상세 `images`의 중복 제외는 주소 기준 첫 행.
- [ ] **Step 4: 통과 확인** — 전체 `./gradlew test` PASS. `OpenApiTest`가 필드를 보면 함께 고친다.

### Task 9: 프론트 적용

**Files:**
- Modify: `frontend/src/lib/api.ts` (타입)
- Create: `frontend/src/lib/image.ts`, `frontend/src/lib/image.test.ts`
- Modify: `frontend/src/components/RemoteImage.tsx`, `PostCard.tsx`, `BoardColumn.tsx`, `NeighborPager.tsx`, `NeighborList.tsx`, `Gallery.tsx`, `frontend/src/app/posts/[id]/page.tsx`, `frontend/next.config.ts`
- Test: `frontend/src/lib/image.test.ts`, 타입이 바뀐 기존 테스트

**Interfaces:**
- Consumes: Task 8의 JSON.
- Produces: `imageSources(thumbUrl: string | null, originalUrl: string): { src: string; fallback: string | null }` — 축소본이 있으면 `{src: thumb, fallback: original}`, 없으면 `{src: original, fallback: null}`. `RemoteImage` props: `ImageProps`에서 `src`를 `string`으로, `fallbackSrc?: string | null` 추가.
- [ ] **Step 1: 실패하는 테스트 작성** — `image.test.ts`: 축소본 있음/없음 두 경우의 정확한 객체.
- [ ] **Step 2: 실패 확인** — `cd frontend && npm test -- image` → FAIL.
- [ ] **Step 3: 구현**
  - `next.config.ts`: `images: { unoptimized: true }`로 바꾸고 `remotePatterns` 제거. 주석 "이미지는 백엔드가 만든 축소본(R2)이나 원본을 브라우저가 직접 읽는다. Vercel 이미지 변환을 쓰지 않는다(무료 한도 월 5,000건)". 먼저 `node_modules/next/dist/docs/`에서 `images.unoptimized` 문서를 확인한다.
  - `RemoteImage`: 오류가 나면 `fallbackSrc`가 있을 때 한 번만 그 주소로 바꾼다(`key`로 이미지마다 상태가 새로 시작되는 기존 방식 유지). 컴포넌트 주석을 새 동작으로 고친다.
  - 카드·하단 목록·메인 칸: `imageSources(post.representativeThumbUrl, post.representativeImageUrl)`. `RowPost`에 `representativeThumbUrl` 추가, `NeighborList`의 `current`에도.
  - 상세 맨 위: `imageSources(post.representativeLargeUrl, post.representativeImageUrl)`.
  - `Gallery`: `images: PostImage[]`를 받는다. 칸은 `imageSources(image.thumbUrl, image.url)`, 확대 보기와 "원본 열기"는 `image.url`. 상세 페이지의 장 수 표시도 `post.images.length`.
- [ ] **Step 4: 통과 확인** — `npm test && npm run lint && npm run build` PASS.

### Task 10: 실제 R2로 로컬 확인

- [ ] **Step 1:** 실행 중인 백엔드를 멈추고 `THUMBNAIL_ENABLED=true`로 다시 띄운다(`SUMMARIZER`는 지금 값 유지, 미리 읽기는 사용자가 승인했을 때만 켠다). 로그에서 축소본이 만들어지는지(완료 수가 느는지) DB로 센다: `select count(*) filter (where thumb_key is not null), count(*) filter (where thumb_failures >= 3), count(*) from post_image where excluded_reason is null`.
- [ ] **Step 2:** 프론트(3000)를 Playwright 스크립트(`scratchpad/tools/`)로 열어 목록 카드·상세 갤러리 이미지의 `src`가 `pub-…r2.dev/thumbs/`인지, `/_next/image` 요청이 0건인지, 상세 맨 위가 `-1280.webp`인지 확인한다.
- [ ] **Step 3:** 실패 이미지 몇 건의 주소와 원인(형식, 404 등)을 모아 덩어리 C 테스트 수와 함께 보고하고 커밋 여부를 묻는다.

# geekhack 동시 요약 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** geekhack 글 요약을 동시에 여러 건(기본 2) 처리하고, 상세 화면에서 "차례를 기다리는 중"과 "요약하는 중"을 서버 기준으로 나눠 보여 준다.

**Architecture:**
- 처리 중인 글은 메모리 보관소(`SummaryInProgress`)에 둔다. `SummaryWorker`는 집기와 끝내기를 한 잠금 안에서 하도록 바꿔 여러 스레드에서 불러도 안전하게 만든다.
- `SummarySlots`는 요약 전용 `ThreadPoolTaskExecutor`에 칸을 연다. 스케줄러가 틱마다 한 칸을 열고, 글을 집은 칸은 한 칸을 더 열며, 열린 칸은 대상이 없을 때까지 이어서 요약한다.
- 상세 응답에 `summarizingForMs`를 더한다. 프론트 `SummaryWatcher`는 이 값으로 기다림과 요약 중을 나눠 보여 준다.

**Tech Stack:** Spring Boot 4 / Java 21 / Lombok / JUnit 5 + AssertJ + Mockito / Testcontainers(PostgreSQL 17), Next.js 16 / TypeScript / vitest

**Spec:** `docs/superpowers/specs/2026-11-09-parallel-summary-design.md`. 이 계획은 설계의 3장 결정을 그대로 옮긴다. 막히면 설계를 먼저 읽는다.

## Global Constraints

- 작업 위치는 git worktree `/Users/hongnamkim/Project/keycheck/.worktrees/parallel-summary`(브랜치 `feature/parallel-summary`)뿐이다. 메인 작업 트리와 다른 worktree는 건드리지 않는다.
- 커밋하지 않는다(사용자가 시킬 때만). 새 파일은 만들자마자 `git add` 한다.
- 백엔드 스타일
  - 들여쓰기 2칸, Lombok.
  - DTO는 record 대신 `private final` + `@Getter`.
  - 테스트는 `@DisplayName`(한국어)과 AssertJ.
  - 패키지는 도메인 아래 역할별 폴더로 나눈다.
  - 주석은 짧은 한국어로, 주변 말투에 맞춘다.
- 자동 테스트는 실제 Claude·geekhack을 부르지 않는다. 로컬 DB를 쓰는 서버를 띄우지 않는다.
- 요약 상태 enum(PENDING/DONE/FAILED)에 새 값을 넣지 않는다.
- DB 마이그레이션은 만들지 않는다.
- 설정 기본값: `keycheck.summary.concurrency: 2`, `keycheck.summary.interval: 5s`(그대로).
- 프론트 문구는 해요체로 쓰고 "해외"를 쓰지 않는다. 정확한 문구는 다음 세 가지다.
  - 기다림: `요약 차례를 기다리고 있어요`
  - 요약 중: `요약하는 중이에요`
  - 늦어짐: `요약이 늦어지고 있어요. 잠시 뒤 새로고침해 주세요` (지금과 같음)
- 프론트 Next.js 코드는 `frontend/AGENTS.md` 안내대로 `node_modules/next/dist/docs/`를 먼저 확인한다.
- 검증 명령
  - 백엔드: `cd backend && ./gradlew test`
  - 프론트: `cd frontend && npm test && npm run lint && npm run build`

## Review Focus

설계가 요구하지만 아래 태스크의 테스트만으로는 잡기 어려운 실패 방식이다. 리뷰어가 코드를 읽어 확인한다.

1. **옛 조회로 다시 집기:** 한 칸이 저장을 커밋하고 표시를 내리는 사이에, 다른 칸이 커밋 전에 돌린 대상 쿼리 결과로 같은 글을 집는 경쟁. 끝내기(`redone` 추가 → 쉬는 시각 → 표시 내리기)가 집기와 같은 잠금 안에서, 저장이 끝난 뒤에 일어나는지 본다. 테스트로 강제하기 어려우니 코드로 확인한다.
2. **종료:** 앱이 종료될 때 진행 중인 Claude 호출을 기다리지 않는지 본다. `ThreadPoolTaskExecutor`의 `waitForTasksToCompleteOnShutdown=false`이고, `ExecutorService`를 `@Bean`으로 직접 내놓지 않아야 한다.
3. **API 오류로 쉬는 동안:** 한 칸이 API 오류로 1분 쉬기를 걸면, 다른 칸은 진행 중인 호출만 끝내고 다음 글을 집지 않는다. Task 1의 `stopsClaimingWhilePaused` 테스트가 다룬다.
4. **옛 백엔드 + 새 프론트:** 응답에 `summarizingForMs` 키가 아예 없으면 지금 화면과 똑같이 동작한다(τ 5초, 60초 늦어짐). Task 4의 legacy 테스트가 다룬다.
5. **긴 요약:** 요약 중 표시가 3분을 넘겨도 늦어짐으로 바뀌지 않고, 7분이 지나야 바뀐다. Task 4 테스트가 다룬다.

---

### Task 1: 처리 중 보관소와 여러 스레드에서 안전한 `SummaryWorker`

**Files:**
- Create: `backend/src/main/java/com/keycheck/summary/service/SummaryInProgress.java`
- Modify: `backend/src/main/java/com/keycheck/summary/service/SummaryWorker.java`
- Test (create): `backend/src/test/java/com/keycheck/summary/service/SummaryInProgressTest.java`
- Test (modify): `backend/src/test/java/com/keycheck/summary/service/SummaryWorkerTest.java`

**Interfaces:**
- Produces: `SummaryInProgress` (`@Component`, 다른 빈에 기대지 않음, 내부는 `ConcurrentHashMap<Long, Instant>`)
  - `boolean contains(long postId)`
  - `int size()`
  - `void add(long postId, Instant startedAt)`
  - `void remove(long postId)`
  - `Long elapsedMs(long postId, Instant now)`: 처리 중이 아니면 null, 처리 중이면 `max(0, now − startedAt)`의 밀리초
- Produces: `SummaryWorker`
  - `public boolean summarizeNext(Runnable onClaimed)`: 글을 집은 직후, 한도를 세기 전에 `onClaimed.run()`을 한 번 부른다. 대상이 없거나 쉬는 중이면 부르지 않는다. 반환값의 뜻은 지금과 같다.
  - `public boolean summarizeNext()`: `summarizeNext(() -> {})`를 부른다. 기존 테스트와 기존 뜻을 그대로 둔다.
  - 생성자에 `SummaryInProgress inProgress`를 추가한다(`@RequiredArgsConstructor` 필드 순서상 `budget` 뒤, `clock` 앞).

- [ ] **Step 1: `SummaryInProgressTest`를 쓴다**
  - `addContainsRemove`: add 뒤 `contains`가 true이고 `size()`가 1, remove 뒤 false이고 0이다.
  - `elapsedMsNullWhenAbsent`: `elapsedMs(1L, now)`가 null이다.
  - `elapsedMsSinceStart`: `add(1L, t)` 뒤 `elapsedMs(1L, t.plusMillis(1500))`가 `1500L`이다.
  - `elapsedMsNeverNegative`: `elapsedMs(1L, t.minusSeconds(1))`가 `0L`이다.

- [ ] **Step 2: `SummaryWorkerTest`에 테스트를 더한다**
  - 테스트용 `worker(...)` 도우미는 같은 `SummaryInProgress` 인스턴스를 필드로 두고 넘긴다.
  - 동시성 테스트의 요약기는 `CountDownLatch`로 붙잡는다. 기존 `RecordingSummarizer`는 `ArrayList`라 스레드 안전하지 않으니, 동시 테스트에서는 스레드 안전한 기록(예: `CopyOnWriteArrayList`)을 쓴다.
  - `twoThreadsClaimDifferentPosts`
    - 준비: PENDING 글 2개. 요약기는 두 호출이 모두 들어올 때까지 latch로 기다린다.
    - 실행: 두 스레드에서 `summarizeNext()`를 동시에 부른다.
    - 확인: 요약기가 받은 제목 집합이 두 글의 제목과 같다(중복 없음). 두 글 모두 DONE이다.
  - `budgetCountedOncePerPost`: `dailyLimit=2`, PENDING 글 2개를 두 스레드로 처리한 뒤 세 번째 `summarizeNext()`가 false다. 한도를 정확히 2번 썼다는 뜻이다.
  - `releasesClaimAfterSuccess`: 요약 중 요약기 안에서 `inProgress.contains(id)`가 true이고, 끝난 뒤 false다.
  - `releasesClaimAfterPostFailure`: 요약기가 `IllegalStateException`을 던지면 글이 FAILED이고 `inProgress.size()`가 0이다.
  - `releasesClaimAfterApiError`: 요약기가 `AnthropicIoException`을 던지면 글은 PENDING이고 `inProgress.size()`가 0이다.
  - `releasesClaimAfterSaveError`: `postRepository`를 spy하거나 대역으로 바꿔 `saveSummary`가 `DataAccessResourceFailureException`을 던지게 한다. `summarizeNext()`가 그 예외를 그대로 던지고(지금처럼 잡지 않는다), `inProgress.size()`가 0이다.
  - `releasesClaimWhenBudgetExhausted`: `dailyLimit=0`이면 `summarizeNext()`가 false이고, 요약기를 부르지 않았고, `inProgress.size()`가 0이다.
  - `stopsClaimingWhilePaused`
    - 준비: PENDING 글 2개. 칸 A의 요약기는 latch로 붙잡고, 칸 B의 요약기는 `UnauthorizedException`을 던지게 한다.
    - 실행: A가 요약 중일 때 B가 API 오류로 끝난다.
    - 확인: 그 뒤 세 번째 `summarizeNext()`는 false다(쉬는 중). A를 풀면 A의 글은 DONE이 된다.
  - `pauseKeepsLaterInstant`
    - 실행: 한도 소진으로 다음 0시까지 쉬게 한 뒤, 다른 경로로 API 오류 1분 쉬기를 걸어 본다.
    - 확인: 1분 뒤에도 `summarizeNext()`가 false다. 쉬는 시각이 더 늦은 값(다음 0시)으로 남았다는 뜻이다. 시계는 테스트의 `clock`을 움직여서 쓴다.
  - `callsOnClaimedOnceWhenClaimed`: PENDING 글 1개에서 `summarizeNext(counter::incrementAndGet)` 뒤 counter가 1이다. 대상이 없을 때 다시 부르면 counter는 그대로 1이다.
  - `skipsPostInProgress`: `inProgress.add(id, now)`로 미리 올려 둔 글은 건너뛰고 다음 글을 요약한다.

- [ ] **Step 3: 테스트가 실패하는지 확인한다**
  - Run: `cd backend && ./gradlew test --tests 'com.keycheck.summary.service.SummaryInProgressTest' --tests 'com.keycheck.summary.service.SummaryWorkerTest'`
  - Expected: 컴파일 실패(`SummaryInProgress` 없음).

- [ ] **Step 4: `SummaryInProgress`를 만들고 `SummaryWorker`를 바꾼다** (설계 3.2를 그대로 따른다)
  - `pausedUntil`을 `AtomicReference<Instant>`로 바꾼다. 쉬는 시각을 정할 때는 `accumulateAndGet(t, max)`로 더 늦은 값만 남긴다.
  - `redone`을 `ConcurrentHashMap.newKeySet()`으로 바꾼다.
  - 잠금 객체 `private final Object claimLock = new Object();`를 둔다.
  - **집기** (`synchronized (claimLock)` 안에서)
    1. 쉬는 중인지 본다.
    2. `findSummaryTargets(..., Limit.of(redone.size() + inProgress.size() + 1))`를 부른다.
    3. 처리 중인 글을 건너뛰고, 기존처럼 `isGbRedo || !redone.contains` 조건으로 고른다.
    4. `inProgress.add(id, clock.instant())`.
  - 잠금 밖에서 `onClaimed.run()` → 한도 세기 → 요약 → 저장 순서로 한다.
  - **끝내기**: `finally` 안의 `synchronized (claimLock)`에서 한다. 순서는 반드시 다음과 같다.
    1. 그 경로가 `redone`에 넣던 경우에만 `redone.add(id)`
    2. 그 경로가 쉬기를 걸던 경우에만 쉬는 시각을 정한다
    3. `inProgress.remove(id)`
  - 각 경로에서 `redone`에 넣을지, 쉬기를 걸지는 지금 코드와 같다. 경로마다 그 결과를 지역 변수로 들고 있다가 `finally`에서 적용한다.
  - 요약기 호출 앞뒤를 `System.nanoTime()`으로 재서, 성공과 실패 모두 걸린 시간을 남긴다. 성공은 `log.info("요약 걸린 시간: topic={}, ms={}", ...)`, 실패는 기존 로그에 ms를 덧붙인다.
  - 클래스 javadoc과 `summarizeNext` javadoc을 고친다. 처리 중 표시, 여러 칸에서 불린다는 것, `onClaimed`의 뜻을 적는다.

- [ ] **Step 5: 이 두 테스트 클래스와 기존 `SummaryWorkerTest` 전체가 통과하는지 확인한다**
  - Run: Step 3과 같은 명령
  - Expected: PASS. 기존 순서·다시 요약 테스트도 모두 통과해야 한다.
  - 기존 테스트가 깨지면 동작을 바꾸지 말고 구현을 고친다.

- [ ] **Step 6: 새 파일을 스테이징한다**
  - Run: `git add backend/src/main/java/com/keycheck/summary/service/SummaryInProgress.java backend/src/test/java/com/keycheck/summary/service/SummaryInProgressTest.java`

### Task 2: 요약 칸(`SummarySlots`)과 전용 스레드 풀, 설정

**Files:**
- Create: `backend/src/main/java/com/keycheck/summary/service/SummarySlots.java`
- Create: `backend/src/main/java/com/keycheck/summary/config/SummaryExecutorConfig.java`
- Modify
  - `backend/src/main/java/com/keycheck/summary/config/SummaryProperties.java`
  - `backend/src/main/java/com/keycheck/summary/scheduler/SummaryScheduler.java`
  - `backend/src/main/resources/application.yml` (`keycheck.summary` 아래)
  - `deploy/keycheck.env.example` (46줄 `KEYCHECK_SUMMARY_DAILYLIMIT` 아래)
- Test (create): `backend/src/test/java/com/keycheck/summary/service/SummarySlotsTest.java`
- Test (modify)
  - `backend/src/test/java/com/keycheck/summary/scheduler/SummarySchedulerTest.java`
  - `new SummaryProperties(...)`를 부르는 모든 테스트: `SummaryWorkerTest` 16곳, `NoticeSummaryWorkerTest` 2곳, `DailySummaryBudgetTest` 1곳

**Interfaces:**
- Consumes: `SummaryWorker.summarizeNext(Runnable onClaimed)` (Task 1)
- Produces
  - `SummaryProperties`에 마지막 필드 `private final int concurrency;`를 더한다. 생성자 인자는 5개가 된다: `(redoFake, redoMissingSubcategory, redoMissingProductName, dailyLimit, concurrency)`.
  - `SummaryExecutorConfig`: `@Configuration`, `@ConditionalOnProperty(name = "keycheck.summary.enabled", havingValue = "true")`.
    - `@Bean ThreadPoolTaskExecutor summaryExecutor(SummaryProperties)`
    - 설정: core = max = concurrency, `queueCapacity=0`, `threadNamePrefix="summary-"`, `waitForTasksToCompleteOnShutdown=false`
  - `SummarySlots`: `@Component`, 같은 조건.
    - 생성자: `(SummaryWorker worker, SummaryProperties properties, @Qualifier("summaryExecutor") Executor executor)`
    - `void openIfFree()`
    - `int openCount()` (테스트용)
  - `SummaryScheduler.run()`은 `slots.openIfFree()`만 부른다.

- [ ] **Step 1: `SummarySlotsTest`를 쓴다** (Spring 없이 단위 테스트)
  - 준비
    - `worker`는 Mockito mock이다.
    - `executor`는 받은 `Runnable`을 리스트에 모아 두고 테스트가 직접 돌리는 대역이다.
    - `concurrency=2`
  - `opensOneSlotPerCall`: `openIfFree()`를 한 번 부르면 executor가 받은 작업이 1개이고 `openCount()`가 1이다.
  - `neverExceedsConcurrency`: `openIfFree()`를 세 번 불러도 받은 작업은 2개이고 `openCount()`는 2다.
  - `slotKeepsSummarizingWhileTargetsExist`: `worker.summarizeNext(any())`가 true, true, false 순으로 돌려주면 작업 하나를 돌렸을 때 3번 불리고, 끝나면 `openCount()`가 0이다.
  - `claimingOpensAnotherSlot`: `summarizeNext`에 넘어온 `onClaimed`를 대역 안에서 부르면 executor가 받은 작업이 2개가 된다.
  - `exceptionClosesSlotAndRestoresCount`: `summarizeNext`가 `DataAccessResourceFailureException`을 던져도 작업 실행이 예외를 밖으로 내지 않고, `openCount()`가 0으로 돌아오며, 다음 `openIfFree()`가 다시 칸을 연다.
  - `rejectedSubmitRestoresCount`: executor가 `RejectedExecutionException`을 던지면 `openCount()`가 0이다.
- [ ] **Step 2: `SummarySchedulerTest`를 고친다**
  - 기대값을 `verify(worker, timeout(5000).atLeast(2)).summarizeNext(any(Runnable.class))`로 바꾼다.
  - `@DisplayName`은 "켜져 있으면 주기마다 요약 칸을 열어 요약 대기 글을 요약한다"로 한다.
- [ ] **Step 3: 테스트가 실패하는지 확인한다**
  - Run: `cd backend && ./gradlew test --tests 'com.keycheck.summary.service.SummarySlotsTest' --tests 'com.keycheck.summary.scheduler.SummarySchedulerTest'`
  - Expected: 컴파일 실패.
- [ ] **Step 4: 구현한다**
  - **`openIfFree`**
    - CAS 루프로 `open < concurrency`일 때만 1을 올리고 `executor.execute(this::runSlot)`를 부른다.
    - `RejectedExecutionException`이 나면 1을 되돌리고 `log.warn`을 남긴다.
  - **`runSlot`**
    - 본문은 `try { while (worker.summarizeNext(this::openIfFree)) {} } catch (RuntimeException e) { log.error("요약 칸이 예외로 닫혔다", e); } finally { open.decrementAndGet(); }`
  - **설정 파일**
    - `SummaryProperties`에 필드와 javadoc을 더한다: "동시에 요약하는 글 수. 운영 요청 한도 등급을 확인하기 전이라 2에서 시작".
    - `new SummaryProperties(...)` 호출을 모두 5인자로 고친다. 기존 테스트에서는 마지막 인자를 `1`로 둔다.
    - `application.yml`의 `keycheck.summary`에 `concurrency: 2`와 한 줄 주석을 더한다.
    - `deploy/keycheck.env.example`의 46줄 아래에 다음 두 줄을 더한다.
      ```
      # 동시에 요약하는 geekhack 글 수
      # KEYCHECK_SUMMARY_CONCURRENCY=2
      ```
  - **`SummaryScheduler`**: javadoc도 맞게 고친다.
- [ ] **Step 5: 백엔드 전체 테스트를 돌린다**
  - Run: `cd backend && ./gradlew test`
  - Expected: BUILD SUCCESSFUL, 실패 0.
  - `OpsConfigTest`처럼 설정 기본값을 확인하는 테스트가 있으면 `concurrency` 기본값 2도 확인하도록 더한다.
- [ ] **Step 6: 새 파일을 스테이징한다**
  - Run: `git add backend/src/main/java/com/keycheck/summary/service/SummarySlots.java backend/src/main/java/com/keycheck/summary/config/SummaryExecutorConfig.java backend/src/test/java/com/keycheck/summary/service/SummarySlotsTest.java`

### Task 3: 상세 응답의 `summarizingForMs`

**Files:**
- Modify
  - `backend/src/main/java/com/keycheck/post/dto/PostDetailResponse.java`
  - `backend/src/main/java/com/keycheck/post/service/PostQueryService.java:290-303`
  - `backend/src/main/java/com/keycheck/post/controller/PostController.java` (상세 API 설명 문구)
- Test (modify): `backend/src/test/java/com/keycheck/post/controller/PostDetailFillApiTest.java`. 상세 응답 필드를 보는 테스트가 따로 있으면 그쪽에 둔다.

**Interfaces:**
- Consumes: `SummaryInProgress.elapsedMs(long, Instant)` (Task 1)
- Produces
  - JSON 필드 `summarizingForMs`: number 또는 null, 항상 키가 있다.
  - `PostDetailResponse.from(...)`의 마지막 인자로 `Long summarizingForMs`를 더한다.
  - javadoc: "지금 요약 중이면 요약을 시작한 뒤 흐른 밀리초, 아니면 null"

- [ ] **Step 1: 테스트를 쓴다**
  - `summarizingForMsWhileInProgress`
    - 준비: 요약 대기 글을 저장하고, 테스트가 `SummaryInProgress` 빈에 `add(id, now − 4초)`로 올린다.
    - 확인: `GET /api/posts/{id}?fill=false` 응답의 `$.summarizingForMs`가 4000 이상이다.
    - 정리: 다른 테스트를 위해 끝나면 `remove`한다.
  - `summarizingForMsNullWhenNotInProgress`: 같은 글이 처리 중이 아니면 `$.summarizingForMs`가 null이고, 키는 있다(`jsonPath(...).value(nullValue())`).
- [ ] **Step 2: 테스트가 실패하는지 확인한다**
  - Run: `cd backend && ./gradlew test --tests 'com.keycheck.post.controller.PostDetailFillApiTest'`
  - Expected: FAIL.
- [ ] **Step 3: 구현한다**
  - `getPost`의 첫 줄에서 `Long summarizingForMs = inProgress.elapsedMs(id, clock.instant());`를 읽는다. DB 조회보다 먼저 읽어야 한다(설계 3.5).
  - `PostQueryService`에 `SummaryInProgress`를 주입하고, 결과를 `from`에 넘긴다.
  - `PostController`의 상세 API 설명에 이 필드를 한 줄 더한다.
- [ ] **Step 4: 백엔드 전체 테스트를 돌린다**
  - Run: `cd backend && ./gradlew test`
  - Expected: BUILD SUCCESSFUL, 실패 0.

### Task 4: 프론트 요약 진행 표시

**Files:**
- Modify
  - `frontend/src/lib/api.ts` (`PostDetail` 타입, 123줄 근처)
  - `frontend/src/lib/summary.ts`
  - `frontend/src/components/SummaryWatcher.tsx`
  - `frontend/src/components/SummaryWatcher.module.css`
- Test (modify): `frontend/src/lib/summary.test.ts`

**Interfaces:**
- Consumes: 상세 응답 `summarizingForMs?: number | null`. 키가 없으면 옛 백엔드다.
- Produces (`summary.ts`)
  - 기존 `summaryProgress`, `summaryWatchState`, `SUMMARY_WAIT_LIMIT_MS`(60초), τ 5초는 **바꾸지 않는다**. 옛 백엔드 경로가 쓴다.
  - `export const SUMMARY_QUEUE_LIMIT_MS = 180_000`
  - `export const SUMMARY_RUN_LIMIT_MS = 420_000`
  - `const SUMMARY_RUN_TAU_MS = 10_000`
  - `export function summaryRunProgress(runMs: number): number`: `0.9 × (1 − e^(−runMs/10000))`, 0 이하이면 0
  - `export type SummaryPhase = "queued" | "running" | "legacy"`
  - `export function summaryPhase(summarizingForMs: number | null | undefined): SummaryPhase`: undefined면 legacy, null이면 queued, 숫자면 running
  - `export function summaryWatchStateFor(status: SummaryStatus, phase: SummaryPhase, sinceMountMs: number, runMs: number): SummaryWatchState`
    - 우선순위: DONE → `done`, FAILED → `failed`
    - legacy: `summaryWatchState(status, sinceMountMs)`와 같다
    - queued: `sinceMountMs >= 180_000`이면 `late`
    - running: `runMs >= 420_000`이면 `late`
    - 그 밖은 `watching`

- [ ] **Step 1: `summary.test.ts`에 테스트를 더한다.** 기존 테스트는 그대로 둔다.
  - `summaryPhase`: undefined → `"legacy"`, null → `"queued"`, `0` → `"running"`, `1234` → `"running"`
  - `summaryRunProgress`
    - `(0)`은 0이다.
    - `(10_000)`은 `toBeCloseTo(0.569, 2)`다.
    - 1시간에서도 `0.9` 이하이고, 시간이 갈수록 줄지 않는다.
  - `summaryWatchStateFor`
    - `("PENDING","queued",179_999,0)`은 `"watching"`, `("PENDING","queued",180_000,0)`은 `"late"`다.
    - `("PENDING","running",600_000,200_000)`은 `"watching"`이다. 연 지 오래됐어도 요약 중이면 늦어지지 않는다.
    - `("PENDING","running",0,420_000)`은 `"late"`다.
    - `("PENDING","legacy",60_000,0)`은 `"late"`, `("PENDING","legacy",59_999,0)`은 `"watching"`이다.
    - `("DONE","queued",999_999,0)`은 `"done"`, `("FAILED","running",0,0)`은 `"failed"`다.
- [ ] **Step 2: 테스트가 실패하는지 확인한다**
  - Run: `cd frontend && npx vitest run src/lib/summary.test.ts`
  - Expected: FAIL(내보낸 함수 없음).
- [ ] **Step 3: `summary.ts`, `api.ts`, `SummaryWatcher.tsx`, CSS를 구현한다**
  - **`api.ts`**: `PostDetail`에 `summarizingForMs?: number | null;`을 더한다.
  - **`SummaryWatcher` 상태**
    - `phase`를 둔다. 처음 값은 `"queued"`이고, 첫 응답 전에도 기다림 표시를 보인다.
    - 마지막 응답의 `summarizingForMs`와 받은 시각을 둔다.
  - **확인 주기**
    - 첫 확인은 마운트하자마자 한다(`setTimeout(check, 0)`).
    - 그 뒤로는 `SUMMARY_POLL_MS`(3초)마다 한다.
  - **응답을 받으면**
    - `summaryPhase(post.summarizingForMs)`로 phase를 정한다. `"summarizingForMs" in post`가 거짓이면 undefined로 본다.
  - **요청이 실패하면**
    - 직전 phase와 직전 값을 그대로 둔다. 상태를 다시 판정할 때는 그 값에 지난 시간을 더해 쓴다.
  - **막대**
    - running: `summaryRunProgress(마지막 값 + (지금 − 받은 시각))`
    - legacy: `summaryProgress(연 뒤 흐른 시간)`
    - queued: 막대를 채우지 않고 기다림 표시를 보인다. 같은 트랙 안에서 CSS 애니메이션 클래스(예: `.queued`)로 만든다. 움직임 줄이기에서는 기존 규칙대로 트랙을 숨긴다.
  - **문구**
    - watching + queued: `요약 차례를 기다리고 있어요`
    - watching + running 또는 legacy: `요약하는 중이에요`
    - `done`, `late`, `failed`는 지금 그대로다.
  - **숨김 정리**: `useLayoutEffect` 정리 함수에서 state, progress와 함께 phase를 `"queued"`로, 마지막 값과 받은 시각을 비움으로 되돌린다.
  - 컴포넌트 javadoc 주석을 새 동작에 맞게 고친다.
- [ ] **Step 4: 프론트 전체 검증을 돌린다**
  - Run: `cd frontend && npm test && npm run lint && npm run build`
  - Expected: 테스트 모두 통과, lint 경고 없음, build 성공.
  - build가 localhost:8080 백엔드를 부르더라도 서버를 새로 띄우지 않는다.

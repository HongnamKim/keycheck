# 배포 전 코드 변경 구현 계획

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 설계 7절의 배포 전 코드 변경(운영 설정, 비용·악용 상한 넷, 프론트 장애 처리, Docker·배포 파일)을 만든다. 실제 서버·클라우드 작업은 하지 않는다.

**Architecture:** 백엔드는 설정을 환경변수 자리로 열고(actuator health, Swagger 토글, Hikari 풀, CORS), 상한 넷은 메모리 안 카운터·세마포어로 둔다(한 대·한 프로세스 전제). 프론트는 백엔드 호출에 시간 제한과 서버 전용 헤더를 붙이고, 목록·상세가 백엔드 실패에도 한국어 안내를 보인다. 배포는 Docker 이미지(GHCR) + 서버 타이머가 가져가는 방식.

**Tech Stack:** Spring Boot 4 / Java 21, Spring Boot Actuator, Next.js 16, Docker, docker compose, Caddy 2, systemd timer, GitHub Actions.

**Spec:** `docs/superpowers/specs/2026-11-07-deploy-design.md`

## Global Constraints

- 커밋·푸시 금지(사용자가 말할 때만). 새 파일은 만들자마자 `git add`. `backend/.env`를 읽거나 출력하지 않는다. 클라우드(AWS·Cloudflare·Vercel·Supabase·GitHub 설정)를 바꾸는 명령은 실행하지 않는다.
- 자동 테스트는 실제 geekhack·Anthropic·R2를 부르지 않는다. 로컬 DB를 쓰는 임시 서버·컨테이너는 수집 경계·게시판 상태를 바꾸는 설정으로 띄우지 않는다(메모리 local-db-test-servers).
- 운영 주소: 프론트 `https://keycheck.kr`, 백엔드 `https://api.keycheck.kr`, 축소본 `https://img.keycheck.kr`. 이미지 `ghcr.io/hongnamkim/keycheck-backend`.
- 상한 기본값: 하루 요약 300건(한국 시간 자정 초기화), 틀 글 채우기 동시 3, 채우기 실패 쿨다운 10분, 조회수 갱신 시간당 60회, 30일 넘게 댓글 없는 글은 갱신 간격 24시간(그 밖은 지금처럼 1시간). 모두 `keycheck.*` 설정값.
- 화면 문구 해요체, "해외" 금지. Java 규칙(record 금지 등)은 CLAUDE.md.
- 검증: 백엔드 `cd backend && ./gradlew test`, 프론트 `cd frontend && npm test && npm run lint && npm run build`.

## Review Focus

1. **상한이 정상 사용을 막는 경우**(사용자 한 명이 틀 글 하나를 열 때 동시 상한·쿨다운에 걸리지 않음, 하루 상한 도달 뒤 다음 날 자동 재개) → Task 2 테스트.
2. **백엔드가 멈췄거나 느릴 때 화면**(목록·상세·메인이 8초 안에 한국어 안내, 헤더 유지) → Task 3 테스트와 화면 확인.
3. **환경변수 누락**(Vercel에서 `NEXT_PUBLIC_API_BASE_URL` 없음 → 빌드 실패, 서버 `.env` properties 함정 → CORS가 placeholder로 적용) → Task 1·3.
4. **비밀값이 이미지·로그·저장소에 들어감**(`.dockerignore`에 `.env`, compose의 env_file 경로, Actions에 비밀값 없음) → Task 4 확인.
5. **서버 갱신 타이머가 이미지를 못 받거나 같은 이미지를 계속 재시작** → Task 4 스크립트(이미지 해시가 바뀔 때만 up -d) 확인.

---

### Task 1: 백엔드 운영 설정

**Files:** `backend/build.gradle`, `backend/src/main/resources/application.yml`, `backend/src/test/resources/application.properties`(필요하면), `README.md`, `backend/.env.example`; Test `backend/src/test/java/com/keycheck/OpsConfigTest.java`

- [ ] **Step 1: 실패하는 테스트** — `OpsConfigTest`(MockMvc, `@SpringBootTest`): `GET /actuator/health` → 200, `$.status == "UP"`, 응답에 `components`가 없음(상세 숨김); `GET /actuator/env` → 404(노출 안 함). 별도 클래스 `SwaggerDisabledTest`(`properties = "springdoc.api-docs.enabled=false"`, `"springdoc.swagger-ui.enabled=false"`): `GET /v3/api-docs` → 404.
- [ ] **Step 2: 실패 확인** → FAIL.
- [ ] **Step 3: 구현**
  - `spring-boot-starter-actuator`. `management.endpoints.web.exposure.include: health`, `management.endpoint.health.show-details: never`.
  - `springdoc.api-docs.enabled: ${SPRINGDOC_ENABLED:true}`, `springdoc.swagger-ui.enabled: ${SPRINGDOC_ENABLED:true}`(운영은 false).
  - `spring.datasource.hikari.maximum-pool-size: ${DB_POOL_SIZE:10}`(운영 5).
  - `keycheck.cors.allowed-origins: ${KEYCHECK_CORS_ALLOWED_ORIGINS:http://localhost:3000}`(placeholder로 바꿔 서버 `.env`에서도 적용되게).
  - `server.shutdown: graceful`.
  - README 환경변수 표에 `SPRINGDOC_ENABLED`, `DB_POOL_SIZE`, CORS 설명(브라우저 호출 네 가지 모두), `/actuator/health`.
- [ ] **Step 4: 통과 확인** — `./gradlew test` 전체 PASS(OpenApiTest 회귀 없음).

### Task 2: 비용·악용 상한 넷

**Files:** `summary/config/SummaryProperties.java`, `summary/service/SummaryWorker.java`, `crawl/service/CrawlService.java`(fillSkeleton), 새 `crawl/config/FillProperties.java`(또는 기존 properties에), `post/service/PostStatsService.java`, 새 `post/config/StatsProperties.java`, `application.yml`; Tests `SummaryWorkerTest`, `PostDetailFillApiTest`(또는 새 `FillLimitTest`), `PostStatsApiTest`

**Interfaces:**
- `keycheck.summary.daily-limit: 300` — 워커가 한국 시간 날짜별로 센다(메모리). 상한에 닿으면 `summarizeNext()`가 false를 돌려주고 경고 로그 한 번. 다음 날 자동 재개. 재시작하면 0부터(설계상 허용 — Anthropic 지출 한도가 최종 안전망).
- `keycheck.fill.max-concurrent: 3`, `keycheck.fill.failure-cooldown: 10m` — `CrawlService.fillSkeleton`이 `Semaphore.tryAcquire()`(대기 없음)로 동시 채우기를 묶고, 실패한 글 ID와 시각을 기억해 쿨다운 동안 geekhack에 다시 요청하지 않는다(틀 그대로 돌려줌). 미리 읽기 경로에는 쿨다운만 적용하지 않는다(미리 읽기는 자체 실패 횟수가 있다) — 동시 상한은 공유.
- `keycheck.stats.hourly-budget: 60`, `keycheck.stats.inactive-after: 30d`, `keycheck.stats.inactive-interval: 24h` — `PostStatsService`가 갱신 전에 (1) 마지막 댓글(`lastPostAt`, 없으면 작성일)이 30일보다 오래면 간격 24시간, 아니면 기존 1시간 (2) 이번 시간(정각 기준 창)의 geekhack 갱신 횟수가 60이면 저장값을 그대로 준다.

- [ ] **Step 1: 실패하는 테스트**
  - `SummaryWorkerTest.stopsAtDailyLimit`: 상한 2, PENDING 3개 → 두 번 요약 뒤 false, 시계를 한국 시간 다음 날로 넘기면 세 번째 요약.
  - `fillCooldownSkipsRecentFailure`: 틀 글 채우기가 geekhack 예외로 실패 → 같은 글을 바로 다시 열면 geekhack 요청 0번(틀 그대로 200), 시계를 10분 넘기면 다시 요청.
  - `fillConcurrencyCap`: 동시 상한 1로 두고 첫 채우기가 게이트에서 기다리는 동안(테스트 래치) 다른 틀 글을 열면 기다리지 않고 틀 그대로 돌려준다(geekhack 요청 0). 사용자 한 명이 틀 글 하나를 여는 정상 경우는 채워진다.
  - `statsInactiveInterval`: 마지막 댓글 40일 전·확인 2시간 전 글 → 갱신하지 않음(저장값), 확인 25시간 전 → 갱신. 마지막 댓글 3일 전·확인 2시간 전 → 갱신(기존 1시간 규칙).
  - `statsHourlyBudget`: 예산 2, 서로 다른 글 3개 갱신 요청 → 두 번만 geekhack, 세 번째는 저장값. 다음 정각 창에서 다시 갱신.
- [ ] **Step 2: 실패 확인** → FAIL.
- [ ] **Step 3: 구현** — 위 Interfaces. 시계는 기존 `Clock` 빈, 한국 시간은 `Asia/Seoul`. 카운터는 스레드 안전하게(동기화 또는 Atomic). 설정 기본값은 application.yml에 주석과 함께.
- [ ] **Step 4: 통과 확인** — `./gradlew test` 전체 PASS.

### Task 3: 프론트 장애 처리와 운영 준비

**Files:** `frontend/src/lib/api.ts`, `frontend/src/components/BoardListPage.tsx`(또는 목록 서버 컴포넌트), `frontend/src/app/posts/[id]/page.tsx`, 새 `frontend/src/app/error.tsx`, 새 `frontend/src/app/robots.ts`, `frontend/src/app/layout.tsx`(metadataBase·OG), `frontend/next.config.ts`; Tests `frontend/src/lib/*.test.ts`

**Interfaces:**
- `api.ts`: 모든 백엔드 호출이 한 함수(`apiFetch(path, init?)`)를 거친다. 서버에서 부를 때(`typeof window === "undefined"`)만 `X-Keycheck-Server: process.env.KEYCHECK_SERVER_TOKEN`(값이 있을 때만) 헤더를 붙이고, 모든 호출에 시간 제한 8초(`AbortSignal.timeout`, 호출자가 넘긴 signal과 `AbortSignal.any`로 합침). 순수 함수 `apiRequestInit(isServer: boolean, token: string | undefined, signal?: AbortSignal): RequestInit`를 두고 테스트한다.
- `next.config.ts`: `process.env.VERCEL`이 있고 `NEXT_PUBLIC_API_BASE_URL`이 비면 throw(빌드 실패).
- `robots.ts`: 모든 크롤러 허용, AI 학습 크롤러(`GPTBot`, `ClaudeBot`, `anthropic-ai`, `CCBot`, `Google-Extended`, `PerplexityBot`, `Bytespider`) 전체 차단. `sitemap`은 넣지 않는다.
- 목록(/ic, /gb)·상세: 서버에서 백엔드 호출이 실패하면(`unstable_rethrow` 뒤) 한국어 안내 "글을 불러오지 못했어요. 잠시 후 다시 시도해 주세요." + 같은 주소를 다시 여는 "다시 시도" 링크. 상세는 404(없는 글)와 실패를 구분한다.
- `app/error.tsx`: `"use client"`, 같은 문구 + `retry()`(Next 16 error 파일 규약 — node_modules/next/dist/docs 확인).
- `layout.tsx`: `metadataBase: new URL(process.env.NEXT_PUBLIC_SITE_URL ?? "https://keycheck.kr")`, 기본 openGraph(title·description·siteName "키체크", locale ko_KR).

- [ ] **Step 1: 실패하는 테스트** — `apiRequestInit`: 서버+토큰 → 헤더 있음, 브라우저 → 헤더 없음, 서버+토큰 없음 → 헤더 없음, signal이 시간 제한과 합쳐짐(넘긴 signal을 abort하면 결과 signal도 aborted). `robots.ts` 결과의 rules에 차단 목록.
- [ ] **Step 2: 실패 확인** → FAIL.
- [ ] **Step 3: 구현**.
- [ ] **Step 4: 확인** — `npm test && npm run lint && npm run build` PASS. 화면 확인: 백엔드를 띄우지 않은 채(또는 없는 포트로) `NEXT_PUBLIC_API_BASE_URL=http://localhost:59999 npm run dev -- -p 3001`로 `/`, `/ic`, `/posts/1`을 열어 헤더와 한국어 안내가 보이는지(8초 안), 영어 오류 화면이 없는지 Playwright로 확인하고 끈다.

### Task 4: Docker·배포 파일·CI

**Files:** `backend/Dockerfile`, `backend/.dockerignore`, `deploy/compose.yaml`, `deploy/Caddyfile`, `deploy/keycheck.env.example`, `deploy/update.sh`, `deploy/keycheck-update.service`, `deploy/keycheck-update.timer`, `deploy/README.md`(서버 첫 세팅 절차), `.github/workflows/ci.yml`, `.github/workflows/backend-image.yml`, `README.md`(배포 절)

**Interfaces:**
- Dockerfile: 다단계. 1단계 `eclipse-temurin:21-jdk`에서 `./gradlew bootJar -x test`(테스트는 CI가 따로), 2단계 `eclipse-temurin:21-jre`(glibc, Alpine 금지)에 jar만 복사, 비루트 사용자, `ENTRYPOINT java $JAVA_OPTS -jar /app/app.jar`, 기본 `JAVA_OPTS="-Xms256m -Xmx896m -XX:MaxMetaspaceSize=256m -XX:+ExitOnOutOfMemoryError"`, `EXPOSE 8080`. `.dockerignore`에 `.env`, `build`, `.gradle`, `.idea`.
- compose: `app`(image `ghcr.io/hongnamkim/keycheck-backend:${KEYCHECK_TAG:-latest}`, `env_file: /etc/keycheck/keycheck.env`, `restart: unless-stopped`, `mem_limit: 1400m`, 로그 `json-file` max-size 10m·max-file 3, 포트 미공개), `caddy`(`caddy:2`, 80·443, `Caddyfile`·`caddy_data`·`caddy_config` 볼륨, restart). Caddyfile: `api.keycheck.kr { reverse_proxy app:8080 }`.
- `update.sh`: `docker compose pull app` 후 실행 중 컨테이너의 이미지 ID와 받은 이미지 ID가 다를 때만 `docker compose up -d app`, 오래된 이미지 정리(`docker image prune -f`), 결과를 journal에 남김. 타이머 3분.
- `keycheck.env.example`: 설계 4절 운영 변수 전부(값 자리표시, 주석), 비밀값 없음.
- CI `ci.yml`: pull_request와 push(dev, main)에 백엔드 `./gradlew test`, 프론트 `npm ci && npm test && npm run lint && npm run build`(`NEXT_PUBLIC_API_BASE_URL`은 더미).
- `backend-image.yml`: main push(backend/** 변경) 때 테스트 → `docker/build-push-action`으로 `ghcr.io/hongnamkim/keycheck-backend:{sha}`와 `:latest` push(`GITHUB_TOKEN`, `packages: write`). 서버 접속 없음.
- `deploy/README.md`: Lightsail 첫 세팅(Docker 설치, `/etc/keycheck/keycheck.env` 600, `/opt/keycheck`에 compose·Caddyfile, 타이머 설치·켜기, 되돌리기 `KEYCHECK_TAG=<sha>`), GHCR 패키지를 공개로 바꾸는 절차, Lightsail 방화벽(80·443·브라우저 SSH).

- [ ] **Step 1: 파일 작성**.
- [ ] **Step 2: 로컬 확인** — `docker build -t keycheck-backend:local backend` 성공. 컨테이너를 로컬 DB에 붙여 `SUMMARIZER=fake KEYCHECK_SUMMARY_ENABLED=false CRAWL_ENABLED=false THUMBNAIL_ENABLED=false PREFETCH_ENABLED=false SPRINGDOC_ENABLED=false`, `DB_URL=jdbc:postgresql://host.docker.internal:5432/keycheck`, 포트 8090으로 띄워 `/actuator/health` 200, `/api/posts?board=ic` 200, `/v3/api-docs` 404 확인 후 컨테이너 삭제. 컨테이너 안에서 WebP 네이티브 로딩 확인: `docker run --rm --entrypoint java keycheck-backend:local -cp /app/app.jar ...`가 번거로우면 ImageIO WebP writer가 잡히는지 간단한 jshell 대신 생략하고 deviations에 적는다. 워크플로 YAML 문법 확인(`actionlint`가 없으면 python yaml 파싱). compose 문법 `docker compose -f deploy/compose.yaml config`(env_file 경로가 없으면 임시 파일로).
- [ ] **Step 3: 확인** — 비밀값이 이미지에 없는지(`docker history`, 이미지 안 `/app`에 `.env` 없음).

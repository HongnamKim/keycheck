# 배포 설계

작성 2026-11-07. 점검 보고(개발용, git 제외): `.superpowers/work/deploy/{backend,frontend,cost,security}.md`.

## 1. 왜, 무엇을

- MVP(IC·공동구매 수집, 한국어 요약, 메인 매거진, 축소본, 한국 벤더)를 처음으로 공개한다. 데이터는 운영에서 처음부터 새로 수집한다.
- 이미지가 많고 LLM 호출이 있어, 공개하자마자 생길 수 있는 비용·가용성 위험을 배포 전에 막는다.

## 2. 목표와 비목표

**목표**
- `https://keycheck.kr`(프론트), `https://api.keycheck.kr`(백엔드), `https://img.keycheck.kr`(축소본)로 운영한다.
- main 브랜치에 합치면 자동으로 배포된다(백엔드 GitHub Actions, 프론트 Vercel Git 연동).
- 봇·악용이 있어도 Claude 비용과 서버가 정해진 한도 안에 머문다.

**비목표**
- 여러 대·무중단 배포, 컨테이너 오케스트레이션(ECS·쿠버네티스), 로드밸런서. 다만 나중에 옮기기 쉽도록 컨테이너(Docker)로 띄운다(4절).
- 백엔드 장애 시 화면용 캐시(메모리에 있던 과제 — 점검 결과 Vercel에서 효과가 약하고 상세 동작을 깨므로 나중).
- 디스코드 국내 판매(나중).
- 로그인.

**하면 안 되는 것**
- 비밀 키를 저장소·명령줄·로그에 남기는 것.
- 한 번이라도 가짜 요약기(`SUMMARIZER` 빠뜨림)로 운영 DB에 쓰는 것.
- Supabase Data API를 켜 둔 채 공개하는 것.

## 3. 구성 (사용자 결정 포함)

| 부분 | 서비스 | 주소 |
|---|---|---|
| 도메인 | 가비아에서 `keycheck.kr` 구매, 네임서버는 Cloudflare(무료) — 사용자 결정 | |
| 프론트 | Vercel Hobby(+Analytics), 함수 리전 서울 icn1 — 사용자 결정 | `keycheck.kr` (`www`는 `keycheck.kr`로 넘김) |
| 백엔드 | AWS Lightsail 서울, Ubuntu LTS, **2GB** 요금제(축소본 작업 메모리), 고정 IP — 사용자 결정(서울·한 대), 크기는 Claude 판단 | `api.keycheck.kr` |
| DB | Supabase 무료, **서울 리전**, 세션 풀러 | |
| 축소본 | Cloudflare R2, **운영 전용 버킷**과 토큰 | `img.keycheck.kr` |
| 요약 | Anthropic, 운영 전용 워크스페이스·키, 월 지출 한도 | |

## 4. 백엔드 실행

- 형태: **Docker**(사용자 결정 — 규모가 커지면 ECS 등으로 옮기기 쉽게). 서버에는 Docker Engine과 compose만 둔다. `docker compose`로 두 컨테이너를 띄운다.
  - `app`: 백엔드 이미지. 바탕은 glibc 기반 `eclipse-temurin:21-jre`(축소본 WebP 라이브러리가 glibc용 .so라 Alpine은 쓰지 않는다). 포트는 밖으로 열지 않고 compose 내부 네트워크에서만 8080.
  - `caddy`: `caddy:2` 이미지, 80·443을 열고 Let's Encrypt 인증서를 볼륨에 보관, `api.keycheck.kr` → `app:8080`.
- 재시작: `restart: unless-stopped`. JVM `-Xms256m -Xmx896m -XX:MaxMetaspaceSize=256m -XX:+ExitOnOutOfMemoryError`(OOM이면 컨테이너가 죽고 Docker가 다시 띄운다 — 축소본 작업이 "죽은 횟수도 실패로 센다"를 전제로 함). 컨테이너 메모리 상한 약 1.4GB.
- 로그: Docker 기본 로그는 크기 제한이 없으므로 `json-file`에 `max-size`·`max-file`을 건다.
- 비밀값: compose `env_file: /etc/keycheck/keycheck.env`(root 소유, 600)로 **실제 환경변수**로 넣는다. 이미지·저장소에 넣지 않고, 컨테이너 작업 폴더에 `.env`를 두지 않는다(.env를 properties로 읽으면 대문자 환경변수가 적용되지 않는 함정 — 점검 보고 backend 1절).
- 환경변수(운영): `DB_URL`(세션 풀러 + `sslmode=require`), `DB_USERNAME`, `DB_PASSWORD`, `DB_POOL_SIZE=5`, `KEYCHECK_CORS_ALLOWED_ORIGINS=https://keycheck.kr`, `SUMMARIZER=claude`, `ANTHROPIC_API_KEY`, `CRAWL_ENABLED=true`, `THUMBNAIL_ENABLED=true`, `PREFETCH_ENABLED=false`(아래 7절), `R2_*`(운영 버킷), `R2_PUBLIC_URL=https://img.keycheck.kr`, Swagger 끔, 아래 6절 상한 값.
- 방화벽: 80, 443만 연다. 22번은 **Lightsail 브라우저 SSH만 허용**(사용자 결정 — 사용자 IP가 자주 바뀌므로 IP로 열지 않는다. 서버 관리는 AWS 콘솔의 브라우저 터미널로). 앱 포트는 compose 내부에만 있다.
- 첫 기동 전 Supabase `public` 스키마를 비워 둔다(Flyway가 V1~V11을 적용).

## 5. 배포 흐름

- 브랜치: `feature/mvp` → `dev`(PR) → `main`(PR) — CLAUDE.md 규칙. main에 합치면 배포.
- 백엔드(**서버가 가져가는 방식** — 사용자 결정): GitHub Actions가 main push 때 테스트 → `bootJar` → Docker 이미지 빌드 → **GHCR**(GitHub 이미지 저장소)에 커밋 해시 태그와 `latest`로 올린다. 서버의 systemd 타이머가 몇 분마다(예: 3분) `latest`를 확인해 바뀌었으면 `docker compose pull && docker compose up -d`. Actions는 서버에 접속하지 않으므로 SSH 키·서버 주소를 GitHub에 두지 않는다. 저장소가 공개이므로 이미지도 공개로 두어 서버에 GHCR 토큰이 필요 없다(이미지에는 비밀값이 없고 비밀값은 서버 환경변수 파일에만 있다). 되돌리기는 compose의 이미지 태그를 이전 커밋 해시로 고정한다(타이머가 그 태그를 유지). 반영은 합친 뒤 몇 분 늦을 수 있다. 서버에서 빌드하지 않는다(메모리).
- 프론트: Vercel이 GitHub 저장소 main을 바로 배포한다(Root Directory `frontend`). `NEXT_PUBLIC_API_BASE_URL=https://api.keycheck.kr`는 빌드 때 박히므로 Vercel Production 환경변수에 먼저 넣는다. 값이 없으면 빌드가 실패하게 한다.
- 사용자 몫: gh·aws 로그인, 가비아 구매와 네임서버 변경, Cloudflare·Vercel·Supabase·Anthropic 대시보드 작업(키 발급은 사용자가 서버·Secrets에 직접 넣는다). Claude 몫: 코드·설정 파일·Actions 작성, CLI로 할 수 있는 Lightsail 세팅. 요금이 생기거나 되돌리기 어려운 작업은 실행 직전 확인(메모리 규칙).

## 6. 비용·악용 방어

- **백엔드 안의 상한(배포 전, 코드)**
  - 하루 Claude 요약 상한(예: 300건). 넘으면 다음 날(한국 시간)까지 쉰다. 설정값.
  - 틀 글 채우기 동시 대기 상한(예: 3). 넘치면 기다리지 않고 틀 그대로 돌려준다 — 상세 요청이 서버 스레드를 묶어 사이트가 멈추는 것을 막는다.
  - 채우기에 실패한 글은 10분간 다시 geekhack에 요청하지 않는다.
  - 조회수 갱신(`/api/posts/{id}/stats`) 상한(검토 후 Claude 판단 — 빠뜨렸던 경로): 시간당 geekhack 요청 예산(예: 60회)을 넘으면 저장값을 그대로 준다. 마지막 댓글이 30일 넘게 없는 글은 갱신 간격을 1시간에서 24시간으로 늘린다. 오래된 글을 돌아가며 열어 geekhack에 하루 종일 요청하게 만드는 것(차단 위험)을 막는다.
- **Anthropic 월 지출 한도**(예: $20) — 사용자가 콘솔에서.
- **Cloudflare(api.keycheck.kr만 프록시)**: 무료 요금제의 속도 제한 규칙은 한 개, 식에는 경로만, IP별 10초 단위다(요청 헤더를 식에 못 쓴다 — Cloudflare 문서 2026-11-07 확인). 그래서 두 규칙으로 둔다. (1) 사용자 지정 규칙: 호스트가 `api.keycheck.kr`이고 비밀 헤더(`X-Keycheck-Server`, 화면 서버(Vercel)만 붙임)가 토큰과 같으면 Skip — 속도 제한 규칙, Browser Integrity Check, Security Level(무료 플랜 식 편집기에서 헤더 필드가 되는지는 콘솔에서 확인). (2) 속도 제한 규칙: 경로가 `/api/`로 시작하면 IP별 10초에 30회(예), 넘으면 10초 차단. 한국 이동통신은 여러 사용자가 IP 하나를 함께 쓰므로(CGNAT) 빡빡하게 두지 않는다. 절차는 `deploy/README.md` "공개 전 Cloudflare 설정". Bot Fight Mode는 Vercel 서버 요청까지 막고 무료 요금제에서 예외를 못 두므로 쓰지 않는다. 백엔드는 Cloudflare 프록시 IP만 받도록 Lightsail 방화벽 443을 Cloudflare IP 대역으로 좁힌다(Claude 판단, 나중에 해도 됨).
- **Vercel**: Hobby에서 되는 봇 보호·방화벽을 켠다. 켠 뒤 카카오톡·디스코드 링크 미리보기가 뜨는지 확인한다(카카오톡 수집기는 확인된 봇 목록에 없을 수 있다).
- **robots.txt**: 검색 엔진은 허용하고 AI 학습 크롤러(GPTBot, ClaudeBot, CCBot 등)는 막는다.
- 상한 근거: 2020년 이후 글 약 5,100개를 전부 한 번 요약해도 약 $35~80, 같은 글은 다시 요약하지 않는다(점검 보고 cost 4절).

## 7. 배포 전 코드 변경

1. 위 6절 백엔드 상한 넷(요약 일일 상한, 채우기 동시 대기, 실패 쿨다운, 조회수 갱신 예산).
2. 프론트: `/ic`·`/gb`·상세에 백엔드 실패 처리(한국어 안내 + 다시 시도), `app/error.tsx`, 백엔드 호출 시간 제한(8초, 상세는 처음 여는 틀 글 채우기 — 요청 차례 5초 + geekhack 10초 — 를 기다리도록 15초), Vercel 운영 배포에서 `NEXT_PUBLIC_API_BASE_URL` 없으면 빌드 실패(미리보기 배포는 막지 않음), Vercel 서버 요청에 비밀 헤더, `robots.txt`, 메타데이터(OG) 점검, Vercel Analytics(`<Analytics />`), 함수 리전 `frontend/vercel.json`(`icn1`).
3. 백엔드: Swagger 운영 끔, Hikari 풀 크기 설정 자리, 상태 확인 주소(`/actuator/health` 또는 간단한 `/api/health`), README·`.env.example` 운영 항목.
4. 배포 파일: `backend/Dockerfile`(+ `.dockerignore`), 서버용 `compose.yaml`과 `Caddyfile`, 이미지 확인·갱신 systemd 서비스·타이머, GitHub Actions 워크플로(테스트·이미지 빌드·GHCR 올리기), 서버 첫 세팅 절차(문서). 로컬에서 같은 이미지로 띄워 보는 방법도 README에 적는다.

## 8. 첫 공개 순서

1. 코드 변경(7절)을 dev·main에 합친다(아직 배포 대상 없음).
2. Cloudflare에 `keycheck.kr` 연결 확인 → R2 운영 버킷 + `img.keycheck.kr`.
3. Supabase 프로젝트(서울) → Data API 끄기 → SSL 강제 → 연결 문자열.
4. Lightsail 인스턴스·고정 IP·방화벽(80·443, 브라우저 SSH) → DNS `api` → 브라우저 SSH로 Docker·compose 설치, 환경변수 파일, compose·Caddy·타이머 설치 → GitHub Actions가 첫 이미지를 올리면 타이머가 띄움 → `https://api.keycheck.kr/actuator/health` 확인 → Flyway 적용 확인.
5. Vercel 프로젝트(Root Directory `frontend`, Production Branch `main`) → 환경변수 → Web Analytics 켜기 → `keycheck.kr` 연결 → 첫 배포 → 함수 리전이 icn1인지(`x-vercel-id`) 확인. 절차는 README "프론트 첫 세팅 (Vercel)".
6. Cloudflare: 토큰 만들기 → Vercel `KEYCHECK_SERVER_TOKEN`(Production, Sensitive) 넣고 다시 배포 → 사용자 지정 규칙(Skip) → 속도 제한 규칙 → SSL Full (strict) → `api` 프록시 켜기 → 확인(목록·상세가 열리고, 헤더 없이 연달아 부르면 429). robots 확인. Vercel 봇 보호를 켜고 카카오톡·디스코드에 `/posts/{id}` 링크를 붙여 미리보기가 뜨는지 확인(막히면 그 수집기 User-Agent 예외 또는 봇 보호를 경로별로). 그 밖의 공개 전 점검(r2.dev 끄기, Anthropic 지출 한도 등)은 `deploy/README.md` "공개 전 점검".
7. 수집이 첫 0~1쪽을 읽고 요약하는지(Claude 약 $3) 지켜본다. 축소본이 `img.keycheck.kr`로 나오는지 본다.

## 9. 나중

- 미리 읽기를 켤지(켜면 2020년 이후 글 전부 약 $35~80 한 번) — 운영해 본 뒤 사용자가 정한다.
- Supabase 월 전송량(egress): 정기 수집이 글 본문까지 읽어 월 약 1.8GB — 무료 5GB 안이지만 방문이 늘면 수집·목록 쿼리에서 본문 빼기.
- Anthropic WIF(키 없는 인증): Lightsail에서는 신원 공급자가 마땅치 않아 키로 시작한다.
- 백엔드 장애 시 화면 캐시.

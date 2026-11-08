# 키체크 (keycheck)

geekhack의 키보드·키캡 인터레스트 체크(IC)와 공동구매(GB) 글을 한국어 핵심 요약, 대표 이미지, 원문 링크로 모아 보는 읽기 전용 서비스입니다.

현재 첫 버전(MVP)을 만드는 중입니다. 요약은 기본으로 가짜 요약기가 만들고, 설정하면 Claude가 만듭니다.

## 스택

- 프론트엔드: Next.js 16 (App Router, TypeScript)
- 서버: Spring Boot 4, Java 21 (Gradle)
- DB: PostgreSQL 17 (로컬은 Docker Compose)

## 로컬 실행

필요한 것: Docker, JDK 21, Node.js 22.12 이상 (그보다 낮은 22.x에서는 `npm test`의 Vitest가 동작하지 않습니다)

### 1. DB

```bash
docker compose up -d
```

`localhost:5432`에 `keycheck` DB가 뜹니다(계정·비밀번호 모두 `keycheck`). 스키마는 백엔드가 기동할 때 Flyway로 만듭니다.

### 2. 백엔드

```bash
cd backend
./gradlew bootRun
```

`http://localhost:8080`에서 뜹니다. API 문서는 `http://localhost:8080/swagger-ui/index.html`에 있습니다(`SPRINGDOC_ENABLED=false`면 꺼집니다). 상태 확인은 `http://localhost:8080/actuator/health`이고, 앱과 DB 연결이 정상이면 200과 `{"status":"UP"}`만 돌려줍니다(세부 항목은 숨기고, 다른 actuator 주소는 열지 않습니다).

수집은 기본으로 꺼져 있습니다. 켜면 기동 직후와 이후 30분마다 실제 geekhack에 요청합니다(요청 간격 2초).

```bash
CRAWL_ENABLED=true ./gradlew bootRun
```

| 환경변수 | 기본값 | 설명 |
|---|---|---|
| `CRAWL_ENABLED` | `false` | geekhack 정기 수집 |
| `PREFETCH_ENABLED` | `false` | 틀 글 미리 읽기. 본문을 아직 읽지 않은 글을 10초에 한 건씩, 요청 간격이 비어 있을 때만 geekhack에서 읽는다. 채운 글은 요약되므로 `SUMMARIZER=claude`면 글당 약 1.1센트 |
| `DB_URL` | `jdbc:postgresql://localhost:5432/keycheck` | DB 주소 |
| `DB_USERNAME`, `DB_PASSWORD` | `keycheck` | DB 계정 |
| `DB_POOL_SIZE` | `10` | DB 연결 풀 크기. 배포 DB(Supabase 세션 풀러)는 연결 수가 적어 운영은 `5` |
| `KEYCHECK_CORS_ALLOWED_ORIGINS` | `http://localhost:3000` | 브라우저에서 백엔드를 부를 수 있는 프론트 주소. 쉼표로 여러 개, 끝에 `/` 없이 정확히 쓴다. 브라우저가 직접 부르는 요청은 목록 다음 묶음, 상세 화면의 조회수 갱신, 요약을 기다리는 동안 상세 다시 읽기, 상세 하단 앞뒤 목록 네 가지이고, 주소가 맞지 않으면 모두 막힌다 |
| `SPRINGDOC_ENABLED` | `true` | API 문서(`/v3/api-docs`)와 Swagger UI. 운영은 `false` |
| `SUMMARIZER` | `fake` | 요약기. `claude`면 Claude API(기본 모델 `claude-sonnet-5-5`, `KEYCHECK_CLAUDE_MODEL=claude-haiku-5-5`로 변경 가능)로 분류·요약한다 |
| `ANTHROPIC_API_KEY` | (없음) | `SUMMARIZER=claude`일 때 쓰는 API 키. 없으면 SDK 기본 인증 순서를 따른다 |
| `R2_ENDPOINT` | (없음) | 이미지 축소본 저장소(Cloudflare R2)의 S3 API 주소 `https://<계정ID>.r2.cloudflarestorage.com`. 이 값과 `R2_BUCKET`·`R2_ACCESS_KEY_ID`·`R2_SECRET_ACCESS_KEY` 중 하나라도 비면 축소본을 만들지 않고 화면은 원본 이미지를 쓴다 |
| `R2_BUCKET` | (없음) | 축소본 버킷 이름 |
| `R2_ACCESS_KEY_ID` | (없음) | R2 API 토큰의 Access Key ID |
| `R2_SECRET_ACCESS_KEY` | (없음) | R2 API 토큰의 Secret Access Key |
| `R2_PUBLIC_URL` | (없음) | 브라우저가 축소본을 받는 공개 주소 앞부분. 로컬은 버킷의 `r2.dev` 주소, 배포는 연결한 도메인. DB에는 객체 이름만 두고 응답할 때 이 주소를 붙인다. 비어 있으면 축소본을 만들어도 화면은 원본을 쓴다(축소본 작업을 켜면 경고를 남긴다) |
| `THUMBNAIL_ENABLED` | `false` | 이미지 축소본 작업. 축소본이 없는 이미지를 3초에 한 장씩 원본 호스트에서 받아 WebP(가로 640px, 대표 이미지는 1280px도)로 줄여 R2에 올린다. geekhack 첨부는 요청 간격이 비어 있을 때만 받는다. R2 값이 없으면 돌지 않는다 |
| `NOTICE_ENABLED` | `false` | 디스코드 판매 공지 수집·첨부 보관 작업. 5분마다 채널의 새 메시지를 읽어 저장하고 첨부를 R2에 올린다. 봇 토큰과 채널 id, R2 값이 있어야 한다 |
| `DISCORD_BOT_TOKEN` | (없음) | 판매 공지 채널을 읽는 디스코드 봇 토큰. 비밀값이라 `backend/.env`에만 넣는다 |
| `SWAGKEY_CHANNEL_ID` | (없음) | Swagkeys 공지 채널 id. 비면 그 판매처는 수집하지 않는다 |
| `GEONWORKS_CHANNEL_ID` | (없음) | GeonWorks 공지 채널 id. 비면 그 판매처는 수집하지 않는다 |
| `NOTICE_SUMMARY_ENABLED` | `false` | 공지 요약 작업. `SUMMARIZER=claude`면 공지마다 Claude 비용이 든다. 로컬에서 가져온 공지를 확인 없이 요약하지 않도록 기본은 끈다 |

새 글은 요약 대기로 저장되고, 백그라운드 작업이 몇 초마다 한 건씩 요약합니다. 실제 요약을 쓰려면 `backend/.env.example`을 `backend/.env`로 복사해 키를 넣고 `SUMMARIZER=claude`를 지정합니다. `backend/.env`는 커밋하지 않습니다.

```bash
cp backend/.env.example backend/.env   # ANTHROPIC_API_KEY 값을 넣는다
cd backend && SUMMARIZER=claude ./gradlew bootRun
```

틀 글 미리 읽기는 한 글을 3번 읽지 못하면 그 글을 더 시도하지 않습니다(상세를 열면 계속 채웁니다). geekhack 장애처럼 글 탓이 아닌 실패로 빠진 글을 다시 넣으려면 DB에서 실패 횟수를 되돌립니다.

```sql
update post set prefetch_failures = 0 where detail_fetched_at is null;
```

이미지 축소본은 R2 버킷의 `thumbs/` 아래에, 디스코드 공지 첨부는 `notices/` 아래에 올립니다. 로컬 개발도 실제 R2 버킷에 연결합니다(R2 값은 `backend/.env`에 넣고 커밋하지 않습니다). 자동 테스트는 이 값이 있어도 R2를 부르지 않습니다.

축소본 작업은 이미지 한 장을 처리하는 동안 메모리를 많이 씁니다. 힙을 많게는 300MB 가까이(원본 25MB, 줄여 읽은 장면, 축소본과 인코딩 사본), 점진적 JPEG이면 힙 밖에 계수 버퍼를 128MB까지 더 씁니다. JVM 기본 최대 힙은 서버 메모리의 1/4이므로, 메모리가 작은 서버에서 켤 때는 힙을 늘립니다(예: `-XX:MaxRAMPercentage=60`).

축소본 작업은 한 이미지를 3번 만들지 못하면(원본을 받지 못함, 이미지로 읽지 못함, 만들다 프로세스가 죽음) 더 시도하지 않고 화면은 원본을 씁니다. 원본 호스트의 일시 장애나 서버 네트워크 문제처럼 이미지 탓이 아닌 실패로 빠진 이미지를 다시 넣으려면 실패 횟수만 되돌립니다(완료된 축소본은 그대로 둡니다).

```sql
update post_image set thumb_failures = 0 where thumb_key is null and thumb_failures >= 3;
```

배포 전 정리:

- 버킷의 `thumbs/` 객체를 모두 지웁니다(로컬에서 만든 테스트 데이터). 배포 DB는 축소본 상태가 처음부터 "없음"이라 작업이 다시 만듭니다.
- 배포 뒤 로컬 개발을 이어갈 때는 개발용 버킷을 따로 둡니다. 같은 버킷을 쓰면 로컬과 배포의 같은 이미지 번호가 서로의 축소본을 덮어씁니다.

로컬 DB를 배포 DB로 옮겼는데 버킷을 비웠다면, 축소본 상태를 "아직 없음"으로 되돌려 작업이 다시 만들게 합니다.

```sql
update post_image set thumb_key = null, thumb_large_key = null, thumb_failures = 0;
```

### 지난 디스코드 공지 가져오기 (일회성)

봇이 켜지기 전의 공지는 디스코드에서 손으로 옮겨 `messages.json`과 `attachments/` 폴더가 있는 묶음으로 바꿔 둡니다(변환 스크립트는 저장소에 없습니다). 묶음은 봇과 같은 길(저장 + 첨부 보관)로 넣고, 요약은 평소 요약 작업이 나중에 합니다. R2 값이 있어야 하며, 이미 있는 공지는 건너뛰고 보관이 덜 끝난 첨부만 이어서 끝내므로 여러 번 실행해도 안전합니다. 실행 중에는 수집·요약 작업이 모두 꺼집니다.

```bash
cd backend
NOTICE_IMPORT_DIR=/path/to/swagkeys NOTICE_IMPORT_VENDOR=SWAGKEYS \
  ./gradlew bootRun --args='--spring.profiles.active=notice-import'
```

끝나면 가져온 건수, 건너뛴 건수, 보관한 첨부와 실패한 첨부 수를 로그로 남기고 종료합니다(공지 내용은 남기지 않습니다). 운영 서버에서 한 번 실행하는 절차는 [deploy/README.md](deploy/README.md#지난-디스코드-공지-가져오기)입니다.

### 3. 프론트

```bash
cd frontend
npm install
npm run dev
```

`http://localhost:3000`에서 뜹니다. 백엔드 주소가 다르면 `NEXT_PUBLIC_API_BASE_URL`(기본 `http://localhost:8080`)을 지정합니다. 이 값은 빌드할 때 들어가므로 바꾸면 다시 빌드해야 합니다.

### 테스트

```bash
cd backend && ./gradlew test                          # Docker 필요 (Testcontainers)
cd frontend && npm test && npm run lint && npm run build
```

자동 테스트는 실제 geekhack, 디스코드, Claude API, R2를 호출하지 않습니다.

## 배포

| 부분 | 서비스 | 주소 |
|---|---|---|
| 프론트 | Vercel (Root Directory `frontend`, 함수 리전 서울 `icn1`) | `https://keycheck.kr` |
| 백엔드 | AWS Lightsail 서울, Docker (`app` + Caddy) | `https://api.keycheck.kr` |
| DB | Supabase (서울, 세션 풀러) | |
| 축소본 | Cloudflare R2 운영 전용 버킷 | `https://img.keycheck.kr` |

- `main`에 합치면 배포됩니다. 프론트는 Vercel이 바로 배포합니다. 백엔드는 GitHub Actions(`.github/workflows/backend-image.yml`)가 테스트 뒤 이미지를 `ghcr.io/hongnamkim/keycheck-backend`에 커밋 해시와 `latest` 태그로 올리고, 서버 타이머가 3분마다 확인해 바뀌었으면 백엔드만 다시 띄웁니다(합친 뒤 몇 분 늦을 수 있습니다).
- PR과 `dev`·`main` push마다 `.github/workflows/ci.yml`이 백엔드·프론트 테스트를 돌립니다.
- 서버 첫 세팅, 운영 환경변수, 되돌리기, 공개 전 점검(Cloudflare 등): [deploy/README.md](deploy/README.md)

### 같은 이미지를 로컬에서 띄우기

```bash
docker build -t keycheck-backend:local backend
docker run --rm -p 8090:8080 \
  -e DB_URL=jdbc:postgresql://host.docker.internal:5432/keycheck \
  -e KEYCHECK_SUMMARY_ENABLED=false \
  keycheck-backend:local
```

`http://localhost:8090/actuator/health`로 확인합니다. 이미지에는 `backend/.env`가 들어가지 않아 가짜 요약기로 뜨므로 요약 작업을 끕니다(켜 두면 로컬 DB의 요약 대기 글에 가짜 요약이 남습니다). 수집·미리 읽기·축소본은 기본으로 꺼져 있습니다. 로컬은 Apple Silicon이면 arm64 이미지가 만들어지고, 운영 이미지는 Actions가 amd64로 만듭니다.

### 프론트 첫 세팅 (Vercel)

1. 프로젝트: Root Directory `frontend`, Production Branch `main`. 함수 리전은 `frontend/vercel.json`이 서울(`icn1`)로 정합니다. 상세 화면은 백엔드를 두 번 차례로 부르므로 함수가 미국에서 돌면 느려집니다.
2. 환경변수: 아래 표의 `NEXT_PUBLIC_API_BASE_URL`을 Production에 넣습니다. `KEYCHECK_SERVER_TOKEN`은 [공개 전 Cloudflare 설정](deploy/README.md#공개-전-cloudflare-설정)에서 넣습니다.
3. Analytics 탭에서 Web Analytics를 켭니다(코드는 `layout.tsx`의 `<Analytics />`). 켜기 전의 방문은 남지 않으므로 첫 배포 전에 켭니다.
4. 도메인 `keycheck.kr`을 연결하고 `www`는 `keycheck.kr`로 넘깁니다.
5. 첫 배포 뒤 확인합니다.
   - 함수 리전: `curl -s -o /dev/null -D - https://keycheck.kr/posts/<글 ID> | grep -i x-vercel-id`에서 `::`로 나뉜 두 번째 값(함수가 돈 곳)이 `icn1`인지 봅니다(첫 번째는 요청을 받은 곳). Vercel 대시보드의 배포 상세에서도 함수 리전을 볼 수 있습니다.
   - 방화벽의 봇 보호를 켰다면 카카오톡과 디스코드에 `https://keycheck.kr/posts/<글 ID>` 링크를 붙여 미리보기 카드가 뜨는지 봅니다. 카카오톡 수집기는 Vercel이 아는 봇 목록에 없을 수 있습니다. 안 뜨면 그 수집기의 User-Agent를 예외로 두거나 봇 보호를 끕니다.

### 프론트 환경변수 (Vercel)

| 환경변수 | 기본값 | 설명 |
|---|---|---|
| `NEXT_PUBLIC_API_BASE_URL` | `http://localhost:8080` | 백엔드 주소. 운영은 `https://api.keycheck.kr`. 빌드할 때 코드에 들어가므로 첫 배포 전에 Production 환경변수로 넣는다. Vercel 운영(Production) 배포에서 비어 있으면 빌드가 실패한다. 미리보기(Preview) 배포는 막지 않고, 값이 없으면 화면이 "글을 불러오지 못했어요"로 뜬다(Preview에도 넣으면 화면 서버 호출은 되지만 브라우저 호출은 CORS가 운영 주소만 허용해 실패한다) |
| `KEYCHECK_SERVER_TOKEN` | (없음) | 화면 서버가 백엔드를 부를 때 붙이는 `X-Keycheck-Server` 헤더 값(비밀값). Cloudflare 사용자 지정 규칙이 이 헤더가 맞는 요청을 속도 제한 규칙에서 뺀다([공개 전 Cloudflare 설정](deploy/README.md#공개-전-cloudflare-설정)). 서버에서만 읽고 브라우저 요청에는 붙지 않는다. 없으면 헤더를 붙이지 않는다 |
| `NEXT_PUBLIC_SITE_URL` | `https://keycheck.kr` | 링크 미리보기(OG) 등 메타데이터의 기준 주소 |

## 샘플 이미지

시안에 쓴 이미지의 권리는 원작성자에게 있어 이 저장소에 포함하지 않습니다. 출처는 [docs/sample-image-sources.md](docs/sample-image-sources.md)에 있습니다.

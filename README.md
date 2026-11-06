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

`http://localhost:8080`에서 뜹니다. API 문서는 `http://localhost:8080/swagger-ui/index.html`에 있습니다.

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
| `KEYCHECK_CORS_ALLOWED_ORIGINS` | `http://localhost:3000` | 상세 화면의 조회수 갱신 요청을 허용할 프론트 주소 |
| `SUMMARIZER` | `fake` | 요약기. `claude`면 Claude API(기본 모델 `claude-sonnet-5-5`, `KEYCHECK_CLAUDE_MODEL=claude-haiku-5-5`로 변경 가능)로 분류·요약한다 |
| `ANTHROPIC_API_KEY` | (없음) | `SUMMARIZER=claude`일 때 쓰는 API 키. 없으면 SDK 기본 인증 순서를 따른다 |
| `R2_ENDPOINT` | (없음) | 이미지 축소본 저장소(Cloudflare R2)의 S3 API 주소 `https://<계정ID>.r2.cloudflarestorage.com`. 이 값과 `R2_BUCKET`·`R2_ACCESS_KEY_ID`·`R2_SECRET_ACCESS_KEY` 중 하나라도 비면 축소본을 만들지 않고 화면은 원본 이미지를 쓴다 |
| `R2_BUCKET` | (없음) | 축소본 버킷 이름 |
| `R2_ACCESS_KEY_ID` | (없음) | R2 API 토큰의 Access Key ID |
| `R2_SECRET_ACCESS_KEY` | (없음) | R2 API 토큰의 Secret Access Key |
| `R2_PUBLIC_URL` | (없음) | 브라우저가 축소본을 받는 공개 주소 앞부분. 로컬은 버킷의 `r2.dev` 주소, 배포는 연결한 도메인. DB에는 객체 이름만 두고 응답할 때 이 주소를 붙인다. 비어 있으면 축소본을 만들어도 화면은 원본을 쓴다(축소본 작업을 켜면 경고를 남긴다) |
| `THUMBNAIL_ENABLED` | `false` | 이미지 축소본 작업. 축소본이 없는 이미지를 3초에 한 장씩 원본 호스트에서 받아 WebP(가로 640px, 대표 이미지는 1280px도)로 줄여 R2에 올린다. geekhack 첨부는 요청 간격이 비어 있을 때만 받는다. R2 값이 없으면 돌지 않는다 |

새 글은 요약 대기로 저장되고, 백그라운드 작업이 몇 초마다 한 건씩 요약합니다. 실제 요약을 쓰려면 `backend/.env.example`을 `backend/.env`로 복사해 키를 넣고 `SUMMARIZER=claude`를 지정합니다. `backend/.env`는 커밋하지 않습니다.

```bash
cp backend/.env.example backend/.env   # ANTHROPIC_API_KEY 값을 넣는다
cd backend && SUMMARIZER=claude ./gradlew bootRun
```

틀 글 미리 읽기는 한 글을 3번 읽지 못하면 그 글을 더 시도하지 않습니다(상세를 열면 계속 채웁니다). geekhack 장애처럼 글 탓이 아닌 실패로 빠진 글을 다시 넣으려면 DB에서 실패 횟수를 되돌립니다.

```sql
update post set prefetch_failures = 0 where detail_fetched_at is null;
```

이미지 축소본은 R2 버킷의 `thumbs/` 아래에 올립니다. 로컬 개발도 실제 R2 버킷에 연결합니다(R2 값은 `backend/.env`에 넣고 커밋하지 않습니다). 자동 테스트는 이 값이 있어도 R2를 부르지 않습니다.

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

자동 테스트는 실제 geekhack과 Claude API를 호출하지 않습니다.

## 샘플 이미지

시안에 쓴 이미지의 권리는 원작성자에게 있어 이 저장소에 포함하지 않습니다. 출처는 [docs/sample-image-sources.md](docs/sample-image-sources.md)에 있습니다.

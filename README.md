# 키체크 (keycheck)

해외 키보드·키캡 인터레스트 체크(IC) 소식을 한국어 핵심 요약, 대표 이미지, 원문 링크로 모아 보는 읽기 전용 서비스입니다.

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
| `DB_URL` | `jdbc:postgresql://localhost:5432/keycheck` | DB 주소 |
| `DB_USERNAME`, `DB_PASSWORD` | `keycheck` | DB 계정 |
| `KEYCHECK_CORS_ALLOWED_ORIGINS` | `http://localhost:3000` | 상세 화면의 조회수 갱신 요청을 허용할 프론트 주소 |
| `SUMMARIZER` | `fake` | 요약기. `claude`면 Claude API(기본 모델 `claude-sonnet-5-5`, `KEYCHECK_CLAUDE_MODEL=claude-haiku-5-5`로 변경 가능)로 분류·요약한다 |
| `ANTHROPIC_API_KEY` | (없음) | `SUMMARIZER=claude`일 때 쓰는 API 키. 없으면 SDK 기본 인증 순서를 따른다 |

새 글은 요약 대기로 저장되고, 백그라운드 작업이 몇 초마다 한 건씩 요약합니다. 실제 요약을 쓰려면 `backend/.env.example`을 `backend/.env`로 복사해 키를 넣고 `SUMMARIZER=claude`를 지정합니다. `backend/.env`는 커밋하지 않습니다.

```bash
cp backend/.env.example backend/.env   # ANTHROPIC_API_KEY 값을 넣는다
cd backend && SUMMARIZER=claude ./gradlew bootRun
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

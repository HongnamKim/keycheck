# 키체크(keycheck)

geekhack Interest Checks(board 132)의 키보드·키캡 글을 한국어 요약, 대표 이미지, 원문 링크로 보여주는 읽기 전용 서비스.

- 설계: `docs/superpowers/specs/2026-11-01-keycheck-design.md`
- 구현 계획: `docs/superpowers/plans/2026-11-01-keycheck-mvp.md` (태스크 순서와 진행 원칙은 이 문서를 따른다)
- UI 시안: https://claude.ai/artifact/H3wUMv8ZWu3XaMcVL8gsQh

## 브랜치

- `main`: 배포용. 직접 커밋·푸시하지 않는다. `dev`에서 PR로만 합친다.
- `dev`: 개발 통합 브랜치. feature 브랜치의 PR을 받는다.
- 지금은 MVP 단계: 구현 계획의 모든 태스크를 `feature/mvp` 한 브랜치에서 작업한다. 태스크마다 새 브랜치를 따지 않는다.
- MVP가 완성되면 `feature/mvp`를 `dev`로 PR해 합친다.
- MVP 이후에는 세부 기능마다 `dev`에서 `feature/<기능명>`을 따고, 끝나면 `dev`로 PR을 연다.

## 커밋

- 사용자가 커밋하라고 할 때만 커밋한다. 태스크가 끝나도 자동으로 커밋하지 않는다.
- 새 파일은 만들 때 바로 `git add` 한다.
- 제목은 conventional commit 형식(`feat:`, `fix:`, `refactor:`, `chore:`, `docs:`, `test:`), 한국어로 쓴다.
- 본문은 `## 주요 내용`, `## 세부 내용`(필요하면 `### 영역`) 섹션과 하이픈 목록으로 쓴다.

## 구조와 실행

```
backend/    Spring Boot 4, Java 21, Gradle Groovy
frontend/   Next.js 16 (App Router, TypeScript)
docker-compose.yml  로컬 PostgreSQL 17
```

- 백엔드 테스트: `cd backend && ./gradlew test` (Testcontainers 사용, Docker 필요)
- 백엔드 실행: `docker compose up -d` 후 `cd backend && ./gradlew bootRun`
- 프론트: `cd frontend && npm test && npm run lint && npm run build`
- 커밋 전에 바꾼 쪽의 테스트를 통과시킨다.

## 코드 규칙

- WebFlux는 쓰지 않는다 (Spring MVC, 동기 방식).
- 패키지는 도메인(`post`, `crawl` 등) 아래를 역할별 폴더(`controller`, `service`, `repository`, `domain`, `dto` 등)로 나눈다. 테스트도 같은 구조를 따른다.
- 스타일은 사용자의 `typing-practice-be`를 따른다: Lombok, DTO·값 타입은 `record` 대신 `private final` 필드와 `@Getter`, 엔티티는 `@Getter`·`@NoArgsConstructor(access = PROTECTED)`·정적 `create`, 들여쓰기 2칸, 테스트는 `@DisplayName`과 AssertJ.
- 자동 테스트에서 실제 geekhack을 호출하지 않는다. 테스트 HTML은 구조만 흉내 내 직접 작성한다.
- geekhack 원문 HTML·이미지를 저장소에 커밋하지 않는다.
- Next.js 코드는 `frontend/AGENTS.md` 안내대로 `node_modules/next/dist/docs/`를 먼저 확인한다.

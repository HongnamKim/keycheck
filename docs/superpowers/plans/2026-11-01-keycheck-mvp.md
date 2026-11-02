# 키체크 첫 버전 구현 계획

- 기준 설계: `docs/superpowers/specs/2026-11-01-keycheck-design.md`
- 작성일: 2026-11-01

## 진행 원칙

- 태스크는 순서대로 진행하고, 태스크마다 테스트 통과를 확인한다. 커밋은 사용자가 요청할 때만 한다(각 태스크의 "커밋:"은 메시지 예시).
- 테스트를 먼저 쓰고 구현한다(파서·분류·이미지 선택·표기 함수).
- 설계에 없는 기능은 넣지 않는다. 설계와 다르게 가야 하면 멈추고 묻는다.
- 자동 테스트는 실제 geekhack을 호출하지 않는다. 테스트 HTML은 실제 구조를 흉내 내 직접 작성한다.

## 확정 버전 (2026-11-01 Maven Central·npm 확인)

| 항목 | 버전 |
|---|---|
| Java | 21 |
| Spring Boot | 4.1.2 |
| Gradle | 8.14.3 (Wrapper) |
| PostgreSQL | 17 (Docker 이미지) |
| Next.js | 16.4.3 |
| Node.js | 22 |

## 선행 조건: geekhack 실제 HTML 확인 (Task 0)

파서 선택자(CSS selector)는 실제 HTML로 확인해야 한다. 매번 geekhack을 호출하지 않도록 실제 HTML을 한 번 저장해 두고 이후 작업은 저장본으로 한다.

- `scripts/fetch-geekhack-samples.sh`: 게시판 목록 2페이지와 샘플 8건 글 페이지를 `samples/geekhack/`에 저장 (요청 간 3초, 이미 받은 파일은 건너뜀)
- `samples/`는 원문 저작권 때문에 커밋하지 않는다(.gitignore). 저장본이 없는 환경에서는 스크립트를 한 번 실행한다.
- 클라우드 세션은 네트워크 설정에서 `geekhack.org`를 허용해야 실행된다.
- 자동 테스트 픽스처는 저장본을 그대로 쓰지 않고, 확인한 구조를 흉내 내 직접 작성한다(공개 저장소에 원문을 넣지 않기 위해).

Task 0에서 확인할 것:
1. 목록 행에서 글 ID, 제목, 작성자, 댓글 수, 조회수, 고정글 여부를 읽는 선택자
2. 글 페이지 첫 글 본문, 이미지, 서명 영역의 선택자
3. 글 페이지에서 조회수(`Read N times` 형식 예상)와 댓글 수를 둘 다 얻을 수 있는지
4. 응답 인코딩(ISO-8859-1)과 엔티티 처리

3번에서 글 페이지에 댓글 수가 없으면 대안(예: 글 페이지의 게시물 수 - 1)을 정리해 사용자에게 확인받는다. 결과는 `docs/geekhack-html-notes.md`에 선택자와 함께 기록한다.

Task 1~3, 6~8, 10, 13은 Task 0 없이 먼저 진행할 수 있다.

---

## 백엔드

### Task 1. 백엔드 프로젝트 뼈대

파일:
- `backend/settings.gradle`, `backend/build.gradle` (Groovy), Gradle Wrapper
- `backend/src/main/java/com/keycheck/KeycheckApplication.java`
- `backend/src/main/resources/application.yml`
- `docker-compose.yml` (postgres:17, DB `keycheck`, 포트 5432)

의존성: `spring-boot-starter-webmvc`, `spring-boot-starter-data-jpa`, `spring-boot-starter-flyway`, `flyway-database-postgresql`, `postgresql`, `jsoup`, `lombok`, 테스트용 `spring-boot-starter-test`, `spring-boot-testcontainers`, `testcontainers-postgresql`.

플러그인: `java`, `org.springframework.boot`, `io.spring.dependency-management`.

- `@EnableScheduling` 추가
- `application.yml`: DB 접속 정보는 환경변수(`DB_URL`, `DB_USERNAME`, `DB_PASSWORD`)에 로컬 기본값, `spring.jpa.hibernate.ddl-auto: validate`

검증:
- `./gradlew test`: 컨텍스트 로딩 테스트 1개 통과 (Testcontainers PostgreSQL)
- `docker compose up -d` 후 `./gradlew bootRun` 기동 확인

커밋: `chore: 백엔드 프로젝트 뼈대와 로컬 DB 구성`

### Task 2. 스키마와 엔티티

파일:
- `backend/src/main/resources/db/migration/V1__init.sql`
- `post/Post.java`, `post/PostImage.java`, `post/Category.java`, `post/SummaryStatus.java`, `image/ImageSource.java`
- `post/PostRepository.java`, `post/PostImageRepository.java`

스키마는 설계 4.7 그대로.
- `post.topic_id` 유니크
- `summary_lines`, `specs`는 `jsonb`. 엔티티에서는 `@JdbcTypeCode(SqlTypes.JSON)`으로 `List<String>`, `List<SpecItem>`(`label`, `value`)에 매핑
- 인덱스: `(hidden, posted_at desc)`, `(hidden, views desc)`, `category`
- `Post` ↔ `PostImage`: `@OneToMany(mappedBy)`, 대표 이미지는 `@ManyToOne` 없이 `representative_image_id` 컬럼 + 조회 시 조인 (순환 참조 단순화)

테스트 (`PostRepositoryTest`, Testcontainers):
- 글과 이미지 저장 후 다시 읽기, jsonb 왕복 확인
- `topic_id`로 조회

커밋: `feat: 글·이미지 스키마와 엔티티 추가`

### Task 3. geekhack 요청 클라이언트와 요청 간격 제한

파일:
- `crawl/RequestGate.java`
- `crawl/GeekhackClient.java`
- `crawl/GeekhackProperties.java` (`baseUrl`, `userAgent`, `minInterval=2s`)

`RequestGate` (설계 4.3: 모든 geekhack 요청이 하나의 간격 제한을 공유):
```java
public class RequestGate {
  public synchronized boolean tryAcquire() // 간격이 찼으면 true 반환하고 시각 기록, 아니면 false (사용자 조회용)
  public void acquire()                    // 간격이 찰 때까지 기다린 뒤 시각 기록 (정기 수집용)
}
```
- 동시 요청에 안전하도록 `synchronized` 사용

`GeekhackClient`:
- `String fetchBoardPage(int page)`, `String fetchTopic(long topicId)`
- `RestClient`로 바이트를 받아 ISO-8859-1로 디코딩 (Task 0 결과에 따라 조정)
- 클라이언트는 게이트를 직접 쓰지 않는다. 정기 수집은 `acquire()`, 사용자 조회는 `tryAcquire()`로 호출자가 게이트를 통과한 뒤 클라이언트를 부른다

테스트 (`RequestGateTest`): 가짜 `Clock`으로
- 첫 `tryAcquire` true, 2초 이내 두 번째 false, 2초 후 true

커밋: `feat: geekhack 요청 클라이언트와 요청 간격 제한`

### Task 4. 게시판 목록 파서 (Task 0 필요)

파일:
- `crawl/BoardListParser.java`, `crawl/BoardTopic.java`
- `backend/src/test/resources/fixtures/board-list.html` (직접 작성)

```java
@Getter
@RequiredArgsConstructor
public class BoardTopic {
  private final long topicId;
  private final String title;
  private final String author;
  private final int replies;
  private final int views;
}
```
- 선택자는 `docs/geekhack-html-notes.md` 2장
- 고정글 제외(`td.subject` class에 `sticky`), 제목의 HTML 엔티티 디코딩
- 목록에는 작성일이 없다. 작성일은 Task 5의 글 페이지에서 읽는다
- 함께: `GeekhackClient` 디코딩을 ISO-8859-1에서 Windows-1252로 변경 (notes 1장)

테스트: 고정글 1개 + 일반글 2개 픽스처로 일반글 2개만, 각 필드 값 확인

커밋: `feat: 게시판 목록 파서`

### Task 5. 글 페이지 파서 (Task 0 필요)

파일:
- `crawl/TopicParser.java`, `crawl/TopicPage.java`
- `fixtures/topic-external.html`, `fixtures/topic-attachment.html` (직접 작성)

```java
// 첫 페이지(topic={id}.0): 새 글 수집용. private final 필드 + @Getter
public class TopicPage { String bodyText; List<RawImage> images; int views; Instant postedAt; }
// 마지막 페이지(topic={id}.999999): 조회수·댓글 수 갱신용 (Task 11). private final 필드 + @Getter
public class TopicStats { int views; int replies; }
```
- 선택자는 `docs/geekhack-html-notes.md` 3장
- 첫 글 `div.inner` 안의 이미지만 수집(외부 `img.bbc_img`, 첨부 `dlattach`) + `div.attachments`. 서명·아바타는 `div.inner` 밖이라 수집되지 않는다
  - 그래서 `RawImage.inSignature`와 Task 7의 서명 규칙을 제거한다
- 작성일: `« on: Tue, 13 October 2026, 08:29:35 »`, `Today at …` 형식도 처리. 시간대는 설정값 `keycheck.geekhack.time-zone` (기본 `America/Chicago`)
- 댓글 수: 마지막 페이지의 마지막 `Reply #N`, 없으면 0
- `GeekhackClient.fetchTopicLastPage(topicId)` 추가

테스트: 픽스처별 본문 텍스트, 이미지 순서·출처, 작성일(일반·Today 형식), 조회수, 마지막 페이지의 댓글 수(댓글 있음·없음)

커밋: `feat: 글 페이지 파서`

### Task 6. 분류기

파일: `classify/Classifier.java`

- `classify(title, bodyText): Category`
- 설계 4.4 순서: 제목 키캡 단어 → 제목 기타 단어 → 본문 사양 단어 종류 수 비교 → 키보드
- 대소문자 무시, 단어 경계 비교

테스트: 샘플 8건 이름 + 기타 예시(데스크매트, 스위치), 본문 판정(키캡 우세·키보드 우세·동점), 제목이 본문보다 우선, 단어 경계

커밋: `feat: 제목·본문 기반 분류기`

### Task 7. 대표 이미지 선택

파일: `image/RepresentativeImageSelector.java`(인터페이스), `image/RuleBasedImageSelector.java`

```java
public interface RepresentativeImageSelector {
  Selection select(List<RawImage> images); // 대표 인덱스(없으면 null) + 이미지별 제외 사유
}
```
- 설계 4.5 제외 규칙: 이모티콘·아바타·테마 경로, 후원·디스코드 버튼 호스트, 파일명 `logo`/`banner`/`divider`
- 제외 규칙 목록은 상수로 한곳에 모은다

테스트: 규칙별 제외 1건씩 + "첫 이미지가 배너인 글은 두 번째 이미지 선택" + "후보 없음"

커밋: `feat: 규칙 기반 대표 이미지 선택`

### Task 8. 요약 인터페이스와 가짜 요약기

파일: `summary/Summarizer.java`, `summary/FakeSummarizer.java`, `summary/SummaryResult.java`

- 설계 4.6. `FakeSummarizer`는 `["[가짜 요약] {제목}", 본문 앞 100자]`와 빈 스펙 목록을 반환
- 구현체 선택은 `keycheck.summarizer=fake` 설정 (`@ConditionalOnProperty`, 기본값 fake)

테스트: 가짜 요약 결과 형식

커밋: `feat: 요약 인터페이스와 가짜 요약기`

### Task 9. 정기 수집 (Task 4·5 필요)

파일: `crawl/CrawlService.java`, `crawl/CrawlScheduler.java`

흐름 (설계 4.3):
1. 목록 1~2페이지를 읽는다 (페이지마다 `gate.acquire()`)
2. 이미 있는 글: 제목, 조회수, 댓글 수, `stats_checked_at` 갱신
3. 처음 보는 글: `gate.acquire()` 후 글 페이지를 읽어 본문·이미지 저장 → 분류 → 대표 이미지 선택 → 요약(성공 `DONE`, 예외 `FAILED`)
4. 글 하나가 실패해도 나머지는 계속 처리하고 로그를 남긴다

- `@Scheduled(fixedDelayString = "${keycheck.crawl.interval:PT30M}")`, 기동 직후 1회 실행
- 글 하나 저장 단위로 트랜잭션

테스트 (`CrawlServiceTest`, Testcontainers): `GeekhackClient`를 픽스처를 돌려주는 가짜로 바꿔
- 첫 실행에 새 글 저장·분류·대표 이미지·요약 상태 확인
- 두 번째 실행에 조회수만 갱신되고 글 페이지는 다시 읽지 않음
- 글 하나의 파싱 실패가 다른 글 저장을 막지 않음

커밋: `feat: 게시판 정기 수집`

### Task 10. 조회 API

파일: `post/PostQueryService.java`, `post/PostController.java`, `post/dto/*.java`

- `GET /api/posts?category=all|keyboard|keycap&sort=recent|views&page=0` (페이지 크기 20)
  - 숨김 제외, 응답은 설계 4.8 카드 필드 + `totalPages`
  - 잘못된 `category`/`sort`는 400
- `GET /api/posts/{id}`: 상세 필드, 숨김이거나 없으면 404. geekhack을 호출하지 않는다
- 로컬 개발용 CORS: `http://localhost:3000` 허용 (설정값)

테스트 (`PostControllerTest`, Testcontainers + MockMvc):
- 분류 필터, 두 정렬, 숨김 제외, 404, 400

커밋: `feat: 글 목록·상세 조회 API`

### Task 11. 조회수 갱신 API (Task 5 필요)

파일: `post/PostStatsService.java`, `PostController`에 엔드포인트 추가

`GET /api/posts/{id}/stats` (설계 4.3):
1. `stats_checked_at`이 1시간 이내면 저장값 반환
2. 아니면 `gate.tryAcquire()`. false면 저장값 반환
3. true면 마지막 페이지(`fetchTopicLastPage`)를 읽어 조회수·댓글 수·`stats_checked_at` 저장 후 반환
4. 요청·파싱 실패 시 저장값 반환, `stats_checked_at` 유지

테스트 (가짜 클라이언트 + 가짜 Clock):
- 1시간 이내 → 클라이언트 호출 없음
- 오래됨 → 새 값 저장·반환
- 게이트 닫힘 → 저장값, 호출 없음
- 클라이언트 예외 → 저장값, `stats_checked_at` 그대로

커밋: `feat: 조회수·댓글 수 갱신 API`

### Task 12. 샘플 8건 실제 점검 (geekhack 접속 필요)

파일: `backend/src/main/java/com/keycheck/check/SampleCheckRunner.java` (`check` 프로필에서만 동작), 결과는 `docs/sample-check-result.md`

- 샘플 8건의 글 ID를 받아 실제 페이지를 읽고(게이트 사용) 분류, 대표 이미지 URL, 이미지 후보·제외 사유, 조회수·댓글 수를 출력
- 대표 이미지가 맞는지는 사람이 원문과 비교해 결과 문서에 기록 (설계 4.5의 미검증 항목)
- 결과가 나쁘면 Task 7 규칙을 조정하고, 조정한 규칙은 테스트에 추가

커밋: `test: 샘플 8건 실제 수집 점검 결과`

---

## 프론트엔드

### Task 13. 프론트 프로젝트 뼈대와 표기 함수

파일:
- `frontend/` (`create-next-app@16.4.3`, TypeScript, App Router, ESLint)
- `frontend/src/lib/format.ts`, `frontend/src/lib/format.test.ts` (Vitest)
- `frontend/src/lib/api.ts` (백엔드 주소는 `API_BASE_URL` 환경변수)

`format.ts` (설계 5장):
- `formatViews(n)`: `999`→`999`, `3456`→`3,400+`, `12345`→`1.2만+`, `80000`→`8만+`, `80999`→`8만+`
- `formatCheckedAt(date, now)`: `방금 확인`, `N분 전 확인`, `N시간 전 확인`, `N일 전 확인`

검증: `npm test`, `npm run lint`, `npm run build`

커밋: `chore: 프론트 프로젝트 뼈대와 조회수 표기 함수`

### Task 14. 목록 화면

파일: `frontend/src/app/page.tsx`, `frontend/src/components/PostCard.tsx`, `FilterBar.tsx`

- 시안(https://claude.ai/artifact/H3wUMv8ZWu3XaMcVL8gsQh)의 HTML·CSS를 읽어 카드·필터·정렬 디자인을 옮긴다
- 필터·정렬·페이지는 URL 쿼리(`?category=keycap&sort=views&page=2`)로 유지
- 카드: 대표 이미지(없으면 기본 그림), 분류, 제목, 요약 첫 줄(`DONE`이 아니면 "요약 준비 중"), 조회수(`formatViews`)·댓글 수·확인 시각
- 이미지는 원본 URL 그대로 `<img>` 사용 (핫링크, 설계 4.5)

검증: 백엔드 + 로컬 DB에 Task 9 픽스처 수집 결과를 넣고 브라우저(Playwright 스크린샷)로 필터·정렬·페이지 이동 확인

커밋: `feat: 글 목록 화면`

### Task 15. 상세 화면

파일: `frontend/src/app/posts/[id]/page.tsx`, `frontend/src/components/StatsLive.tsx` (`"use client"`)

- 서버 컴포넌트가 `/api/posts/{id}`로 저장된 글을 그린다: 요약, 스펙표, "가격·일정은 원문에서 확인" 안내, 원문 링크
- `StatsLive`: 저장값을 먼저 보여주고 마운트 후 `/api/posts/{id}/stats`를 호출해 값과 확인 시각을 바꾼다. 실패하면 저장값 유지
- 404면 Next `notFound()`

검증: Playwright로 상세 진입, 조회수 갱신 표시, 404 확인

커밋: `feat: 글 상세 화면과 조회수 실시간 갱신`

### Task 16. 실행 안내

파일: `README.md`에 "로컬 실행" 절 추가 (DB → 백엔드 → 프론트 순서, 환경변수)

커밋: `docs: 로컬 실행 안내 추가`

---

## 완료 기준

- 백엔드 `./gradlew test`, 프론트 `npm test && npm run lint && npm run build` 통과
- 로컬에서 수집 → 목록 → 상세 → 조회수 갱신까지 동작 확인 (Playwright 스크린샷)
- 샘플 8건 점검 결과 문서화 (Task 12)
- 설계의 "제외" 항목(배포, 실제 AI, 관리자 화면)은 손대지 않음

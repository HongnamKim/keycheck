# 키체크(keycheck) 첫 버전 설계

- 작성일: 2026-11-01
- 상태: 승인 대기

## 1. 목표

geekhack Interest Checks 게시판(board 132)의 키보드·키캡 글을 국내 유저가 한국어로 빠르게 훑어볼 수 있게 한다. 각 글은 한국어 핵심 요약, 대표 이미지, 원문 링크로 보여준다. 읽기 전용 공개 서비스다.

UI는 승인된 시안(https://claude.ai/artifact/H3wUMv8ZWu3XaMcVL8gsQh)의 구조를 따른다: 카드 목록, 필터(전체/키보드/키캡), 정렬(최근/조회순), 상세 화면(요약, 스펙표, 원문 링크).

## 2. 첫 버전 범위

### 포함
- geekhack IC 게시판 자동 수집 (목록 + 첫 글 본문 + 이미지 URL)
- 키보드/키캡/기타 자동 분류
- 대표 이미지 자동 선택 (규칙 기반 후보 필터링)
- 요약 인터페이스와 가짜 요약기 (실제 AI 미연결)
- 조회 API (목록, 상세)
- Next.js 목록·상세 화면
- 로컬 개발 환경 (Docker Compose로 PostgreSQL)

### 제외 (나중에 결정)
- 배포 구성: 프로젝트 완성 후 결정. 후보안은 7장에 기록만 해둔다.
- 실제 AI 요약·번역: 요약기 구현체만 바꾸면 붙도록 설계한다.
- 관리자 화면: 첫 버전에서는 잘못된 대표 이미지·요약을 DB에서 직접 수정한다.
- Group Buys 게시판, 검색, 회원, 알림

## 3. 저장소 구조

하나의 저장소에 백엔드와 프론트를 같이 둔다.

```
keycheck/
├── backend/            # Spring Boot + Kotlin (Gradle Kotlin DSL)
├── frontend/           # Next.js (App Router, TypeScript)
├── docker-compose.yml  # 로컬 PostgreSQL
└── docs/
```

## 4. 백엔드

### 4.1 기술 선택
- Spring Boot, Kotlin, Java 21. 정확한 버전은 구현 계획 단계에서 그 시점 최신 안정판으로 정한다.
- Spring MVC(동기 방식). 코루틴·WebFlux는 쓰지 않는다.
- Spring Data JPA + PostgreSQL. 스키마는 Flyway로 관리한다.
- `kotlin("plugin.spring")`, `kotlin("plugin.jpa")` 사용. 엔티티는 `data class`가 아닌 일반 `class`로 만든다.
- HTML 파싱: Jsoup. HTTP 요청: Spring `RestClient`.
- 스케줄링: `@Scheduled`.

Kotlin 초보를 고려해 Java와 다른 문법(널 안전성, 주 생성자, `val`/`var` 등)은 구현 중에 짧게 설명한다.

### 4.2 패키지 구성

```
com.keycheck
├── crawl/      # geekhack 요청, 목록·본문 파싱, 수집 스케줄
├── post/       # 엔티티, 리포지토리, 조회 서비스, API 컨트롤러
├── image/      # 대표 이미지 후보 필터링·선택
├── classify/   # 키보드/키캡/기타 분류
└── summary/    # Summarizer 인터페이스와 구현체
```

### 4.3 수집

확인된 사실:
- 로그인 없이 목록·본문 HTML과 RSS(`action=.xml;type=rss;board=132`)를 받을 수 있다.
- 응답 인코딩은 ISO-8859-1이며, 일부 문자는 HTML 엔티티로 온다.
- robots.txt에 제한이 없다. 이용약관은 아직 확인하지 않았다.

방식:
- RSS에는 조회수가 없으므로 게시판 목록 HTML을 기본 소스로 쓴다.
- 30분마다 게시판 목록 1~2페이지를 읽어 글 ID, 제목, 작성자, 작성일, 조회수, 댓글 수를 저장하거나 갱신한다. 이때 `stats_checked_at`도 갱신한다.
- 처음 보는 글만 본문 페이지를 열어 첫 글 HTML에서 본문 텍스트와 이미지 URL을 뽑는다. 외부 이미지와 `dlattach` 첨부 둘 다 수집한다.

조회수·댓글 수 갱신 (목록 2페이지 밖으로 밀려난 글):
- 상세 화면은 저장된 글을 바로 보여주고, 화면이 뜬 뒤 브라우저가 `GET /api/posts/{id}/stats`를 따로 호출한다. 목록 조회는 갱신을 일으키지 않는다.
- `stats_checked_at`이 1시간 이내면 저장된 값을 돌려준다. 그보다 오래됐으면 geekhack에서 다시 읽어 저장한 뒤 새 값을 돌려준다. 그동안 화면에는 기존 값이 보인다.
- 사용자 조회가 곧바로 geekhack 요청이 되므로, 모든 geekhack 요청(수집 포함)은 하나의 간격 제한을 공유한다. 직전 요청 후 2초가 지나지 않았으면 다시 읽지 않고 저장된 값을 돌려준다(정기 수집은 기다렸다가 요청). 방문자가 몰려도 geekhack 요청은 2초에 1건을 넘지 않는다.
- geekhack 요청이 실패하면 저장된 값을 돌려주고 `stats_checked_at`은 그대로 둔다.
- 글 페이지에서 조회수·댓글 수를 어떻게 읽을지(글 페이지 표시 vs 목록 검색)는 구현 계획 단계에서 실제 HTML로 확인한다.
- 요청 사이에 최소 2초 간격을 두고, 연락처를 담은 User-Agent를 보낸다.
- 원문 본문 텍스트는 요약용으로만 DB에 저장하고 API로 노출하지 않는다.

본문의 가격·일정·상태는 글쓴이가 갱신하지 않아 낡았을 수 있다(예: Della). 그래서 화면에는 "가격·일정은 원문에서 확인" 안내를 항상 붙인다.

### 4.4 분류

키보드 글 제목은 제품명만 있는 경우가 많아(예: `Della 960`, `RHINO60`) 키보드 단어로는 잡히지 않는다. 그래서 키캡·기타를 먼저 가려내고 나머지를 키보드로 본다. 순서대로 적용한다.

1. 제목에 키캡 단어가 있으면 키캡: `GMK`, `KAT`, `MT3`, `SA`, `DSA`, `DCS`, `XDA`, `PBT`, `ePBT`, `keycap(s)`, `keyset` 등
2. 제목에 기타 단어가 있으면 기타: `deskmat`, `desk mat`, `switch(es)`, `artisan(s)`, `cable(s)` 등
3. 본문에서 키보드 사양 단어와 키캡 사양 단어가 각각 몇 종류 나오는지 세어 많은 쪽
   - 키보드: `plate`, `PCB`, `gasket`, `mount`, `typing angle`, `weight`, `hotswap`, `case`
   - 키캡: `base kit`, `alphas`, `modifiers`, `novelties`, `spacebars`, `colorway`, `profile`
   - 본문은 다른 분류 이름도 자주 언급하므로(키캡 글의 "65% 키보드 렌더" 등) 상품명이 아니라 사양 항목 단어만 센다.
4. 같거나 둘 다 0이면 키보드

단어는 대소문자를 무시하고 단어 경계로 비교한다(`SA`가 `SALE`에 걸리지 않게). 기타 글은 "전체"에서만 보인다.

단어 목록과 본문 판정은 실제 글로 검증하지 않았다. Task 12에서 샘플로 확인하고 조정한다. 분류가 틀린 글은 첫 버전에서 DB로 직접 고친다. 실제 AI 요약기를 붙이면 분류도 AI에 맡기고 이 규칙은 예비로 둘 수 있다.

### 4.5 대표 이미지 선택

geekhack에는 대표 이미지 표시가 없다. 시안용 샘플 8건 중 4건은 첫 이미지가 배너·로고 등이라 부적합했다.

첫 버전은 규칙 기반 후보 필터링만 구현한다.
- 제외: 이모티콘, 아바타, 사이트 테마 이미지, 서명 영역 이미지, 후원·디스코드 버튼, 파일명에 `logo`, `banner`, `divider` 등이 들어간 이미지
- 남은 후보 중 첫 번째를 대표로 정하고, 후보 목록 전체를 저장한다.
- 후보가 없으면 대표 이미지 없이 기본 그림을 보여준다.

이 규칙이 샘플 8건에서 얼마나 맞는지는 아직 검증하지 않았다. 구현 후 8건으로 확인하고 결과를 기록한다. 나중 단계로 AI 이미지 선택과 관리자 교체를 붙일 수 있게 선택 로직을 인터페이스로 분리한다.

이미지는 원본 URL을 그대로 쓴다(핫링크). 외부 호스트가 핫링크를 막는 경우와 저장 방식은 배포 단계에서 다시 정한다.

### 4.6 요약

```kotlin
interface Summarizer {
    fun summarize(source: PostSource): SummaryResult
}
```

- `SummaryResult`: 한국어 요약 문장 목록 + 스펙 키-값 목록(레이아웃, 마운트, 소재 등)
- 첫 버전 구현체는 `FakeSummarizer`. 제목과 본문 앞부분으로 고정 형식의 가짜 결과를 만든다.
- 글마다 요약 상태(`PENDING`, `DONE`, `FAILED`)를 둔다. 화면은 `DONE`이 아니면 "요약 준비 중"을 보여준다.
- 실제 AI는 나중에 구현체만 추가하고 설정으로 고른다. 자동 요약 품질은 아직 검증되지 않았다(시안 요약은 수작업이었다).

### 4.7 데이터 모델

`post`
| 컬럼 | 설명 |
|---|---|
| id | PK |
| topic_id | geekhack 글 ID (유니크) |
| title, author | |
| category | KEYBOARD / KEYCAP / OTHER |
| posted_at | 원문 작성일 |
| views, replies | 조회수·댓글 수 (4.3 갱신 규칙) |
| stats_checked_at | 조회수·댓글 수를 마지막으로 확인한 시각 |
| original_url | 원문 링크 |
| body_text | 요약용 원문 텍스트 (비공개) |
| summary_status | PENDING / DONE / FAILED |
| summary_lines | jsonb, 한국어 요약 |
| specs | jsonb, 스펙 키-값 |
| representative_image_id | FK → post_image, null 허용 |
| hidden | 숨김 여부 (DB 직접 수정용) |
| created_at, updated_at | |

`post_image`
| 컬럼 | 설명 |
|---|---|
| id | PK |
| post_id | FK |
| url | 원본 URL |
| source | EXTERNAL / ATTACHMENT |
| position | 본문 내 순서 |
| excluded_reason | 후보에서 빠진 이유, 후보면 null |

### 4.8 API

- `GET /api/posts?category={all|keyboard|keycap}&sort={recent|views}&page={n}`
  - 숨김 글 제외. 카드에 필요한 필드만 반환(제목, 분류, 대표 이미지 URL, 요약 첫 줄, 조회수, 댓글 수, `stats_checked_at`, 작성일).
- `GET /api/posts/{id}`
  - 요약 전체, 스펙표, 대표 이미지, 원문 링크, 저장된 조회수, 댓글 수, `stats_checked_at`. geekhack을 호출하지 않는다.
- `GET /api/posts/{id}/stats`
  - 조회수, 댓글 수, `stats_checked_at`. 1시간보다 오래된 값이면 geekhack에서 다시 읽는다(4.3).
- 조회수는 원래 숫자로 반환하고, 표기 변환은 프론트에서 한다.

## 5. 프론트엔드

- Next.js App Router + TypeScript. 서버 컴포넌트에서 백엔드 API를 호출한다.
- `/`: 카드 목록, 필터·정렬은 URL 쿼리로 유지한다(공유·뒤로가기 가능).
- `/posts/[id]`: 상세. 요약, 스펙표, "가격·일정은 원문에서 확인" 안내, 원문 링크.
- 조회수 표기: 1,000 미만은 그대로, 1,000 이상 1만 미만은 앞 두 자리만 남기고 내림해 `3,400+`, 1만 이상은 만 단위 소수 첫째 자리까지 내림해 `1.2만+`, `8만+`(소수가 0이면 생략).
- 댓글 수는 내림 없이 그대로 보여준다.
- 상세 화면의 조회수·댓글 수는 클라이언트 컴포넌트가 `/stats`를 호출해 받은 값으로 바꿔 보여준다. 응답 전에는 저장된 값을 보여준다.
- 조회수·댓글 수 옆에 작게 `3시간 전 확인`처럼 `stats_checked_at` 기준 상대 시간을 붙인다.
- 디자인은 시안을 따른다. 시안의 실제 렌더링·동작은 검증하지 않았으므로 구현하면서 확인한다.

## 6. 테스트

- 파서·분류·이미지 필터: 단위 테스트. 테스트용 HTML은 geekhack 구조만 흉내 낸 직접 작성본을 쓴다(원문을 공개 저장소에 넣지 않기 위해).
- 리포지토리·API: Spring Boot 테스트 + Testcontainers PostgreSQL.
- 실제 사이트 확인: 샘플 8건을 대상으로 수집·분류·대표 이미지 결과를 수동 점검하는 스크립트. 자동 테스트에서는 실제 사이트를 호출하지 않는다.

## 7. 배포 (미정)

프로젝트 완성 후 결정한다. 이전에 검토한 후보:
- 무료 구성: Oracle Always Free ARM VM(서울)에 Spring + PostgreSQL, Vercel Hobby에 Next, Cloudflare R2에 이미지·DB 백업
- 위험: Oracle VM은 7일 저사용 시 회수될 수 있음(백업·설치 스크립트로 대응), Vercel Hobby는 비상업 전용
- 광고·제휴 수익화를 한다면 Next도 VM에 올리고 앞단에 Cloudflare를 두는 쪽으로 바뀐다.

## 8. 공개 전 확인할 것

- geekhack 이용약관과 수집 허용 여부
- 원본 이미지 핫링크·저장 방식의 권리 문제
- 요약이 원문 대체가 되지 않을 분량인지

# geekhack HTML 구조 (Task 0)

- 확인일: 2026-11-02, 저장본 `samples/geekhack/` (`scripts/fetch-geekhack-samples.sh`, 커밋 안 함)
- 엔진: SMF 2.0.15, 테마 Nostalgia
- 선택자는 Jsoup 기준. 비로그인 응답이라 모든 링크에 `PHPSESSID=...&amp;`가 붙는다. 링크에서는 `topic=(\d+)`만 뽑고, 원문 링크는 `https://geekhack.org/index.php?topic={id}.0`로 새로 만든다.

## 1. 인코딩

- `<meta charset=ISO-8859-1>`로 선언하지만 실제 바이트는 **Windows-1252**다. 샘플에서 `0x97`(—), `0x92`('), `0x95`(•), `0x93`·`0x94`(“ ”)가 나왔다.
- ISO-8859-1로 읽으면 이 글자들이 깨진다(예: `Flat Out � A Motorsport`). → **Windows-1252로 디코딩한다.**
- 그 밖의 문자(한글, 일부 기호)는 `&#8212;` 같은 숫자 엔티티로 오므로 Jsoup이 풀어 준다.

## 2. 게시판 목록 (`index.php?board={132|70}.{offset}`)

| 항목 | 위치 |
|---|---|
| 글 행 | `#messageindex table.table_grid tbody tr` 중 `td.subject`가 있는 행 |
| 고정글 | `td.subject`의 class에 `sticky` 포함 (`stickybg`, `locked_sticky2`) → 제외 |
| 글 ID·제목 | `td.subject span[id^=msg_] > a` — href의 `topic=(\d+)\.0`, 텍스트가 제목 |
| 작성자 | `td.subject p > a` 텍스트 ("Started by" 뒤 링크) |
| 댓글·조회수 | `td.stats` 텍스트 `12 Replies 4347 Views` (쉼표 없음) → `(\d+)\s+Replies\s+(\d+)\s+Views` |
| 마지막 댓글 시각 | `td.lastpost` (쓰지 않음) |

- 페이지당 50행. 오프셋은 `board=132.0`, `132.50`, `132.100`… (`TOPICS_PER_PAGE = 50` 가정이 맞다)
- 첫 페이지는 고정글 1개 + 일반글 49개.
- 기본 정렬은 **마지막 댓글순**(`sort_down`이 Last post 열에 있음). 새 글뿐 아니라 댓글이 달린 오래된 글도 1~2페이지로 올라온다.
- **Group Buys and Preorders(board 70)**: 2026-11-03 확인. 구조·정렬·쪽당 50행이 132와 같다. 첫 쪽 위에 고정글이 8개 있다(파서가 뺀다). 저장본 `board-70-firstpost-page0.html`.
- **작성순**: `index.php?board=132.{offset};sort=first_post;desc` 는 글 번호 내림차순(최신 글 먼저)으로 50개씩 준다(2026-11-03 확인, 저장본 `board-132-firstpost-page1.html`, offset 50). 행 구조는 기본 목록과 같고 고정글은 첫 페이지 위에만 온다. 쪽 경계가 댓글에 따라 바뀌지 않으므로 과거 글 확보는 이 정렬을 쓴다. 정기 수집은 댓글이 달린 글의 조회수도 갱신하려고 기본 정렬(마지막 댓글순)을 그대로 쓴다. 확인 당시 마지막 쪽은 offset 5900(119쪽).
- `;sort=first_post;desc`를 URI 템플릿으로 넘기면 `=`가 `%3D`로 바뀌므로 `GeekhackClient`는 만든 주소를 그대로 보낸다.
- 끝을 넘는 offset을 주면 SMF가 마지막 쪽을 다시 주는 것으로 알려져 있다(`constructPageIndex`). geekhack에서는 확인하지 않았다. 그래서 과거 글 확보는 빈 쪽뿐 아니라 이미 받은 마지막 쪽이 다시 와도 끝으로 본다.
- **목록에는 글 작성일이 없다.** 작성일은 글 페이지에서 읽는다.

## 3. 글 페이지 (`index.php?topic={id}.{offset}`)

| 항목 | 위치 |
|---|---|
| 조회수 | `#forumposts h3.catbg` 텍스트의 `(Read 37883 times)` |
| 글 하나 | `#forumposts div.post_wrapper` (첫 번째가 원글) |
| 작성 시각 | 글 안 `div.keyinfo div.smalltext` 텍스트 `« on: Tue, 13 October 2026, 08:29:35 »` |
| 댓글 번호 | 댓글의 같은 위치 `« Reply #13 on: ... »` |
| 본문 | 글 안 `div.inner` (id `msg_{번호}`) |
| 외부 이미지 | `div.inner img.bbc_img` (일부 `bbc_img resized`) |
| 첨부 이미지 | `div.inner a.highslide > img`, src가 `index.php?action=dlattach;topic=…;attach=…;image` |
| 첨부 영역 | `div.attachments a.highslide` (본문 아래). href가 원본, 안쪽 img는 썸네일(attach 번호가 다름). 샘플 8건의 원글은 비어 있었고 형식은 댓글에서 확인했다 |
| 이모티콘 | `div.inner img.smiley` (`/Smileys/` 경로) |
| 아바타 | `div.poster img.avatar` — `div.inner` 밖 |
| 서명 | `div.signature` — `div.inner` 밖 (`moderatorbar` 뒤) |

- 이미지 주소에도 `PHPSESSID=...&`가 붙어 있으므로 저장 전에 지운다.

### 시간대
- 비로그인 표시 시각은 **UTC−6**이다. 같은 글 2건을 RSS(`pubDate`, GMT)와 비교해 6시간 차이를 확인했다(2026-11-02 기준).
- 미국 중부시간(서머타임 적용)인지 고정 −6인지는 확인하지 못했다. 서머타임 기간 글은 최대 1시간 어긋날 수 있다. → 시간대를 설정값으로 두고 기본 `America/Chicago`.
- 오늘·어제 글이 `Today at …`으로 표시되는지는 샘플에 없어 확인하지 못했다(SMF 기본 동작은 그렇게 표시). 파서는 이 형식도 처리해야 한다.

### 댓글 수
- 글 페이지에는 댓글 수 표시가 없다. 한 페이지(50개)짜리 글은 마지막 `Reply #N`이 댓글 수다.
- 여러 페이지 글은 첫 페이지만으로 알 수 없다. 대신 **오프셋을 아주 크게 주면(`topic={id}.999999`) 마지막 페이지가 온다**(120481로 확인: `Reply #50`~`#78`, 조회수도 표시).
- → 조회수·댓글 수 갱신은 `topic={id}.999999` 요청 한 번으로 한다. 댓글이 없으면 `Reply #`가 없으므로 0.

## 4. 샘플 8건 실제 제목

| 글 | 제목 |
|---|---|
| 120481 | Della 960 - TKL F13 WKL - Coming soon 2026/10/30 |
| 127115 | F8X \| Geonworks x Nuxroskb Collaboration \| Update the sale date, vendors,...... |
| 127131 | [IC] RHINO60 Custom Keyboard - JACKY DESIGN |
| 127200 | [IC] Constance 60 |
| 126608 | [IC] GMK CYL Blanks - October 16th GB Launch! |
| 126906 | [IC] GMK Knight |
| 126959 | [IC] DCS Nights |
| 127212 | [IC] DCS Doubleshot Hangul (Beige/WoB Alphas) |

- 말머리 `[IC]`가 없는 글도 있다(120481, 127115).
- 분류 규칙(설계 4.4)으로는 8건 모두 의도대로 나올 것으로 보인다. 실제 실행 확인은 Task 12.
- 목록 1페이지에서 규칙이 놓칠 만한 제목: `PBTfans Genius #81`(`PBT` 뒤에 글자가 붙어 단어 경계에 안 걸림), `PCB Stabilizer V3`(스태빌라이저인데 기타 단어 없음), `SWG-Retro Red Light`(스위치 브랜드 약어). → Task 12에서 본문 판정 결과와 함께 보고 단어를 보강한다.

## 5. 대표 이미지 예비 관찰

시안에서 사람이 고른 이미지와 현재 규칙(설계 4.5)이 고를 이미지를 비교했다. 규칙 결과가 틀렸는지는 이미지를 직접 봐야 알 수 있어 Task 12에서 판단한다.

- 같음: 127131, 126906
- 규칙이 배너를 올바르게 거름: 126959 (`Nights_Banner.png` 제외)
- 다름 (첫 이미지를 고름): 120481, 127115, 127200, 126608, 127212
- 126608은 첫 이미지가 이모티콘(`blank.gif`)이었고 규칙이 제외했다.

## 6. 구현에 반영할 것

| 대상 | 변경 |
|---|---|
| `GeekhackClient` (Task 3 코드) | ISO-8859-1 → Windows-1252 디코딩 |
| Task 4 목록 파서 | `BoardTopic.postedAt` 제거 (목록에 없음). 고정글은 class의 `sticky`로 판별 |
| Task 5 글 파서 | 작성일은 첫 글 `keyinfo`에서, 시간대 설정값(기본 `America/Chicago`), `Today at` 형식 처리. 이미지는 `div.inner` 안에서만 읽으므로 서명 이미지는 애초에 수집되지 않음 |
| Task 7 이미지 선택 | 서명 이미지가 파서 단계에서 빠지므로 `RawImage.inSignature`와 서명 규칙은 필요 없어진다 (Task 5에서 정리) |
| Task 11 갱신 API | `topic={id}.999999` 한 번으로 조회수 + 마지막 `Reply #N` |

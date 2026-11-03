# 샘플 8건 실제 점검 결과 (Task 12)

- 점검 일시: 2026-11-03 20:40 (KST)
- 대상: 시안에 쓴 샘플 8건 (키보드 4건, 키캡 4건)
- 점검한 규칙: 분류(설계 4.4), 대표 이미지 선택(설계 4.5)

## 방법

1. `SampleCheckRunner`(`check` 프로필)로 실제 geekhack 첫 페이지(`topic={id}.0`)를 글마다 한 번씩 읽었다. 요청 사이 간격은 `RequestGate`(2초)를 그대로 쓴다. 웹 서버와 수집 스케줄러는 켜지 않고, 실행기는 DB를 읽거나 쓰지 않는다. 다만 DB 연결과 Flyway 마이그레이션은 그대로 켜져 있어 PostgreSQL이 먼저 떠 있어야 한다(빈 DB면 스키마를 만든다).

   ```
   docker compose up -d
   cd backend && ./gradlew bootRun --args='--spring.profiles.active=check'
   # 특정 글만: --args='--spring.profiles.active=check 127200 126959'
   ```

2. 운영 코드와 같은 `TopicParser`·`Classifier`·`RuleBasedImageSelector`로 제목, 분류, 대표 이미지, 이미지 후보 전체(순서, 출처, 제외 사유), 조회수, 첫 페이지 기준 댓글 수를 출력했다.
3. 고른 대표 이미지(필요하면 다른 후보도)를 내려받아 직접 보고, 사람이 시안용으로 고른 이미지(`sample-images/`)와 비교했다. 같은 그림이 아니어도 제품을 잘 보여 주면 적합으로 본다. 배너·로고·텍스트 그림·표·가격표 등이면 부적합이다.
4. 실제 요청은 1회 실행(8건)만 했다. 규칙을 고친 뒤에는 저장본(`samples/geekhack/`, 2026-11-02)을 로컬 서버로 내줘 같은 실행기로 다시 돌렸고, 결과는 조회수 말고 같았다.

## 글별 결과

| 글 ID | 제목 | 분류 (기대 → 결과) | 대표 이미지 | 판정 | 이미지 수 (제외) |
|---|---|---|---|---|---|
| 120481 | Della 960 - TKL F13 WKL - Coming soon 2026/10/30 | 키보드 → KEYBOARD | [0] https://i.imgur.com/zKw2Hk1.png | 부적합: 가죽 위 명함에 브랜드 로고만 있는 티저 사진. 키보드가 보이지 않는다 | 28 (0) |
| 127115 | F8X \| Geonworks x Nuxroskb Collaboration \| ... | 키보드 → KEYBOARD | [0] https://cdn.shopify.com/s/files/1/0786/9520/3115/files/Silver6.png?v=1789702356 | 부적합: 케이스에 새긴 협업 로고 클로즈업. 키보드 모양을 알 수 없다 (사람 선택은 [1] `F84_Silver2.png`) | 12 (0) |
| 127131 | [IC] RHINO60 Custom Keyboard - JACKY DESIGN | 키보드 → KEYBOARD | [0] https://i.imgur.com/u5jS0A1.jpeg | 적합: 키보드 정면 렌더, 사람 선택과 같다 | 15 (0) |
| 127200 | [IC] Constance 60 | 키보드 → KEYBOARD | [0] https://geekhack.org/index.php?action=dlattach;topic=127200.0;attach=318369;image | 적합(약함): 케이스·PCB 분해 렌더의 근접 사진. 제품 사진이지만 전체 모양은 덜 드러난다. 사람 선택도 분해도였다 | 21 (0) |
| 126608 | [IC] GMK CYL Blanks - October 16th GB Launch! | 키캡 → KEYCAP | [1] https://i.postimg.cc/fDn8NbZW/Black-Base.png | 적합: Black Base 키트 렌더. [0] 이모티콘(`blank.gif`)을 규칙이 걸렀다 | 13 (2) |
| 126906 | [IC] GMK Knight | 키캡 → KEYCAP | [0] https://i.imgur.com/lQVWvvp.jpeg | 적합: 베이스 키트 렌더, 사람 선택과 같다 | 33 (0) |
| 126959 | [IC] DCS Nights | 키캡 → KEYCAP | [1] https://photos.kstj.us/ALPS_Base_Branded.png | 적합: ALPS 베이스 키트 렌더. [0] `Nights_Banner.png`를 규칙이 걸렀다 | 16 (3) |
| 127212 | [IC] DCS Doubleshot Hangul (Beige/WoB Alphas) | 키캡 → KEYCAP | [0] https://i.imgur.com/jxzP4ZE.jpeg | 적합: 한글 키캡을 끼운 키보드 실사진 | 3 (0) |

- 분류: 8건 모두 기대와 같다. 키캡 4건은 제목 단어(`GMK`, `DCS`)로, 키보드 4건은 제목에 단어가 없어 본문 사양 단어로 판정됐다(키보드 단어 종류 : 키캡 단어 종류 = 120481 4:0, 127115 7:0, 127131 7:1, 127200 8:4).
- 대표 이미지: 8건 중 6건 적합(1건은 약함), 2건 부적합.
- 제외 규칙이 실제로 동작한 것은 이모티콘(126608, 126959)과 파일명 `banner`(126959) 두 종류다. 로고·버튼·디바이더 규칙은 이 8건에서는 걸린 이미지가 없었다.
- 조회수는 첫 페이지 머리말에서 읽었다(예: 120481 83,919회). 댓글 수는 첫 페이지의 마지막 `Reply #N`이라 여러 페이지 글(120481 등)에서는 실제보다 작다. 정기 수집은 목록의 댓글 수를, 갱신 API는 마지막 페이지를 쓰므로 운영 코드에는 영향이 없다.

## 규칙 조정

### 대표 이미지: 조정하지 않음

부적합 2건은 주소만으로 가릴 수 없다.
- 120481: `i.imgur.com/zKw2Hk1.png`. imgur 무작위 이름이고, 같은 이미지가 본문 뒤쪽 사진 모음([18])에도 다시 나온다. 다음 후보 [1]은 서울 키보드 엑스포 현수막 사진이라 첫 후보를 건너뛰어도 나아지지 않는다.
- 127115: `Silver6.png`(로고 클로즈업)와 사람이 고른 `F84_Silver2.png`(키보드 전체)는 파일명으로 구별되지 않는다.

"첫 이미지 건너뛰기", "png 대신 jpg 우선" 같은 규칙은 적합한 6건 중 4건(첫 이미지가 적합한 127131, 127200, 126906, 127212)을 망가뜨리므로 넣지 않았다. 이 두 건은 설계대로 첫 버전에서 DB로 대표 이미지를 직접 고치고, 나중 단계(AI 이미지 선택, 관리자 교체)에서 다룬다.

### 분류: 제목 단어 2개 추가

저장본 게시판 목록 2페이지(`board-132-page0.html`, `page1.html`, 고정글 제외 99건)의 제목만 현재 `Classifier`로 돌려 봤다. 명백히 틀린 패턴 두 가지를 보강했다(테스트 `ClassifierTest`에 추가).

| 추가 | 근거 (저장본 제목) | 변화 |
|---|---|---|
| 키캡 제목 단어 `PBTfans` | `PBTfans Genius #81`, `PBTfans Luna`, `PBTfans Maguro`, `PBTfans IDOL TENSHI` — 키캡 브랜드인데 `PBT` 뒤에 글자가 붙어 단어 경계에 걸리지 않았다 | 4건 키보드 → 키캡 |
| 기타 제목 단어 `stabilizer(s)`, `stabiliser(s)` | `Consi Studio ... PCB Stabilizer V3` — 스태빌라이저 부품 | 1건 키보드 → 기타 |

제목만 본 결과는 키캡 55, 키보드 42, 기타 2건이 됐다. 제목으로 못 정한 글은 실제 수집에서 본문 사양 단어로 다시 판정되므로, 아래 후보들은 본문까지 봐야 틀렸는지 알 수 있다.

## 남은 문제

- 대표 이미지 2건(120481, 127115)은 규칙으로 못 고친다. 첫 이미지가 로고·티저인 키보드 글은 다른 글에서도 나올 것으로 보인다. 첫 버전은 DB로 직접 고친다.
- 127200처럼 대표 이미지가 geekhack 첨부(`dlattach`)면 화면이 geekhack에 직접 이미지를 요청한다(핫링크). 설계 4.5대로 배포 단계에서 다시 정한다.
- 분류 보강 후보(애매해서 넣지 않음). 본문 판정 결과를 본 뒤 정한다.
  - 키캡 제조사 약어: `KKB`(Triumph Adler Neo, Cobalt, Korean Delights, 1001 Nights, Cross Impact), `MW`·`MWKEYS`(Night Tare, Datenshi, Zoltraak, Kiss Of Death), `KBS`(Space Odyssey, Dirty), `MTNU`(ZX, Dasher, A History of Violets). 대부분 키캡 세트로 보이지만 `MW` 같은 짧은 약어는 다른 뜻으로도 쓰이고, 같은 브랜드가 키보드를 낼 수도 있다.
  - 스위치 브랜드 `SWG`(`SWG-Retro Red Light`, `SWG Retroguardia`): 스위치인지 제목만으로 확신할 수 없다.
  - `base kits`가 제목에 있는 글(`KKB Cobalt - US/UK/FR/DE base kits!`): 본문 단어를 제목에도 쓸지는 정하지 않았다.
- 분류 단어와 본문 판정은 샘플 8건과 저장본 목록 제목으로만 확인했다. 실제 수집을 돌린 뒤 틀린 글이 보이면 다시 조정한다.

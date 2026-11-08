# 운영 서버 (백엔드)

백엔드는 AWS Lightsail 서울 인스턴스 한 대(Ubuntu LTS, 2GB, 고정 IP)에서 Docker 컨테이너 두 개로 돕니다.

- `app`: 백엔드 이미지 `ghcr.io/hongnamkim/keycheck-backend`. 포트를 밖으로 열지 않습니다.
- `caddy`: 80·443을 받아 `https://api.keycheck.kr`을 `app:8080`으로 넘깁니다. 인증서는 Let's Encrypt에서 자동으로 받고 갱신합니다.

`main`에 백엔드 변경을 합치면 GitHub Actions(`.github/workflows/backend-image.yml`)가 테스트 뒤 이미지를 커밋 해시와 `latest` 태그로 GHCR에 올립니다. 서버의 타이머가 3분마다 이미지를 받아 보고, 지금 도는 것과 다를 때만 `app`을 다시 띄웁니다. Actions는 서버에 접속하지 않으므로 SSH 키나 서버 주소를 GitHub에 두지 않습니다.

| 파일 | 서버 위치 | 하는 일 |
|---|---|---|
| `compose.yaml` | `/opt/keycheck/compose.yaml` | `app`·`caddy` 실행 설정 (로그 10MB×3, `app` 메모리 1.4GB 상한) |
| `Caddyfile` | `/opt/keycheck/Caddyfile` | HTTPS와 `app` 연결 |
| `update.sh` | `/opt/keycheck/update.sh` | 새 이미지 확인·갱신 |
| `keycheck-update.service`, `keycheck-update.timer` | `/etc/systemd/system/` | `update.sh`를 3분마다 실행 |
| `keycheck.env.example` | `/etc/keycheck/keycheck.env`로 값을 채워 둠 | 앱 환경변수와 비밀값 |

서버 관리는 AWS 콘솔의 Lightsail 브라우저 SSH로 합니다. 아래 명령은 모두 그 터미널에서 실행합니다.

## 첫 세팅

### 1. 인스턴스와 방화벽

- 서울 리전, Ubuntu LTS, 2GB 요금제로 만들고 고정 IP를 붙입니다.
- 네트워킹 탭의 방화벽(IPv4와 IPv6 모두)은 다음만 남깁니다.
  - HTTP 80, HTTPS 443
  - SSH 22: "IP 주소로 제한"을 켜고 IP는 넣지 않은 채 "Lightsail 브라우저 SSH/RDP 허용"만 둡니다. 사용자 IP가 자주 바뀌어 IP로 열지 않습니다.
  - 앱 포트 8080은 열지 않습니다(compose 내부에만 있습니다).
- Cloudflare DNS에 `api` A 레코드를 고정 IP로 만듭니다. Caddy가 인증서를 처음 받을 때까지는 프록시를 끈 채(DNS only) 둡니다. 프록시는 [공개 전 Cloudflare 설정](#공개-전-cloudflare-설정)에서 켭니다.

### 2. Docker 설치

Docker 공식 문서 "Install Docker Engine on Ubuntu"의 apt 저장소 방법입니다. 문서가 바뀌었으면 문서를 따릅니다.

```bash
sudo apt-get update
sudo apt-get install -y ca-certificates curl
sudo install -m 0755 -d /etc/apt/keyrings
sudo curl -fsSL https://download.docker.com/linux/ubuntu/gpg -o /etc/apt/keyrings/docker.asc
sudo chmod a+r /etc/apt/keyrings/docker.asc
echo "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.asc] https://download.docker.com/linux/ubuntu $(. /etc/os-release && echo "${UBUNTU_CODENAME:-$VERSION_CODENAME}") stable" | sudo tee /etc/apt/sources.list.d/docker.list > /dev/null
sudo apt-get update
sudo apt-get install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin
sudo docker compose version
```

축소본 작업이 메모리를 많이 쓸 때의 안전판으로 스왑 1GB를 만듭니다.

```bash
sudo fallocate -l 1G /swapfile
sudo chmod 600 /swapfile
sudo mkswap /swapfile
sudo swapon /swapfile
echo '/swapfile none swap sw 0 0' | sudo tee -a /etc/fstab
free -h                                                     # Swap 1.0Gi
```

### 3. 환경변수 파일

```bash
sudo install -d -m 700 /etc/keycheck
sudo install -m 600 /dev/null /etc/keycheck/keycheck.env
sudo nano /etc/keycheck/keycheck.env
```

[`keycheck.env.example`](keycheck.env.example) 내용을 붙여 넣고 `<...>`를 채웁니다.

- 키와 비밀번호는 편집기에서 직접 넣습니다. `echo`나 `docker run -e`처럼 명령에 실으면 셸 기록과 프로세스 목록에 남습니다.
- 값은 적은 그대로 들어갑니다(compose `format: raw`라 `$`나 `#`가 든 비밀번호도 바뀌지 않습니다). 따옴표를 두르지 말고, 줄 끝에 주석이나 공백을 붙이지 않습니다. `CRAWL_ENABLED=true `처럼 끝에 공백이 붙으면 `true`로 보지 않아 수집·축소본이 오류 없이 꺼지고, `SUMMARIZER`면 가짜 요약기로 뜹니다. 저장한 뒤 아래 명령이 아무것도 출력하지 않는지 봅니다(줄 번호와 이름만 출력하고 값은 출력하지 않습니다).

  ```bash
  sudo grep -nE '[[:space:]]$' /etc/keycheck/keycheck.env | cut -d= -f1
  ```
- 컨테이너는 이 파일을 실제 환경변수로 받습니다. 서버 어디에도 `.env` 형식으로 앱 설정을 두지 않습니다(파일로 읽는 `.env`에서는 `KEYCHECK_CORS_ALLOWED_ORIGINS` 같은 대문자 표기가 적용되지 않습니다).
- `SUMMARIZER=claude`를 빠뜨리고 한 번이라도 기동하면 IC 글에 가짜 요약이 남습니다. 첫 기동 전에 확인합니다.
- Supabase `public` 스키마는 비워 둡니다. 첫 기동 때 Flyway가 V1부터 적용하는데, 다른 테이블이 있으면 기동이 멈춥니다.

### 4. 배포 파일과 타이머

```bash
sudo git clone --depth 1 --branch main https://github.com/HongnamKim/keycheck.git /tmp/keycheck
sudo install -d /opt/keycheck
sudo install -m 644 /tmp/keycheck/deploy/compose.yaml /tmp/keycheck/deploy/Caddyfile /opt/keycheck/
sudo install -m 755 /tmp/keycheck/deploy/update.sh /opt/keycheck/
sudo install -m 644 /tmp/keycheck/deploy/keycheck-update.service /tmp/keycheck/deploy/keycheck-update.timer /etc/systemd/system/
sudo rm -rf /tmp/keycheck

cd /opt/keycheck
sudo docker compose up -d caddy
sudo systemctl daemon-reload
sudo systemctl enable --now keycheck-update.timer
```

`caddy`는 바로 뜨고, `app`은 타이머가 첫 이미지를 받으면 띄웁니다.

### 5. 첫 이미지와 공개 전환

1. 코드를 `main`에 합치면 Actions가 첫 이미지를 올립니다. GHCR 패키지는 처음에 비공개로 생깁니다.
2. GitHub 프로필의 Packages 탭 → `keycheck-backend` → Package settings → Danger Zone의 Change visibility → Public. 저장소가 공개이고 이미지에는 비밀값이 없으므로 공개로 둡니다. 그래서 서버에 GHCR 토큰이 필요 없습니다.
3. 공개 전에는 서버가 이미지를 받지 못해 타이머가 3분마다 실패를 남깁니다. 공개하면 다음 확인 때 `app`을 띄웁니다.

### 6. 확인

```bash
journalctl -u keycheck-update -n 20                         # "다시 띄움" 또는 "그대로"
cd /opt/keycheck && sudo docker compose ps
sudo docker compose logs --tail 200 app                     # Flyway "Successfully applied 11 migrations"
sudo docker compose exec caddy wget -qO- http://app:8080/actuator/health   # DNS·인증서 전에도 확인
curl -s https://api.keycheck.kr/actuator/health             # {"status":"UP"}
# 브라우저 호출이 막히지 않는지: 운영 프론트 주소가 그대로 돌아와야 한다
curl -s -o /dev/null -D - -H 'Origin: https://keycheck.kr' https://api.keycheck.kr/api/posts/highlights | grep -i '^access-control-allow-origin'
# 갱신 타이머가 같은 이미지를 다시 띄우지 않는지: app이 뜬 뒤 한 번 더 돌려 "그대로"가 나와야 한다
sudo systemctl start keycheck-update.service && journalctl -u keycheck-update -n 3
```

- 로그에 1분 간격으로 반복되는 ERROR가 없는지, 특히 "요약 API를 쓸 수 없어", "축소본 작업 중 예상하지 못한 오류", "저장소(R2_*) 설정이 없어"가 없는지 봅니다.
- 요약 로그에 "Claude 요약 사용 토큰: model=claude-..."가 찍히는지 봅니다.
- 마지막 명령이 매번 "다시 띄움"이면 이미지 비교가 이 서버에서 맞지 않는 것이므로 타이머를 끄고(`sudo systemctl stop keycheck-update.timer`) `update.sh`를 고칩니다. 그대로 두면 3분마다 `app`을 다시 띄웁니다.
- Supabase SQL Editor에서 몇 분 간격으로 두 번 봅니다.

  ```sql
  select count(*) from flyway_schema_history;                      -- 11
  select summarized_by, count(*) from post group by 1;              -- 'fake'가 없고 claude 모델 ID만 늘어남
  select count(*) from post;                                        -- 수집이 돌면 늘어남
  select count(thumb_key) from post_image;                          -- 축소본이 돌면 늘어남
  ```

  `fake`가 있으면 아래 [가짜 요약이 남았을 때](#가짜-요약이-남았을-때)를 따릅니다. `post`나 `thumb_key`가 늘지 않으면 환경변수 파일의 `CRAWL_ENABLED`·`THUMBNAIL_ENABLED` 줄을 다시 봅니다.

## 공개 전 점검

화면을 알리기 전에 아래를 모두 마칩니다. 대시보드 작업이라 이 저장소의 코드로는 대신할 수 없습니다.

- Supabase(첫 기동 전에): 설정의 "Enable Data API"를 끄고 "Enforce SSL"을 켭니다. Flyway가 만든 테이블은 RLS가 꺼져 있어 Data API가 켜져 있으면 공개 키(anon)로 읽고 쓸 수 있습니다. 앱은 JDBC로만 붙으므로 Data API가 필요 없습니다.
- R2: 운영 버킷의 Settings에서 r2.dev 공개 주소(Public Development URL)를 끄고 `img.keycheck.kr` 연결 도메인만 둡니다. API 토큰은 운영 버킷만 Object Read & Write로 만듭니다.
- Anthropic: 운영 전용 워크스페이스에 월 지출 한도(예: $100)를 겁니다. 앱의 하루 요약 상한(2,000건)은 재시작하면 0부터 다시 세므로 이 한도가 마지막 안전망입니다.
- Cloudflare: 아래 [공개 전 Cloudflare 설정](#공개-전-cloudflare-설정).
- Vercel: 루트 [README](../README.md#프론트-첫-세팅-vercel)의 프론트 첫 세팅.

### 공개 전 Cloudflare 설정

무료 요금제의 속도 제한 규칙은 한 개뿐이고, 식에는 경로(URI Path)만 쓸 수 있으며 IP별로 10초 단위로 셉니다. 요청 헤더를 쓸 수 없어 화면 서버(Vercel)의 요청을 그 규칙 안에서 뺄 수 없습니다. 그래서 사용자 지정 규칙(Custom rules, 무료 5개)의 Skip으로 먼저 빼고, 속도 제한 규칙은 나머지 요청에 겁니다. 화면 서버의 요청은 몇 안 되는 Vercel IP에서 오므로 빼지 않으면 IP별 제한에 걸려 목록·상세가 "글을 불러오지 못했어요"로 뜹니다.

1. 토큰을 만듭니다. `openssl rand -hex 32`의 출력을 복사합니다(명령에 값이 없으니 셸 기록에 남지 않습니다). 파일이나 메모에 남기지 않습니다.
2. Vercel Project Settings → Environment Variables에 `KEYCHECK_SERVER_TOKEN`을 Production, Sensitive로 넣고 다시 배포합니다(환경변수는 새 배포부터 적용됩니다).
3. Cloudflare Security → WAF → Custom rules에 규칙을 만듭니다.
   - 식: `(http.host eq "api.keycheck.kr" and any(http.request.headers["x-keycheck-server"][*] eq "<토큰>"))`. 헤더 이름은 소문자로 씁니다. 식 편집기에서 요청 헤더 필드를 쓸 수 없으면 여기서 멈추고 프록시를 끈 채(DNS only) 둡니다(백엔드 안의 상한은 그대로 동작합니다).
   - 동작: Skip. 건너뛸 것은 All rate limiting rules, 그리고 Browser Integrity Check와 Security Level(화면 서버의 Node 요청이 브라우저가 아니라서 걸릴 수 있습니다).
4. Security → WAF → Rate limiting rules에 규칙 하나를 만듭니다.
   - 식: URI Path가 `/api/`로 시작.
   - 기준 IP, 10초에 30회(예), 넘으면 Block 10초. 한국 이동통신은 여러 사용자가 IP 하나를 함께 쓰므로(CGNAT) 빡빡하게 두지 않습니다. 브라우저는 요약을 기다리는 동안 3초마다 상세를 다시 부릅니다.
5. SSL/TLS 모드를 "Full (strict)"로 둡니다(Flexible이면 HTTPS로 넘기는 요청이 끝없이 돕니다). 그다음 DNS의 `api` 레코드 프록시를 켭니다.
6. 확인합니다.
   - `https://keycheck.kr`의 목록과 상세(새로 연 글 포함)가 열립니다.
   - 헤더 없이 연달아 부르면 금방 429가 섞여 나옵니다. 10초 뒤에는 다시 200입니다.

     ```bash
     for i in $(seq 1 40); do curl -s -o /dev/null -w '%{http_code}\n' https://api.keycheck.kr/api/posts/highlights; done | sort | uniq -c
     ```

## 되돌리기

이전 커밋의 이미지로 고정합니다. 태그는 GHCR 패키지 페이지(GitHub 프로필 Packages → `keycheck-backend`)의 버전 목록에 있는 커밋 전체 해시입니다. 이미지는 백엔드가 바뀐 `main` 커밋에만 만들어지므로, 목록에 없는 해시로 고정하면 받기에 실패해 지금 이미지가 그대로 돕니다.

```bash
echo KEYCHECK_TAG=<이전 커밋 전체 해시> | sudo tee /opt/keycheck/.env
sudo systemctl start keycheck-update.service
```

타이머는 고정한 태그를 계속 유지합니다. 다시 `latest`를 따라가려면 `sudo rm /opt/keycheck/.env` 후 같은 `systemctl start`를 실행합니다. 이 `.env`는 compose가 이미지 태그를 고를 때만 읽고 컨테이너에는 들어가지 않습니다(앱 설정은 `/etc/keycheck/keycheck.env`).

## 가짜 요약이 남았을 때

`SUMMARIZER=claude`를 빠뜨린 채 기동했으면 IC 글에 가짜 요약(`summarized_by = 'fake'`)이 남습니다. 환경변수 파일에서 `SUMMARIZER=claude`를 고치고 `KEYCHECK_SUMMARY_REDOFAKE=true` 줄을 더한 뒤 `sudo docker compose up -d --force-recreate app`. 위 SQL에서 `fake`가 0이 되면 그 줄을 지우고 같은 명령으로 다시 띄웁니다. 다시 요약도 하루 요약 상한에 들어갑니다.

## 지난 디스코드 공지 가져오기

봇을 켜기 전의 공지를 한 번 넣는 절차입니다. 묶음(`messages.json` + `attachments/`)은 로컬에서 미리 만들어 둡니다. 서버 이미지의 ENTRYPOINT는 실행 인자를 버리므로 설정은 모두 `-e` 환경변수로 줍니다. 가져오기 컨테이너는 서버 환경변수 파일의 `CRAWL_ENABLED=true` 등이 있어도 모든 수집·요약 작업을 끈 채 뜹니다. 서버 메모리가 2GB라 `app`을 잠깐 멈추고 실행합니다(그동안 사이트가 응답하지 않습니다).

```bash
cd /opt/keycheck
sudo systemctl stop keycheck-update.timer
# 묶음을 /opt/keycheck/notice-import/swagkeys/ 에 복사한다(다른 사용자도 읽을 수 있게)
sudo chmod -R a+rX /opt/keycheck/notice-import
sudo docker compose stop app
sudo docker compose run --rm \
  -e SPRING_PROFILES_ACTIVE=notice-import \
  -e NOTICE_IMPORT_DIR=/import \
  -e NOTICE_IMPORT_VENDOR=SWAGKEYS \
  -v /opt/keycheck/notice-import/swagkeys:/import:ro \
  app
sudo docker compose start app
sudo systemctl start keycheck-update.timer
```

- 결과는 마지막 로그 한 줄(`공지 가져오기 결과: imported=..., skipped=..., attachmentsStored=..., attachmentsFailed=..., aborted=...`)로 봅니다. `aborted=true`나 오류로 끝났으면(저장소 장애, 환경변수 누락) 원인을 고친 뒤 같은 명령을 다시 실행합니다. 이어서 끝냅니다.
- `NOTICE_SUMMARY_ENABLED=true`인 서버에서는 가져온 공지를 `app`이 다시 뜬 뒤 요약 작업이 요약하므로 건수만큼 Claude 비용이 듭니다. 실행 전에 건수를 확인합니다.
- 다른 판매처는 폴더와 `NOTICE_IMPORT_VENDOR`(`GEONWORKS`)를 바꿔 같은 방식으로 실행합니다.
- 끝난 뒤 묶음은 지워도 됩니다(`sudo rm -rf /opt/keycheck/notice-import`).

## 운영 중

- 로그: `sudo docker compose logs -f app` (`/opt/keycheck`에서). 갱신 기록은 `journalctl -u keycheck-update`.
- 환경변수를 바꾼 뒤: `sudo docker compose up -d --force-recreate app`
- 서버에서 직접 손볼 때는 타이머부터 끕니다(`sudo systemctl stop keycheck-update.timer`, 끝나면 `start`). 켜 둔 채면 새 이미지가 올라왔을 때 멈춰 둔 `app`을 다시 띄웁니다.
- 국내 판매(`/api/sales/*`)를 처음 싣는 릴리스는 백엔드를 프론트보다 먼저 배포한다(프론트가 새 엔드포인트와 필드를 쓴다. 나머지는 없어도 무너지지 않게 돼 있지만 그 상태로 두지 않는다).
- 배포 파일(`deploy/`)을 바꿨으면 4절처럼 새로 받아 덮어쓰고, compose를 바꿨으면 `sudo docker compose up -d`, 타이머 유닛을 바꿨으면 `sudo systemctl daemon-reload`.
- Caddy 이미지는 자동으로 갱신하지 않습니다. 가끔 `sudo docker compose pull caddy && sudo docker compose up -d caddy`.

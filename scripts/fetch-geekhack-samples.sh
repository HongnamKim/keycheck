#!/usr/bin/env bash
# geekhack 실제 HTML을 samples/geekhack/에 저장한다 (Task 0).
# 저장본은 원문 저작권 때문에 커밋하지 않는다(.gitignore). 필요한 환경에서 한 번 실행해 쓴다.
# 요청 사이 3초 간격, 이미 받은 파일은 다시 받지 않는다 (덮어쓰려면 --force).
set -euo pipefail

cd "$(dirname "$0")/.."
OUT=samples/geekhack
mkdir -p "$OUT"

UA="keycheck/0.1 (+https://github.com/HongnamKim/keycheck)"
BASE="https://geekhack.org/index.php"
FORCE=${1:-}

# 샘플 8건 (docs/sample-image-sources.md)
TOPICS=(120481 127115 127131 127200 126608 126906 126959 127212)

fetch() {
  local name=$1 url=$2
  local file="$OUT/$name.html"
  if [[ -s "$file" && "$FORCE" != "--force" ]]; then
    echo "skip  $file"
    return
  fi
  curl -sS --fail -A "$UA" -o "$file" "$url"
  echo "saved $file ($(wc -c < "$file") bytes)"
  sleep 3
}

fetch board-132-page0 "$BASE?board=132.0"
fetch board-132-page1 "$BASE?board=132.50"
for id in "${TOPICS[@]}"; do
  fetch "topic-$id" "$BASE?topic=$id.0"
done

date -u +"%Y-%m-%dT%H:%M:%SZ" > "$OUT/FETCHED_AT"
echo "done: $OUT"

#!/usr/bin/env bash
# 백엔드 이미지를 받아 보고, 지금 app 컨테이너의 이미지와 다를 때만 app을 다시 띄운다.
# keycheck-update.timer가 3분마다 부르고, 출력은 journal에 남는다(journalctl -u keycheck-update).
# 이미지 태그는 compose.yaml 옆 .env의 KEYCHECK_TAG(없으면 latest). 태그를 고정하면 그 이미지를 유지한다
set -euo pipefail
cd "$(dirname "$0")"

docker compose pull --quiet app

image=$(docker compose config --images app)
pulled=$(docker image inspect --format '{{.Id}}' "$image")
container=$(docker compose ps --all --quiet app)
current=""
if [ -n "$container" ]; then
  current=$(docker inspect --format '{{.Image}}' "$container")
fi

if [ "$pulled" = "$current" ]; then
  echo "그대로: $image $pulled"
  exit 0
fi

echo "다시 띄움: $image ${current:-없음} -> $pulled"
docker compose up -d app
# 태그를 잃은 옛 이미지를 지운다
docker image prune -f

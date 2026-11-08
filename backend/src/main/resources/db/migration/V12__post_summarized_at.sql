-- 요약을 마지막으로 저장한 시각(다시 요약하면 그 시각으로 바뀐다). 사이트맵 lastmod와 RSS 피드 순서에 쓴다.
-- 요약이 없거나 이 칼럼을 더하기 전에 요약한 글이면 NULL(백필 없음). 실패로 끝난 요약은 바꾸지 않는다.
ALTER TABLE post ADD COLUMN summarized_at TIMESTAMPTZ;

-- 첫 글 본문의 링크 목록([{text, url}]). 요약기가 벤더 판매 링크를 고를 때 쓴다. API로 내보내지 않는다.
-- 틀 글이거나 이 칼럼을 더하기 전에 읽은 글이면 NULL(백필 없음).
ALTER TABLE post ADD COLUMN body_links JSONB;

-- 요약기가 뽑은 벤더 목록([{name, region, url}]). 아직 뽑지 않은 글이면 NULL(백필 없음).
ALTER TABLE post ADD COLUMN vendors JSONB;

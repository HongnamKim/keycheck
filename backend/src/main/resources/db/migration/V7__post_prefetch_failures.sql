-- 백그라운드 미리 읽기가 틀 글을 읽지 못한 횟수. MAX_FAILURES에 닿으면 미리 읽기에서 뺀다(상세 열기는 계속 채운다).
ALTER TABLE post ADD COLUMN prefetch_failures INTEGER NOT NULL DEFAULT 0;

-- 백그라운드 미리 읽기가 채웠고 아직 아무도 상세를 열지 않은 글. 요약 순서에서 사용자가 연 글·정기 수집 글보다 뒤로 간다.
ALTER TABLE post ADD COLUMN prefetched BOOLEAN NOT NULL DEFAULT FALSE;

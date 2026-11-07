-- 목록에서만 읽은 글(틀 글)은 본문·이미지가 없다. 누군가 상세를 처음 열 때 글 첫 페이지를 읽어 채운다.
ALTER TABLE post ALTER COLUMN body_text DROP NOT NULL;

-- 글 첫 페이지를 읽어 채운 시각. NULL이면 틀 글. 지금까지 저장한 글은 모두 본문까지 읽은 글이다.
ALTER TABLE post ADD COLUMN detail_fetched_at TIMESTAMPTZ;
UPDATE post SET detail_fetched_at = created_at;

-- 최근순 목록은 글 번호(작성순) 내림차순이다
CREATE INDEX idx_post_hidden_topic_id ON post (hidden, topic_id DESC);

-- 과거 글 확보 상태 (한 줄). 작성순 목록의 0..next_page-1 쪽을 받아 두었고,
-- topic_id >= floor_topic_id 인 글은 모두 DB에 있다. floor_topic_id가 NULL이면 아무것도 커버하지 않는다.
CREATE TABLE board_backfill (
    id             INTEGER PRIMARY KEY CHECK (id = 1),
    next_page      INTEGER NOT NULL,
    floor_topic_id BIGINT,
    exhausted      BOOLEAN NOT NULL
);

INSERT INTO board_backfill (id, next_page, floor_topic_id, exhausted) VALUES (1, 0, NULL, FALSE);

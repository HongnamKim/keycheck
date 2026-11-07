-- 글을 읽어 온 geekhack 게시판: 'IC'(Interest Checks, 132) 또는 'GB'(Group Buys and Preorders, 70).
-- 지금까지의 글은 모두 IC 게시판에서 읽었다.
ALTER TABLE post ADD COLUMN board VARCHAR(10) NOT NULL DEFAULT 'IC';

-- 게시판 필터의 최근순 목록
CREATE INDEX idx_post_hidden_board_topic_id ON post (hidden, board, topic_id DESC);

-- 과거 글 확보 상태를 게시판마다 한 줄씩 둔다. 기존 한 줄(id = 1)은 IC 게시판의 상태다.
-- id 칸을 지우면 그 기본 키와 CHECK (id = 1)도 함께 지워진다.
ALTER TABLE board_backfill ADD COLUMN board VARCHAR(10);
UPDATE board_backfill SET board = 'IC';
ALTER TABLE board_backfill DROP COLUMN id;
ALTER TABLE board_backfill ALTER COLUMN board SET NOT NULL;
ALTER TABLE board_backfill ADD PRIMARY KEY (board);

INSERT INTO board_backfill (board, next_page, floor_topic_id, exhausted) VALUES ('GB', 0, NULL, FALSE);

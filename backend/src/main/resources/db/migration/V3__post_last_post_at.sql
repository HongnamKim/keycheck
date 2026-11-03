-- 게시판 목록에서 읽은 마지막 게시물(댓글, 없으면 원글) 시각. 목록에서 아직 읽지 못했으면 NULL.
ALTER TABLE post ADD COLUMN last_post_at TIMESTAMPTZ;

-- 작성일 추정의 기준점(작성일이 있는 글)을 글 번호 앞뒤로 찾는다
CREATE INDEX idx_post_posted_topic_id ON post (topic_id) WHERE posted_at IS NOT NULL;

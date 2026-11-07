-- 세부 분류: 키보드는 배열(KB_65 등), 키캡은 제조사(KC_GMK 등). 기타·미분류 글과 아직 정하지 않은 글은 NULL.
ALTER TABLE post ADD COLUMN subcategory VARCHAR(20);

-- 분류·세부 분류 필터의 최근순 목록
CREATE INDEX idx_post_hidden_category_subcategory_topic_id
    ON post (hidden, category, subcategory, topic_id DESC);

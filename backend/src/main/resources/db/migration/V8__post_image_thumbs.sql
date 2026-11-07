-- 축소본(가로 최대 640px) 객체 이름(thumbs/<post_image.id>.webp). NULL이면 아직 없다.
-- 공개 주소는 응답할 때 설정의 공개 주소 앞부분을 붙여 만든다. 같은 글의 같은 주소 행은 같은 객체를 가리킨다.
ALTER TABLE post_image ADD COLUMN thumb_key TEXT;

-- 대표 이미지의 큰 축소본(가로 최대 1280px) 객체 이름(thumbs/<post_image.id>-1280.webp). 대표 이미지만 있다.
-- thumb_key보다 먼저 저장하므로 대표 이미지에 thumb_key가 있으면 이 값도 있다(1280px만 만들 수 없는 긴 이미지는 빼고).
ALTER TABLE post_image ADD COLUMN thumb_large_key TEXT;

-- 축소본을 만들지 못한 횟수(원본을 받지 못했거나 이미지로 읽지 못함). MAX_FAILURES에 닿으면 더 시도하지 않고 화면은 원본을 쓴다.
-- 저장소 장애는 이미지 탓이 아니므로 세지 않는다. 시도 전에 먼저 세고 이미지 탓이 아니면 되돌리므로 만들다 프로세스가 죽은 횟수도 든다.
ALTER TABLE post_image ADD COLUMN thumb_failures INTEGER NOT NULL DEFAULT 0;

-- 축소본 작업이 아직 만들지 않은 후보 이미지를 찾는다
CREATE INDEX idx_post_image_thumb_pending ON post_image (post_id)
    WHERE thumb_key IS NULL AND excluded_reason IS NULL;

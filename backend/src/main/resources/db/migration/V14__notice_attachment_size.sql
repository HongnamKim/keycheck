ALTER TABLE notice_attachment
    -- 원본 사진의 보이는 폭(px). EXIF 방향을 반영한다. 크기를 모르면 NULL.
    ADD COLUMN width  INTEGER,
    -- 원본 사진의 보이는 높이(px). 크기를 모르면 NULL.
    ADD COLUMN height INTEGER;

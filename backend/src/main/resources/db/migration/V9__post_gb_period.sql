-- 공동구매 기간: 요약기가 GB 글의 제목·본문에서 뽑은 시작일·마감일. IC 글이거나 모르면 NULL(취소·연기된 공동구매는 마감일이 NULL).
ALTER TABLE post ADD COLUMN gb_start_date DATE;
ALTER TABLE post ADD COLUMN gb_end_date DATE;

-- GB 글로서 기간을 뽑아 본 요약인지. Claude가 GB 글을 요약했을 때만 TRUE이고, 뽑았는데 NULL이어도 TRUE다.
-- 지금까지의 요약은 기간을 뽑지 않았으므로 FALSE로 둔다(백필 없음). FALSE인 GB 요약은 요약 작업이 다시 요약한다.
ALTER TABLE post ADD COLUMN gb_dates_extracted BOOLEAN NOT NULL DEFAULT FALSE;

-- 요약할 때 요약기에 넘긴 제목. GB 글의 제목이 이것과 달라지면 다시 요약한다. 요약이 없으면 NULL.
-- 지금까지의 요약도 NULL이지만 gb_dates_extracted가 FALSE라 GB 글은 위 조건으로 다시 요약된다.
ALTER TABLE post ADD COLUMN summarized_title VARCHAR(500);

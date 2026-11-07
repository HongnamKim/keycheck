-- 요약을 만든 요약기: 'fake'(가짜 요약기) 또는 Claude 모델 ID. 요약이 없으면 NULL.
-- 지금까지의 요약은 모두 가짜 요약기가 만들었다.
ALTER TABLE post ADD COLUMN summarized_by VARCHAR(100);
UPDATE post SET summarized_by = 'fake' WHERE summary_status = 'DONE';

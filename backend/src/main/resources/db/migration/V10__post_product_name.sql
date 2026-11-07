-- 상품·프로젝트 이름(예: SWG Nightshift). 요약기가 제목·본문에서 원문 표기 그대로 뽑고, 메인이 긴 제목 대신 보여 준다.
-- 모르거나 아직 뽑지 않았으면 NULL. 지금까지의 요약은 이름을 뽑지 않았으므로 NULL로 둔다(백필 없음).
-- keycheck.summary.redo-missing-product-name을 켜면 이름이 없는 요약 완료 글을 다시 요약해 채운다.
ALTER TABLE post ADD COLUMN product_name VARCHAR(200);

"use client";

import LoadFailed from "@/components/LoadFailed";

/**
 * 화면 그리기 중 처리하지 못한 오류의 안전망. Next 내장 오류 화면(영어) 대신 한국어 안내를 보여준다.
 * 루트 레이아웃은 감싸지 않으므로 헤더는 그대로 남는다. retry()는 이 구간을 서버에서 다시 받아 그린다.
 */
export default function ErrorPage({ retry }: { error: unknown; retry: () => void }) {
  return (
    <LoadFailed>
      <button type="button" className="outline-button" onClick={() => retry()}>
        다시 시도
      </button>
    </LoadFailed>
  );
}

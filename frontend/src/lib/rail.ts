/** 메인 가로 줄의 진행 막대와 화살표 계산. 값은 스크롤 상자의 scrollLeft·scrollWidth·clientWidth(px) */

/**
 * 진행 막대 위치(퍼센트 0~100). size는 보이는 폭의 비율, start는 스크롤 위치의 비율.
 * 끝을 넘겨 튕기는 동안(음수·초과 scrollLeft)에도 막대는 줄 안에 둔다. 넘칠 것이 없으면 꽉 찬 막대.
 */
export function railProgress(scrollLeft: number, scrollWidth: number, clientWidth: number): { start: number; size: number } {
  const maxScroll = scrollWidth - clientWidth;
  if (maxScroll <= 0) return { start: 0, size: 100 };
  const clamped = Math.min(Math.max(scrollLeft, 0), maxScroll);
  return { start: (clamped / scrollWidth) * 100, size: (clientWidth / scrollWidth) * 100 };
}

/** 소수점 스크롤 위치(확대 배율 등)로 끝에 닿고도 1px 못 미칠 수 있어 둔 여유 */
const EDGE_SLACK_PX = 1;

/** 화살표를 끌 끝. atStart면 이전, atEnd면 다음 화살표를 끈다 */
export function railEdges(scrollLeft: number, scrollWidth: number, clientWidth: number): { atStart: boolean; atEnd: boolean } {
  return {
    atStart: scrollLeft <= EDGE_SLACK_PX,
    atEnd: scrollLeft + clientWidth >= scrollWidth - EDGE_SLACK_PX,
  };
}

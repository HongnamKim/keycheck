/** 확대 보기에서 delta(+1 다음, -1 이전)만큼 움직인 위치. 끝과 처음은 이어진다. */
export function stepIndex(current: number, delta: number, length: number): number {
  return (((current + delta) % length) + length) % length;
}

/** 확대 보기에서 넘기려면 손가락이 가로로 이만큼은 움직여야 한다 */
const SWIPE_MIN_PX = 50;

/**
 * 터치를 뗄 때까지 움직인 거리(dx, dy)로 넘길 방향을 정한다.
 * 왼쪽으로 밀면 다음(+1), 오른쪽으로 밀면 이전(-1). 짧거나 세로로 더 움직였으면 0.
 */
export function swipeStep(dx: number, dy: number): -1 | 0 | 1 {
  if (Math.abs(dx) < SWIPE_MIN_PX || Math.abs(dx) <= Math.abs(dy)) return 0;
  return dx < 0 ? 1 : -1;
}

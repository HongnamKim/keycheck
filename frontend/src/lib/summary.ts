import type { SummaryStatus } from "./api";

/** 상세 화면이 요약 상태를 다시 묻는 간격 */
export const SUMMARY_POLL_MS = 3_000;
/** 처음 연 때부터 이만큼 지나도 PENDING이면 그만 묻고 늦어진다고 알린다 */
export const SUMMARY_WAIT_LIMIT_MS = 60_000;

/** 요약이 오기 전 프로그레스바가 차오르는 끝(90%). 요약이 오면 100%로 채운다 */
export const SUMMARY_PROGRESS_CAP = 0.9;
/** 차오르는 속도. 처음 연 글은 보통 5~13초 안에 요약이 온다(요약 작업 5초 간격 + Claude 호출 4~5초 + 3초마다 확인) */
const SUMMARY_PROGRESS_TAU_MS = 5_000;

/**
 * 요약을 기다리는 프로그레스바의 채움 비율(0~0.9). 실제 진행률은 알 수 없어(Claude는 한 번에 답한다) 시간으로 추정한다.
 * 처음에 빨리 차고 점점 느려져 15초쯤 90% 근처에 닿고, 그 뒤로는 거의 멈춰 기다린다.
 */
export function summaryProgress(elapsedMs: number): number {
  if (elapsedMs <= 0) return 0;
  return SUMMARY_PROGRESS_CAP * (1 - Math.exp(-elapsedMs / SUMMARY_PROGRESS_TAU_MS));
}

/** watching: 계속 묻는다. late: 1분이 지나 멈춤. failed: 요약 실패로 멈춤. done: 요약이 끝나 화면을 다시 그린다 */
export type SummaryWatchState = "watching" | "late" | "failed" | "done";

/** 마지막으로 받은 요약 상태와 처음 연 때부터 지난 시간(ms)으로 지켜보기 상태를 정한다. 끝난 상태가 시간 제한보다 앞선다. */
export function summaryWatchState(status: SummaryStatus, elapsedMs: number): SummaryWatchState {
  if (status === "DONE") return "done";
  if (status === "FAILED") return "failed";
  return elapsedMs >= SUMMARY_WAIT_LIMIT_MS ? "late" : "watching";
}

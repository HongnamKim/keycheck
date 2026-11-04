import type { SummaryStatus } from "./api";

/** 상세 화면이 요약 상태를 다시 묻는 간격 */
export const SUMMARY_POLL_MS = 3_000;
/** 처음 연 때부터 이만큼 지나도 PENDING이면 그만 묻고 늦어진다고 알린다 */
export const SUMMARY_WAIT_LIMIT_MS = 60_000;

/** watching: 계속 묻는다. late: 1분이 지나 멈춤. failed: 요약 실패로 멈춤. done: 요약이 끝나 화면을 다시 그린다 */
export type SummaryWatchState = "watching" | "late" | "failed" | "done";

/** 마지막으로 받은 요약 상태와 처음 연 때부터 지난 시간(ms)으로 지켜보기 상태를 정한다. 끝난 상태가 시간 제한보다 앞선다. */
export function summaryWatchState(status: SummaryStatus, elapsedMs: number): SummaryWatchState {
  if (status === "DONE") return "done";
  if (status === "FAILED") return "failed";
  return elapsedMs >= SUMMARY_WAIT_LIMIT_MS ? "late" : "watching";
}

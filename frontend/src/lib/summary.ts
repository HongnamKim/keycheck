import type { SummaryStatus } from "./api";

/** 상세 화면이 요약 상태를 다시 묻는 간격 */
export const SUMMARY_POLL_MS = 3_000;
/** 옛 백엔드(summarizingForMs 없음) 경로: 처음 연 때부터 이만큼 지나도 PENDING이면 그만 묻고 늦어진다고 알린다 */
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

/** watching: 계속 묻는다. late: 시간 제한이 지나 멈춤(옛 백엔드 1분, 기다림 3분, 요약 중 서버 기준 7분). failed: 요약 실패로 멈춤. done: 요약이 끝나 화면을 다시 그린다 */
export type SummaryWatchState = "watching" | "late" | "failed" | "done";

/** 마지막으로 받은 요약 상태와 처음 연 때부터 지난 시간(ms)으로 지켜보기 상태를 정한다. 끝난 상태가 시간 제한보다 앞선다. */
export function summaryWatchState(status: SummaryStatus, elapsedMs: number): SummaryWatchState {
  if (status === "DONE") return "done";
  if (status === "FAILED") return "failed";
  return elapsedMs >= SUMMARY_WAIT_LIMIT_MS ? "late" : "watching";
}

/** 차례를 기다리는 중(summarizingForMs가 null)이면 연 때부터 이만큼 지나도 PENDING일 때 그만 묻고 늦어진다고 알린다 */
export const SUMMARY_QUEUE_LIMIT_MS = 180_000;
/** 요약하는 중이면 서버 기준 요약 시간이 이만큼 지나야 늦어진다고 알린다(Claude 호출 한 번이 다시 시도를 포함해 6분 넘게 걸릴 수 있다) */
export const SUMMARY_RUN_LIMIT_MS = 420_000;
/** 요약 중 막대가 차오르는 속도. 서버가 요약을 시작한 뒤 흐른 시간 기준이다 */
const SUMMARY_RUN_TAU_MS = 10_000;

/** 요약 중 프로그레스바의 채움 비율(0~0.9). 서버가 요약을 시작한 뒤 흐른 시간(ms)으로 추정한다 */
export function summaryRunProgress(runMs: number): number {
  if (runMs <= 0) return 0;
  return SUMMARY_PROGRESS_CAP * (1 - Math.exp(-runMs / SUMMARY_RUN_TAU_MS));
}

/** queued: 차례를 기다리는 중. running: 요약하는 중. legacy: 응답에 summarizingForMs가 없는 옛 백엔드 */
export type SummaryPhase = "queued" | "running" | "legacy";

/** 상세 응답의 summarizingForMs로 단계를 정한다. 키가 없으면(undefined) 옛 백엔드다 */
export function summaryPhase(summarizingForMs: number | null | undefined): SummaryPhase {
  if (summarizingForMs === undefined) return "legacy";
  return summarizingForMs === null ? "queued" : "running";
}

/**
 * 단계별 지켜보기 상태. 끝난 상태가 시간 제한보다 앞선다.
 * 옛 백엔드는 summaryWatchState와 같고, 기다리는 중은 연 뒤 3분, 요약하는 중은 서버 기준 요약 시간 7분에 늦어진다.
 */
export function summaryWatchStateFor(
  status: SummaryStatus,
  phase: SummaryPhase,
  sinceMountMs: number,
  runMs: number,
): SummaryWatchState {
  if (status === "DONE") return "done";
  if (status === "FAILED") return "failed";
  if (phase === "legacy") return summaryWatchState(status, sinceMountMs);
  if (phase === "queued") return sinceMountMs >= SUMMARY_QUEUE_LIMIT_MS ? "late" : "watching";
  return runMs >= SUMMARY_RUN_LIMIT_MS ? "late" : "watching";
}

/** 단계별 막대 채움 비율. 기다리는 중(첫 응답 전 포함)은 0, 요약하는 중은 서버 기준 요약 시간, 옛 백엔드는 화면을 연 뒤 흐른 시간으로 채운다 */
export function summaryBarProgress(phase: SummaryPhase, sinceMountMs: number, runMs: number): number {
  if (phase === "running") return summaryRunProgress(runMs);
  if (phase === "legacy") return summaryProgress(sinceMountMs);
  return 0;
}

const SUMMARY_MESSAGES: Record<SummaryWatchState, string> = {
  watching: "요약하는 중이에요",
  // 화면을 다시 그리는 동안은 꽉 찬 막대와 같은 문구를 그대로 둔다
  done: "요약하는 중이에요",
  late: "요약이 늦어지고 있어요. 잠시 뒤 새로고침해 주세요",
  failed: "요약을 만들지 못했어요",
};
/** watching이면서 차례를 기다리는 중일 때의 문구 */
const SUMMARY_QUEUED_MESSAGE = "요약 차례를 기다리고 있어요";

/** 요약 자리의 안내 문구. 지켜보는 중이면서 차례를 기다리면(첫 응답 전 포함) 기다림 문구, 그 밖에는 지켜보기 상태의 문구다 */
export function summaryMessage(state: SummaryWatchState, phase: SummaryPhase): string {
  if (state === "watching" && phase === "queued") return SUMMARY_QUEUED_MESSAGE;
  return SUMMARY_MESSAGES[state];
}

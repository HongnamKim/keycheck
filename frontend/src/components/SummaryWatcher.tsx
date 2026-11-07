"use client";

import { useRouter } from "next/navigation";
import { useEffect, useLayoutEffect, useState } from "react";
import { getPost, type SummaryStatus } from "@/lib/api";
import { SUMMARY_POLL_MS, summaryProgress, summaryWatchState, type SummaryWatchState } from "@/lib/summary";
import styles from "./SummaryWatcher.module.css";

/** 프로그레스바를 다시 채우는 간격 */
const PROGRESS_TICK_MS = 200;
/** 요약이 오면 막대를 끝까지 채우는 시간. 다 채운 뒤 화면을 다시 그린다(CSS .full과 같은 값) */
const PROGRESS_FILL_MS = 300;

const MESSAGES: Record<SummaryWatchState, string> = {
  watching: "요약하는 중이에요",
  // 화면을 다시 그리는 동안은 꽉 찬 막대와 같은 문구를 그대로 둔다
  done: "요약하는 중이에요",
  late: "요약이 늦어지고 있어요. 잠시 뒤 새로고침해 주세요",
  failed: "요약을 만들지 못했어요",
};

/**
 * 요약 대기(PENDING) 글의 "핵심 요약" 자리. 몇 초마다 상세를 다시 물어 DONE이 되면 서버 컴포넌트를 다시 그려
 * 요약·스펙·분류가 그 자리에 들어오게 한다. FAILED거나 처음 연 때부터 1분이 지나면 멈추고 안내만 남긴다.
 * 기다리는 동안은 시간으로 추정한 프로그레스바(summaryProgress)를 채우고, 요약이 오면 끝까지 채운다.
 * 안내가 바뀌거나 요약이 들어온 것은 상세 화면이 이 자리를 감싼 라이브 영역이 읽어 준다.
 */
export default function SummaryWatcher({ postId }: { postId: number }) {
  const router = useRouter();
  const [state, setState] = useState<SummaryWatchState>("watching");
  const [progress, setProgress] = useState(0);

  // cacheComponents라 떠난 화면은 Activity로 숨겨져 상태가 남는다. 다시 보일 때 아래 Effect가 1분을 새로 세므로
  // 지난 방문의 late·failed 안내와 막대도 숨겨질 때 지운다(preserving-ui-state 안내의 방식)
  useLayoutEffect(() => {
    return () => {
      setState("watching");
      setProgress(0);
    };
  }, []);

  useEffect(() => {
    const startedAt = Date.now();
    let controller: AbortController | null = null;
    let timer: ReturnType<typeof setTimeout> | undefined;
    let stopped = false;
    const ticker = setInterval(() => setProgress(summaryProgress(Date.now() - startedAt)), PROGRESS_TICK_MS);

    async function check() {
      controller = new AbortController();
      // 요청 하나가 실패하면 PENDING으로 보고 다음 주기에 다시 묻는다(시간 제한 안에서)
      let status: SummaryStatus = "PENDING";
      try {
        status = (await getPost(postId, controller.signal))?.summaryStatus ?? "PENDING";
      } catch {
        // 다음 주기에 다시
      }
      if (stopped) return;
      const next = summaryWatchState(status, Date.now() - startedAt);
      if (next === "watching") {
        timer = setTimeout(check, SUMMARY_POLL_MS);
        return;
      }
      clearInterval(ticker);
      setState(next);
      if (next === "done") {
        setProgress(1);
        timer = setTimeout(() => router.refresh(), PROGRESS_FILL_MS);
      }
    }

    timer = setTimeout(check, SUMMARY_POLL_MS);
    return () => {
      stopped = true;
      clearTimeout(timer);
      clearInterval(ticker);
      controller?.abort();
    };
  }, [postId, router]);

  const waiting = state === "watching" || state === "done";
  return (
    <div className={styles.status}>
      {waiting && (
        // 추정한 진행이라 화면 읽기에는 숫자를 알리지 않고 아래 문구만 읽힌다
        <span className={styles.track} aria-hidden="true">
          <span
            className={state === "done" ? `${styles.bar} ${styles.full}` : styles.bar}
            style={{ transform: `scaleX(${progress})` }}
          />
        </span>
      )}
      <p className={styles.text}>{MESSAGES[state]}</p>
    </div>
  );
}

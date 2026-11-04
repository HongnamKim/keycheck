"use client";

import { useRouter } from "next/navigation";
import { useEffect, useLayoutEffect, useState } from "react";
import { getPost, type SummaryStatus } from "@/lib/api";
import { SUMMARY_POLL_MS, summaryWatchState, type SummaryWatchState } from "@/lib/summary";
import styles from "./SummaryWatcher.module.css";

const MESSAGES: Record<SummaryWatchState, string> = {
  watching: "요약하는 중이에요",
  // 화면을 다시 그리는 동안은 스피너를 그대로 둔다
  done: "요약하는 중이에요",
  late: "요약이 늦어지고 있어요. 잠시 뒤 새로고침해 주세요",
  failed: "요약을 만들지 못했어요",
};

/**
 * 요약 대기(PENDING) 글의 "핵심 요약" 자리. 몇 초마다 상세를 다시 물어 DONE이 되면 서버 컴포넌트를 다시 그려
 * 요약·스펙·분류가 그 자리에 들어오게 한다. FAILED거나 처음 연 때부터 1분이 지나면 멈추고 안내만 남긴다.
 * 안내가 바뀌거나 요약이 들어온 것은 상세 화면이 이 자리를 감싼 라이브 영역이 읽어 준다.
 */
export default function SummaryWatcher({ postId }: { postId: number }) {
  const router = useRouter();
  const [state, setState] = useState<SummaryWatchState>("watching");

  // cacheComponents라 떠난 화면은 Activity로 숨겨져 상태가 남는다. 다시 보일 때 아래 Effect가 1분을 새로 세므로
  // 지난 방문의 late·failed 안내도 숨겨질 때 지운다(preserving-ui-state 안내의 방식)
  useLayoutEffect(() => {
    return () => setState("watching");
  }, []);

  useEffect(() => {
    const startedAt = Date.now();
    let controller: AbortController | null = null;
    let timer: ReturnType<typeof setTimeout> | undefined;
    let stopped = false;

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
      setState(next);
      if (next === "done") router.refresh();
    }

    timer = setTimeout(check, SUMMARY_POLL_MS);
    return () => {
      stopped = true;
      clearTimeout(timer);
      controller?.abort();
    };
  }, [postId, router]);

  const spinning = state === "watching" || state === "done";
  return (
    <p className={styles.status}>
      {spinning && <span className={styles.spinner} aria-hidden="true" />}
      {MESSAGES[state]}
    </p>
  );
}

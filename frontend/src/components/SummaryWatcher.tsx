"use client";

import { useRouter } from "next/navigation";
import { useEffect, useLayoutEffect, useState } from "react";
import { getPost, type SummaryStatus } from "@/lib/api";
import {
  SUMMARY_POLL_MS,
  summaryBarProgress,
  summaryMessage,
  summaryPhase,
  summaryWatchStateFor,
  type SummaryPhase,
  type SummaryWatchState,
} from "@/lib/summary";
import styles from "./SummaryWatcher.module.css";

/** 프로그레스바를 다시 채우는 간격 */
const PROGRESS_TICK_MS = 200;
/** 요약이 오면 막대를 끝까지 채우는 시간. 다 채운 뒤 화면을 다시 그린다(CSS .full과 같은 값) */
const PROGRESS_FILL_MS = 300;

/**
 * 요약 대기(PENDING) 글의 "핵심 요약" 자리. 뜨자마자, 그 뒤로 몇 초마다 상세를 다시 물어 DONE이 되면 서버 컴포넌트를
 * 다시 그려 요약·스펙·분류가 그 자리에 들어오게 한다. 응답의 summarizingForMs로 단계를 나눠 보여 준다.
 * 차례를 기다리는 중(null, 첫 응답 전 포함)이면 차오르지 않는 기다림 표시를 보이고, 연 뒤 3분이 지나면 멈춘다.
 * 요약하는 중(숫자)이면 서버가 요약을 시작한 뒤 흐른 시간으로 막대를 채우고(summaryRunProgress), 서버 기준 7분이 지나야 멈춘다.
 * 옛 백엔드(키 없음)면 예전처럼 연 뒤 흐른 시간으로 채우고(summaryProgress) 1분이 지나면 멈춘다.
 * FAILED여도 멈춘다. 멈추면 안내만 남기고, 요약이 오면 막대를 끝까지 채운다.
 * 안내가 바뀌거나 요약이 들어온 것은 상세 화면이 이 자리를 감싼 라이브 영역이 읽어 준다.
 */
export default function SummaryWatcher({ postId }: { postId: number }) {
  const router = useRouter();
  const [state, setState] = useState<SummaryWatchState>("watching");
  const [progress, setProgress] = useState(0);
  const [phase, setPhase] = useState<SummaryPhase>("queued");

  // cacheComponents라 떠난 화면은 Activity로 숨겨져 상태가 남는다. 다시 보일 때 아래 Effect가 시간을 새로 세므로
  // 지난 방문의 late·failed 안내와 막대, 단계도 숨겨질 때 지운다(preserving-ui-state 안내의 방식).
  // 마지막으로 받은 값과 받은 시각은 Effect 안에 있어 다시 보일 때 새로 시작한다
  useLayoutEffect(() => {
    return () => {
      setState("watching");
      setProgress(0);
      setPhase("queued");
    };
  }, []);

  useEffect(() => {
    const startedAt = Date.now();
    let controller: AbortController | null = null;
    let timer: ReturnType<typeof setTimeout> | undefined;
    let stopped = false;
    // 마지막으로 받은 단계와 summarizingForMs, 받은 시각. 요청이 실패하면 그대로 두고 지난 시간을 더해 쓴다
    let lastPhase: SummaryPhase = "queued";
    let lastRunMs = 0;
    let receivedAt = startedAt;
    const runMs = () => lastRunMs + (Date.now() - receivedAt);
    // 기다리는 중에는 막대를 채우지 않는다
    const ticker = setInterval(
      () => setProgress(summaryBarProgress(lastPhase, Date.now() - startedAt, runMs())),
      PROGRESS_TICK_MS,
    );

    async function check() {
      controller = new AbortController();
      // 요청 하나가 실패하면 PENDING으로 보고 다음 주기에 다시 묻는다(시간 제한 안에서)
      // 원문을 읽은 글에만 그려지므로 채우기를 끈다(JS를 실행하는 봇이 미리 읽은 글의 요약 순서를 앞당기지 않게)
      let status: SummaryStatus = "PENDING";
      try {
        const post = await getPost(postId, controller.signal, { fill: false });
        if (post) {
          status = post.summaryStatus;
          // 옛 백엔드는 키가 없어 undefined → legacy
          lastPhase = summaryPhase(post.summarizingForMs);
          lastRunMs = post.summarizingForMs ?? 0;
          receivedAt = Date.now();
        }
      } catch {
        // 다음 주기에 다시
      }
      if (stopped) return;
      setPhase(lastPhase);
      const next = summaryWatchStateFor(status, lastPhase, Date.now() - startedAt, runMs());
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

    // 첫 확인은 뜨자마자 한다(막대가 차올랐다가 기다림으로 돌아가는 흔들림을 막는다)
    timer = setTimeout(check, 0);
    return () => {
      stopped = true;
      clearTimeout(timer);
      clearInterval(ticker);
      controller?.abort();
    };
  }, [postId, router]);

  const waiting = state === "watching" || state === "done";
  const queued = state === "watching" && phase === "queued";
  return (
    <div className={styles.status}>
      {waiting && (
        // 추정한 진행이라 화면 읽기에는 숫자를 알리지 않고 아래 문구만 읽힌다
        <span className={styles.track} aria-hidden="true">
          {queued ? (
            // key가 달라 막대와 바뀔 때 새로 그려져, 오가는 조각에서 채움으로 이어지는 전환이 생기지 않는다
            <span key="queued" className={`${styles.bar} ${styles.queued}`} />
          ) : (
            <span
              key="bar"
              className={state === "done" ? `${styles.bar} ${styles.full}` : styles.bar}
              style={{ transform: `scaleX(${progress})` }}
            />
          )}
        </span>
      )}
      <p className={styles.text}>{summaryMessage(state, phase)}</p>
    </div>
  );
}

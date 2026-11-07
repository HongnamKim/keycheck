"use client";

import { useEffect, useRef, useState, type ReactNode } from "react";
import type { BoardFilter } from "@/lib/api";
import { railEdges, railProgress } from "@/lib/rail";
import styles from "./RailScroller.module.css";

/** 가로 줄의 지금 모양: 버튼을 숨길 끝과 진행 막대 위치(퍼센트) */
interface RailView {
  atStart: boolean;
  atEnd: boolean;
  start: number;
  size: number;
}

function measure(track: HTMLElement): RailView {
  const { scrollLeft, scrollWidth, clientWidth } = track;
  return { ...railEdges(scrollLeft, scrollWidth, clientWidth), ...railProgress(scrollLeft, scrollWidth, clientWidth) };
}

function Chevron({ direction }: { direction: 1 | -1 }) {
  return (
    <svg viewBox="0 0 24 24" width={20} height={20} aria-hidden="true">
      <path d={direction === 1 ? "M9 6l6 6-6 6" : "M15 6l-6 6 6 6"} />
    </svg>
  );
}

/**
 * 메인의 가로로 넘기는 카드 줄과 진행 막대. children은 카드 목록(li), name은 버튼 이름에 붙일 게시판 이름(IC, 공동구매).
 * 데스크톱은 줄 위 양쪽에 이전·다음 버튼을 띄우고, 더 넘길 쪽을 흐린다. 끝에 닿은 쪽 버튼은 숨긴다.
 * placeholder면 글을 받기 전 자리라 상태를 잡지 않는다.
 */
export function RailTrack({
  board,
  name,
  placeholder = false,
  children,
}: {
  board: BoardFilter;
  name: string;
  placeholder?: boolean;
  children: ReactNode;
}) {
  const trackRef = useRef<HTMLUListElement>(null);
  /** 아직 줄을 재지 않았으면(숨겨진 동안 포함) null */
  const [view, setView] = useState<RailView | null>(null);

  useEffect(() => {
    const track = trackRef.current;
    if (placeholder || !track) return;
    const update = () => setView(measure(track));
    // 숨겨졌다 다시 보일 때(cacheComponents의 Activity)도 붙을 때마다 DOM에서 다시 잰다
    update();
    track.addEventListener("scroll", update, { passive: true });
    const observer = new ResizeObserver(update);
    observer.observe(track);
    return () => {
      track.removeEventListener("scroll", update);
      observer.disconnect();
      setView(null);
    };
  }, [placeholder]);

  function scrollPage(direction: 1 | -1) {
    const track = trackRef.current;
    if (!track) return;
    const reduceMotion = window.matchMedia("(prefers-reduced-motion: reduce)").matches;
    // 한 화면만큼 넘기면 scroll-snap이 가장 가까운 카드 머리에 맞춘다
    track.scrollBy({ left: direction * track.clientWidth, behavior: reduceMotion ? "auto" : "smooth" });
  }

  const showPrev = !placeholder && view !== null && !view.atStart;
  const showNext = !placeholder && view !== null && !view.atEnd;
  const railClass = [styles.rail, showPrev && styles.fadeStart, showNext && styles.fadeEnd].filter(Boolean).join(" ");
  const placeClass = board === "gb" ? styles.buttonGb : styles.buttonIc;

  return (
    <div aria-hidden={placeholder || undefined}>
      <div className={railClass}>
        <ul ref={trackRef} className={styles.track}>
          {children}
        </ul>
        {showPrev && (
          <button
            type="button"
            className={`${styles.button} ${styles.prev} ${placeClass}`}
            onClick={() => scrollPage(-1)}
            aria-label={`이전 ${name} 글`}
          >
            <Chevron direction={-1} />
          </button>
        )}
        {showNext && (
          <button
            type="button"
            className={`${styles.button} ${styles.next} ${placeClass}`}
            onClick={() => scrollPage(1)}
            aria-label={`다음 ${name} 글`}
          >
            <Chevron direction={1} />
          </button>
        )}
      </div>
      <div className={styles.progress}>
        {!placeholder && view && (
          <span className={styles.bar} style={{ left: `${view.start}%`, width: `${view.size}%` }} />
        )}
      </div>
    </div>
  );
}

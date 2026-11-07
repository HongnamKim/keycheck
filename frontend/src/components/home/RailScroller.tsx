"use client";

import { createContext, useCallback, useContext, useEffect, useRef, useState, type ReactNode } from "react";
import type { BoardFilter } from "@/lib/api";
import { railEdges, railProgress } from "@/lib/rail";
import styles from "./RailScroller.module.css";

/** 가로 줄의 지금 모양: 화살표를 끌 끝과 진행 막대 위치(퍼센트) */
interface RailView {
  atStart: boolean;
  atEnd: boolean;
  start: number;
  size: number;
}

interface Rail {
  /** 아직 줄을 재지 않았으면(줄을 받기 전, 숨겨진 동안) null */
  view: RailView | null;
  /** 줄 상자를 붙여 스크롤·크기 변화를 지켜본다. 돌려준 함수로 뗀다 */
  connect: (track: HTMLElement) => () => void;
  /** 한 화면만큼 넘긴다. direction 1이면 다음, -1이면 이전 */
  scrollPage: (direction: 1 | -1) => void;
}

const RailContext = createContext<Rail | null>(null);

function useRail(): Rail {
  const rail = useContext(RailContext);
  if (!rail) throw new Error("RailScroller 안에서만 쓴다");
  return rail;
}

function measure(track: HTMLElement): RailView {
  const { scrollLeft, scrollWidth, clientWidth } = track;
  return { ...railEdges(scrollLeft, scrollWidth, clientWidth), ...railProgress(scrollLeft, scrollWidth, clientWidth) };
}

/**
 * 메인 가로 줄 하나. 섹션 머리의 화살표(RailArrows)와 줄(RailTrack)이 같은 스크롤 상태를 쓴다.
 * 머리는 바로 그리고 줄은 글을 받은 뒤 들어오므로, 줄이 붙기 전까지 화살표는 꺼져 있다.
 */
export default function RailScroller({ children }: { children: ReactNode }) {
  const trackRef = useRef<HTMLElement | null>(null);
  const [view, setView] = useState<RailView | null>(null);

  const connect = useCallback((track: HTMLElement) => {
    trackRef.current = track;
    const update = () => setView(measure(track));
    // 숨겨졌다 다시 보일 때(cacheComponents의 Activity)도 붙을 때마다 DOM에서 다시 잰다
    update();
    track.addEventListener("scroll", update, { passive: true });
    const observer = new ResizeObserver(update);
    observer.observe(track);
    return () => {
      track.removeEventListener("scroll", update);
      observer.disconnect();
      trackRef.current = null;
      setView(null);
    };
  }, []);

  const scrollPage = useCallback((direction: 1 | -1) => {
    const track = trackRef.current;
    if (!track) return;
    const reduceMotion = window.matchMedia("(prefers-reduced-motion: reduce)").matches;
    // 한 화면만큼 넘기면 scroll-snap이 가장 가까운 카드 머리에 맞춘다
    track.scrollBy({ left: direction * track.clientWidth, behavior: reduceMotion ? "auto" : "smooth" });
  }, []);

  return <RailContext value={{ view, connect, scrollPage }}>{children}</RailContext>;
}

function Chevron({ direction, size }: { direction: 1 | -1; size: number }) {
  return (
    <svg viewBox="0 0 24 24" width={size} height={size} aria-hidden="true">
      <path d={direction === 1 ? "M9 6l6 6-6 6" : "M15 6l-6 6 6 6"} />
    </svg>
  );
}

/**
 * 섹션 머리의 좌우 화살표(데스크톱만). 끝에 닿은 쪽은 끈다. name은 게시판 이름(IC, 공동구매)
 * disabled 대신 aria-disabled로 끈다: 키보드로 누르던 화살표가 끝에서 꺼져도 포커스가 그 자리에 남는다
 */
export function RailArrows({ name }: { name: string }) {
  const { view, scrollPage } = useRail();
  const offPrev = !view || view.atStart;
  const offNext = !view || view.atEnd;
  return (
    <>
      <button
        type="button"
        className={styles.arrow}
        aria-disabled={offPrev}
        onClick={() => {
          if (!offPrev) scrollPage(-1);
        }}
        aria-label={`이전 ${name} 글`}
      >
        <Chevron direction={-1} size={18} />
      </button>
      <button
        type="button"
        className={styles.arrow}
        aria-disabled={offNext}
        onClick={() => {
          if (!offNext) scrollPage(1);
        }}
        aria-label={`다음 ${name} 글`}
      >
        <Chevron direction={1} size={18} />
      </button>
    </>
  );
}

/**
 * 가로로 넘기는 카드 줄과 진행 막대. children은 카드 목록(li). 데스크톱은 오른쪽을 흐리고 다음 버튼을 띄운다.
 * placeholder면 글을 받기 전 자리라 상태를 잡지 않는다.
 */
export function RailTrack({
  board,
  placeholder = false,
  children,
}: {
  board: BoardFilter;
  placeholder?: boolean;
  children: ReactNode;
}) {
  const { view, connect, scrollPage } = useRail();
  const trackRef = useRef<HTMLUListElement>(null);

  useEffect(() => {
    const track = trackRef.current;
    if (placeholder || !track) return;
    return connect(track);
  }, [connect, placeholder]);

  const atEnd = !placeholder && view?.atEnd;
  return (
    <div aria-hidden={placeholder || undefined}>
      <div className={atEnd ? `${styles.rail} ${styles.atEnd}` : styles.rail}>
        <ul ref={trackRef} className={styles.track}>
          {children}
        </ul>
        {/* 머리의 다음 화살표와 같은 일을 하는 마우스용 버튼. 키보드·화면 읽기는 머리 화살표를 쓴다 */}
        {!placeholder && !atEnd && (
          <button
            type="button"
            className={`${styles.next} ${board === "gb" ? styles.nextGb : styles.nextIc}`}
            onClick={() => scrollPage(1)}
            tabIndex={-1}
            aria-hidden="true"
          >
            <Chevron direction={1} size={20} />
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

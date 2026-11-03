"use client";

import { useRef, useState, type KeyboardEvent, type MouseEvent, type TouchEvent } from "react";
import { stepIndex, swipeStep } from "@/lib/gallery";
import RemoteImage from "./RemoteImage";
import styles from "./Gallery.module.css";

/** 원문 이미지 썸네일 목록. 누르면 페이지 안에서 크게 보여주고 좌우로(키보드·버튼·스와이프) 넘긴다. */
export default function Gallery({ urls }: { urls: string[] }) {
  const dialogRef = useRef<HTMLDialogElement>(null);
  const [index, setIndex] = useState(0);
  /** 한 손가락 터치를 시작한 위치. 두 손가락(확대) 중이면 null */
  const touchStartRef = useRef<{ x: number; y: number } | null>(null);
  const many = urls.length > 1;

  function open(i: number) {
    setIndex(i);
    dialogRef.current?.showModal();
  }

  function close() {
    dialogRef.current?.close();
  }

  function step(delta: number) {
    setIndex((current) => stepIndex(current, delta, urls.length));
  }

  function onKeyDown(e: KeyboardEvent<HTMLDialogElement>) {
    if (e.key === "ArrowLeft") step(-1);
    if (e.key === "ArrowRight") step(1);
  }

  function onTouchStart(e: TouchEvent<HTMLDialogElement>) {
    const touch = e.touches[0];
    touchStartRef.current = e.touches.length === 1 ? { x: touch.clientX, y: touch.clientY } : null;
  }

  function onTouchEnd(e: TouchEvent<HTMLDialogElement>) {
    const start = touchStartRef.current;
    touchStartRef.current = null;
    if (!start || !many) return;
    const touch = e.changedTouches[0];
    const delta = swipeStep(touch.clientX - start.x, touch.clientY - start.y);
    if (delta === 0) return;
    // 스와이프 뒤 따라올 수 있는 클릭을 막아, 바깥을 누른 것으로 보고 닫지 않게 한다.
    // 브라우저가 끌기(스크롤)로 처리한 터치는 취소할 수 없고, 그때는 클릭도 만들지 않는다.
    if (e.cancelable) e.preventDefault();
    step(delta);
  }

  // 이미지 바깥(어두운 빈 곳)을 누르면 닫는다
  function onClick(e: MouseEvent<HTMLDialogElement>) {
    if ((e.target as HTMLElement).dataset.backdrop !== undefined) close();
  }

  return (
    <>
      <ul className={styles.grid}>
        {urls.map((url, i) => (
          <li key={i}>
            <button
              type="button"
              className={styles.thumb}
              onClick={() => open(i)}
              aria-label={`원문 이미지 ${i + 1} 크게 보기`}
            >
              <RemoteImage src={url} alt="" fill sizes="(max-width: 640px) 50vw, 240px" className={styles.thumbImage} />
            </button>
          </li>
        ))}
      </ul>

      <dialog
        ref={dialogRef}
        className={styles.viewer}
        aria-label="원문 이미지 크게 보기"
        onKeyDown={onKeyDown}
        onClick={onClick}
        onTouchStart={onTouchStart}
        onTouchEnd={onTouchEnd}
        data-backdrop=""
      >
        <div className={styles.bar}>
          <span className={styles.counter}>
            {index + 1} / {urls.length}
          </span>
          <a href={urls[index]} target="_blank" rel="noopener noreferrer" className={styles.source}>
            원본 열기
          </a>
          <button type="button" className={styles.close} onClick={close} aria-label="닫기">
            <svg viewBox="0 0 24 24" aria-hidden="true">
              <path d="M6 6l12 12M18 6L6 18" />
            </svg>
          </button>
        </div>

        <div className={styles.stage} data-backdrop="">
          <div className={styles.frame} data-backdrop="">
            {/* key로 이미지마다 새로 그려, 앞 이미지의 최적화 실패 상태가 다음 이미지로 넘어가지 않게 한다 */}
            <RemoteImage
              key={urls[index]}
              src={urls[index]}
              alt={`원문 이미지 ${index + 1}`}
              fill
              sizes="100vw"
              className={styles.image}
            />
          </div>
          {many && (
            <>
              <button type="button" className={`${styles.nav} ${styles.prev}`} onClick={() => step(-1)} aria-label="이전 이미지">
                <svg viewBox="0 0 24 24" aria-hidden="true">
                  <path d="M15 5l-7 7 7 7" />
                </svg>
              </button>
              <button type="button" className={`${styles.nav} ${styles.next}`} onClick={() => step(1)} aria-label="다음 이미지">
                <svg viewBox="0 0 24 24" aria-hidden="true">
                  <path d="M9 5l7 7-7 7" />
                </svg>
              </button>
            </>
          )}
        </div>
      </dialog>
    </>
  );
}

"use client";

import { useEffect, useId, useRef, useState } from "react";
import styles from "./InfoTip.module.css";

/**
 * 작은 안내 아이콘. 마우스를 올리거나 키보드로 포커스하면 설명이 보이고, 휴대폰에서는 눌러서 열고 닫는다.
 * 바깥을 누르거나 Esc를 누르면 닫힌다. 설명은 position이 정해진 가장 가까운 부모의 폭에 맞춰 그 아래에 펼쳐진다.
 */
export default function InfoTip({ label, text }: { label: string; text: string }) {
  const [open, setOpen] = useState(false);
  const wrapRef = useRef<HTMLSpanElement>(null);
  const tipId = useId();

  useEffect(() => {
    if (!open) return;
    function onPointerDown(e: PointerEvent) {
      if (!wrapRef.current?.contains(e.target as Node)) setOpen(false);
    }
    function onKeyDown(e: KeyboardEvent) {
      if (e.key === "Escape") setOpen(false);
    }
    document.addEventListener("pointerdown", onPointerDown);
    document.addEventListener("keydown", onKeyDown);
    return () => {
      document.removeEventListener("pointerdown", onPointerDown);
      document.removeEventListener("keydown", onKeyDown);
    };
  }, [open]);

  return (
    <span ref={wrapRef} className={styles.wrap} data-open={open || undefined}>
      <button
        type="button"
        className={styles.button}
        aria-label={label}
        aria-describedby={tipId}
        aria-expanded={open}
        onClick={() => setOpen((v) => !v)}
      >
        <svg viewBox="0 0 20 20" aria-hidden="true">
          <circle cx="10" cy="10" r="8" />
          <path d="M10 9v5M10 6.2v.1" />
        </svg>
      </button>
      <span id={tipId} role="tooltip" className={styles.tip}>
        {text}
      </span>
    </span>
  );
}

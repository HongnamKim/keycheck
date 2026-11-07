import styles from "./PeriodRange.module.css";

const ARROW = " → ";

/**
 * 공동구매 기간 글(예: 10/30 → 11/13, ~ 11/13, 7/6 → 7/27 끝남).
 * 화살표는 사이트 고정폭 글꼴(IBM Plex Mono, latin)에 없어 대체 글꼴(Arial 확대본)이 크게 그리므로 시스템 고정폭 글꼴로 그리고,
 * 줄이 바뀌어도 화살표가 마감일과 함께 다음 줄로 가게 붙인다(시안 "→&nbsp;11/13").
 */
export default function PeriodRange({ range }: { range: string }) {
  const at = range.indexOf(ARROW);
  if (at < 0) return range;
  return (
    <>
      {range.slice(0, at)} <span className={styles.arrow}>→</span>
      {" "}
      {range.slice(at + ARROW.length)}
    </>
  );
}

import styles from "./KoreanVendorBadge.module.css";

/** 한국 벤더가 있는 글의 배지. 공동구매 배지(강조색 채움)와 겹치지 않게 강조색 테두리. 사진 위에서는 className으로 모양을 덮는다 */
export default function KoreanVendorBadge({ className }: { className?: string }) {
  return <span className={[styles.badge, className].filter(Boolean).join(" ")}>한국 벤더</span>;
}

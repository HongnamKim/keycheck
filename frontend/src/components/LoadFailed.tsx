import type { ReactNode } from "react";
import styles from "./LoadFailed.module.css";

/**
 * 백엔드에서 글을 불러오지 못했을 때(멈춤·시간 초과·오류) 목록·상세·오류 화면이 보여주는 안내.
 * children은 다시 시도 링크나 버튼(outline-button)이다.
 */
export default function LoadFailed({ children }: { children: ReactNode }) {
  return (
    <div className={styles.failed}>
      <p>글을 불러오지 못했어요. 잠시 후 다시 시도해 주세요.</p>
      {children}
    </div>
  );
}

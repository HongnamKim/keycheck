import Link from "next/link";
import styles from "./page.module.css";

export default function NotFound() {
  return (
    <div className={styles.message}>
      <p>페이지를 찾을 수 없어요. 지워졌거나 숨겨진 글일 수 있어요.</p>
      <p>
        <Link href="/">목록으로</Link>
      </p>
    </div>
  );
}

import { formatLastPost, formatPostedDate } from "@/lib/format";
import styles from "./PostDates.module.css";

const ESTIMATED_HINT = "글 번호로 추정한 날짜예요";

/**
 * 작성일과 마지막 댓글 시각을 " · "로 잇는다. 보일 것이 없으면 아무것도 그리지 않는다.
 * leadingDot이면 앞 글에 이어 붙도록 맨 앞에도 " · "를 둔다. now는 상대 시간 기준 시각.
 */
export default function PostDates({
  postedAt,
  estimatedPostedAt,
  lastPostAt,
  replies,
  now,
  leadingDot = false,
}: {
  postedAt: string | null;
  estimatedPostedAt: string | null;
  lastPostAt: string | null;
  replies: number;
  now: Date;
  leadingDot?: boolean;
}) {
  const posted = formatPostedDate(postedAt, estimatedPostedAt);
  const lastPost = formatLastPost(lastPostAt, replies, now);
  if (!posted && !lastPost) return null;

  return (
    <>
      {leadingDot && " · "}
      {posted &&
        (posted.estimated ? (
          <span className={styles.piece} title={ESTIMATED_HINT}>
            {posted.text}
            <span className="visually-hidden"> ({ESTIMATED_HINT})</span>
          </span>
        ) : (
          <span className={styles.piece}>{posted.text}</span>
        ))}
      {posted && lastPost && " · "}
      {lastPost && <span className={styles.piece}>{lastPost}</span>}
    </>
  );
}

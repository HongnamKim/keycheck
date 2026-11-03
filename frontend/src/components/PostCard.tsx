import Link from "next/link";
import type { PostCard as PostCardData } from "@/lib/api";
import { formatCheckedAt, formatViews, postCategoryLabel } from "@/lib/format";
import PostDates from "./PostDates";
import RemoteImage from "./RemoteImage";
import styles from "./PostCard.module.css";

/**
 * 목록 카드. 카드 전체가 href(상세 주소)로 가는 링크다. now는 확인·마지막 댓글 시각을 상대 시간으로 바꿀 기준 시각,
 * eager는 처음부터 보이는 카드라 이미지를 지연 로딩하지 않을지 여부.
 */
export default function PostCard({
  post,
  href,
  now,
  eager = false,
}: {
  post: PostCardData;
  href: string;
  now: Date;
  eager?: boolean;
}) {
  const summary = post.summaryStatus === "DONE" ? post.summaryFirstLine : null;

  return (
    <Link href={href} className={styles.card}>
      {post.representativeImageUrl ? (
        <span className={styles.media}>
          <RemoteImage
            src={post.representativeImageUrl}
            alt=""
            fill
            loading={eager ? "eager" : "lazy"}
            sizes="(max-width: 640px) 100vw, (max-width: 960px) 50vw, 320px"
            className={styles.image}
          />
        </span>
      ) : post.detailLoaded ? (
        <span className={styles.noImage}>이미지 없음</span>
      ) : (
        // 목록에서만 가져온 틀 글. 상세를 열면 백엔드가 원문을 읽어 이미지를 채운다
        <span className={`${styles.noImage} ${styles.notLoaded}`}>글을 열면 이미지를 불러와요</span>
      )}
      <div className={styles.body}>
        <span className={styles.category}>{postCategoryLabel(post.category, post.subcategory)}</span>
        <h2 className={styles.title}>{post.title}</h2>
        {summary ? (
          <span className={styles.summary}>{summary}</span>
        ) : (
          <span className={styles.pending}>요약 준비 중</span>
        )}
        {/* 작성일·마지막 댓글. 둘 다 없으면 비어 CSS가 감춘다 */}
        <span className={styles.dates}>
          <PostDates
            postedAt={post.postedAt}
            estimatedPostedAt={post.estimatedPostedAt}
            lastPostAt={post.lastPostAt}
            replies={post.replies}
            now={now}
          />
        </span>
        <span className={styles.meta}>
          조회 {formatViews(post.views)} · 댓글 {post.replies} · {formatCheckedAt(new Date(post.statsCheckedAt), now)}
        </span>
      </div>
    </Link>
  );
}

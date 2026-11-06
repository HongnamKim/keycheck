import Link from "next/link";
import { Suspense } from "react";
import { unstable_rethrow } from "next/navigation";
import { getPosts, type BoardFilter, type PostBatch, type PostCard } from "@/lib/api";
import { BOARD_INFO, formatPostedDate, formatViews, postCategoryLabel } from "@/lib/format";
import { imageSources } from "@/lib/image";
import { listHref, postHref } from "@/lib/query";
import BoardBadge from "./BoardBadge";
import FreshListLink from "./FreshListLink";
import RemoteImage from "./RemoteImage";
import styles from "./BoardColumn.module.css";

/** 메인에 보여주는 게시판별 최근 글 수. 메인에 다른 내용이 늘면 5로 줄인다 */
const RECENT_COUNT = 10;

/** 메인의 게시판 칸. 제목·설명·전체 보기는 바로 그리고, 최근 글은 따로 받아 한쪽이 느려도 다른 칸을 막지 않는다 */
export default function BoardColumn({ board }: { board: BoardFilter }) {
  const info = BOARD_INFO[board];
  const titleId = `board-${board}-title`;

  return (
    <section className={styles.column} aria-labelledby={titleId}>
      <div className={styles.head}>
        <div className={styles.titles}>
          <h2 id={titleId} className={styles.title}>
            {/* 제목 글자와 겹치므로(IC IC) 화면 읽기 프로그램에는 배지를 읽히지 않는다 */}
            <BoardBadge board={board === "gb" ? "GB" : "IC"} label={info.badge} className={styles.titleBadge} decorative />
            <span>{info.title}</span>
          </h2>
          <p className={styles.desc}>{info.desc}</p>
        </div>
        <FreshListLink href={listHref({ board, category: "all", sort: "recent" })} className={styles.more}>
          전체 보기 →
        </FreshListLink>
      </div>
      <Suspense fallback={<p className={styles.message}>글을 불러오는 중이에요</p>}>
        <RecentPosts board={board} />
      </Suspense>
    </section>
  );
}

async function RecentPosts({ board }: { board: BoardFilter }) {
  let first: PostBatch;
  try {
    first = await getPosts(board, "all", "recent");
  } catch (err) {
    unstable_rethrow(err);
    // 한 게시판이 실패해도 메인의 다른 칸은 그대로 둔다
    console.error(err);
    return <p className={styles.message}>글을 불러오지 못했어요</p>;
  }
  const posts = first.items.slice(0, RECENT_COUNT);
  if (posts.length === 0) return <p className={styles.message}>아직 수집된 글이 없어요</p>;

  return (
    <ul className={styles.list}>
      {posts.map((post) => (
        <li key={post.id}>
          <RecentItem post={post} />
        </li>
      ))}
    </ul>
  );
}

/** 썸네일 왼쪽 목록형 항목. 메인에서 연 상세는 "메인으로" 돌아온다(쿼리 없는 상세 주소) */
function RecentItem({ post }: { post: PostCard }) {
  const summary = post.summaryStatus === "DONE" ? post.summaryFirstLine : null;
  const posted = formatPostedDate(post.postedAt, post.estimatedPostedAt);
  const image = post.representativeImageUrl
    ? imageSources(post.representativeThumbUrl, post.representativeImageUrl)
    : null;

  return (
    <Link href={postHref(post.id)} className={styles.item}>
      <span className={styles.thumb}>
        {image ? (
          <RemoteImage src={image.src} fallbackSrc={image.fallback} alt="" fill sizes="112px" className={styles.image} />
        ) : (
          <span className={styles.noImage}>{post.detailLoaded ? "이미지 없음" : "열면 불러와요"}</span>
        )}
      </span>
      <span className={styles.body}>
        <span className={styles.category}>
          <BoardBadge board={post.board} />
          <span>{postCategoryLabel(post.category, post.subcategory)}</span>
        </span>
        <span className={styles.name}>{post.title}</span>
        {summary ? (
          <span className={styles.summary}>{summary}</span>
        ) : (
          <span className={`${styles.summary} ${styles.pending}`}>요약 준비 중</span>
        )}
        <span className={styles.meta}>
          {posted && `${posted.text} · `}조회 {formatViews(post.views)} · 댓글 {post.replies}
        </span>
      </span>
    </Link>
  );
}

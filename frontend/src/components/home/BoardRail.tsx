import { Suspense } from "react";
import { unstable_rethrow } from "next/navigation";
import { getPosts, type BoardFilter, type PostBatch } from "@/lib/api";
import { kstDateKey } from "@/lib/format";
import { listHref } from "@/lib/query";
import FreshListLink from "../FreshListLink";
import RailCard, { RailCardPlaceholder } from "./RailCard";
import RailScroller, { RailArrows, RailTrack } from "./RailScroller";
import styles from "./BoardRail.module.css";

/** 메인에 보여주는 게시판별 최근 글 수 */
const RECENT_COUNT = 10;
/** 글을 받기 전 그리는 카드 자리 수(데스크톱 한 화면을 채운다) */
const PLACEHOLDER_COUNT = 5;

/** 게시판 줄 머리: 번호, 제목, 출처, 화살표에 붙일 게시판 이름 */
const RAILS: Record<BoardFilter, { no: string; title: string; source: string; name: string }> = {
  ic: { no: "01", title: "IC · 관심 조사", source: "geekhack Interest Checks", name: "IC" },
  gb: { no: "02", title: "공동구매", source: "geekhack Group Buys and Preorders", name: "공동구매" },
};

/**
 * 메인의 게시판 가로 줄. 머리(번호·제목·전체 보기)는 바로 그리고, 최근 글은 따로 받아
 * 한쪽이 느려도 다른 줄과 하이라이트를 막지 않는다.
 */
export default function BoardRail({ board }: { board: BoardFilter }) {
  const rail = RAILS[board];
  const titleId = `home-${board}-title`;

  return (
    <section id={`home-${board}`} className={styles.sec} aria-labelledby={titleId}>
      <RailScroller>
        <div className={styles.head}>
          <span className={styles.no} aria-hidden="true">
            {rail.no}
          </span>
          <div className={styles.text}>
            <h2 id={titleId} className={styles.title}>
              {rail.title}
            </h2>
            <p className={styles.sub}>
              {rail.source}
              <span className={styles.sep}> · </span>
              <span className={styles.tail}>새로 올라온 순</span>
            </p>
          </div>
          <div className={styles.tools}>
            <RailArrows name={rail.name} />
            <FreshListLink href={listHref({ board, category: "all", sort: "recent" })} className={styles.more}>
              전체 보기 →
            </FreshListLink>
          </div>
        </div>
        <Suspense
          fallback={
            <RailTrack board={board} placeholder>
              {Array.from({ length: PLACEHOLDER_COUNT }, (_, i) => (
                <RailCardPlaceholder key={i} board={board} />
              ))}
            </RailTrack>
          }
        >
          <RecentPosts board={board} />
        </Suspense>
      </RailScroller>
    </section>
  );
}

async function RecentPosts({ board }: { board: BoardFilter }) {
  let first: PostBatch;
  try {
    first = await getPosts(board, "all", "recent");
  } catch (err) {
    unstable_rethrow(err);
    // 한 게시판이 실패해도 메인의 다른 줄은 그대로 둔다
    console.error(err);
    return <p className={styles.message}>글을 불러오지 못했어요</p>;
  }
  const posts = first.items.slice(0, RECENT_COUNT);
  if (posts.length === 0) return <p className={styles.message}>아직 수집된 글이 없어요</p>;

  // 공동구매 칩의 오늘. 목록을 받은 뒤 서버에서 구해 문자열로 넘긴다(브라우저에서 날짜를 읽지 않는다)
  const today = kstDateKey(new Date());
  return (
    <RailTrack board={board}>
      {posts.map((post) => (
        <RailCard key={post.id} post={post} board={board} today={today} />
      ))}
    </RailTrack>
  );
}

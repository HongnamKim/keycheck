import { unstable_rethrow } from "next/navigation";
import { Suspense } from "react";
import { getPosts, type BoardFilter, type PostBatch } from "@/lib/api";
import { BOARD_INFO } from "@/lib/format";
import { listHref, parseListQuery } from "@/lib/query";
import FilterBar from "./FilterBar";
import LoadFailed from "./LoadFailed";
import PostFeed from "./PostFeed";
import styles from "./BoardListPage.module.css";

type SearchParams = PageProps<"/ic">["searchParams"];

/** 게시판 목록 화면(/ic, /gb). 제목·설명은 바로 그리고, 필터와 목록은 주소의 쿼리를 읽은 뒤 그린다 */
export default function BoardListPage({ board, searchParams }: { board: BoardFilter; searchParams: SearchParams }) {
  const info = BOARD_INFO[board];

  return (
    <div className={styles.page}>
      <div className={styles.head}>
        <h1 className={styles.title}>{info.title}</h1>
        <p className={styles.desc}>{info.desc}</p>
      </div>
      <Suspense fallback={<p className={styles.message}>글을 불러오는 중이에요</p>}>
        <PostList board={board} searchParams={searchParams} />
      </Suspense>
    </div>
  );
}

async function PostList({ board, searchParams }: { board: BoardFilter; searchParams: SearchParams }) {
  const query = parseListQuery(board, await searchParams);
  let first: PostBatch;
  try {
    first = await getPosts(query.board, query.category, query.sort, null, query.sub);
  } catch (err) {
    unstable_rethrow(err);
    // 백엔드가 멈췄거나 느리면(시간 제한) 제목·설명은 두고 목록 자리에 안내를 띄운다
    console.error(err);
    return (
      <LoadFailed>
        {/* 같은 목록 주소를 새로 열어 다시 불러온다 */}
        <a href={listHref(query)} className="outline-button">
          다시 시도
        </a>
      </LoadFailed>
    );
  }
  const renderedAt = new Date().toISOString();

  return (
    <div className={styles.feed}>
      <FilterBar query={query} />
      {/* 필터·정렬이 바뀌면(다른 주소) 목록을 새로 시작한다 */}
      <PostFeed key={listHref(query)} query={query} initial={first} renderedAt={renderedAt} />
    </div>
  );
}

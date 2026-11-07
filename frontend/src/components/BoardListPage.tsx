import { connection } from "next/server";
import { Suspense } from "react";
import type { BoardFilter } from "@/lib/api";
import { getFirstBatch } from "@/lib/cachedApi";
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
  const first = await getFirstBatch(query.board, query.category, query.sort, query.sub ?? null);
  if (!first) {
    // 오류 안내는 요청 때만 그려 정적 셸·재생성 결과·브라우저 캐시에 남지 않게 한다
    await connection();
    // 백엔드가 멈췄거나 느리면(시간 제한) 제목·설명은 두고 목록 자리에 안내를 띄운다
    return (
      <LoadFailed>
        {/* 같은 목록 주소를 새로 열어 다시 불러온다 */}
        <a href={listHref(query)} className="outline-button">
          다시 시도
        </a>
      </LoadFailed>
    );
  }
  return (
    <div className={styles.feed}>
      <FilterBar query={query} />
      {/* 필터·정렬이 바뀌면(다른 주소) 목록을 새로 시작한다 */}
      <PostFeed key={listHref(query)} query={query} initial={first.batch} renderedAt={first.fetchedAt} />
    </div>
  );
}

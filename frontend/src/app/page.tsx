import { Suspense } from "react";
import FilterBar from "@/components/FilterBar";
import PostFeed from "@/components/PostFeed";
import { getPosts } from "@/lib/api";
import { listHref, parseListQuery } from "@/lib/query";
import styles from "./page.module.css";

export default function Home({ searchParams }: PageProps<"/">) {
  return (
    <Suspense fallback={<p className={styles.message}>글을 불러오는 중이에요</p>}>
      <PostList searchParams={searchParams} />
    </Suspense>
  );
}

async function PostList({ searchParams }: Pick<PageProps<"/">, "searchParams">) {
  const query = parseListQuery(await searchParams);
  const first = await getPosts(query.category, query.sort, null, query.sub);
  const renderedAt = new Date().toISOString();

  return (
    <div className={styles.feed}>
      <FilterBar query={query} />
      {/* 필터·정렬이 바뀌면(다른 주소) 목록을 새로 시작한다 */}
      <PostFeed key={listHref(query)} query={query} initial={first} renderedAt={renderedAt} />
    </div>
  );
}

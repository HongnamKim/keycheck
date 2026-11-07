import { unstable_rethrow } from "next/navigation";
import { getNeighbors, type Board, type PostNeighbors } from "@/lib/api";
import { listTitle } from "@/lib/format";
import { initialPage, NEIGHBOR_PAGE_SIZE } from "@/lib/neighbors";
import { listHref, neighborsQuery, type ListQuery } from "@/lib/query";
import NeighborPager, { type RowPost } from "./NeighborPager";

/**
 * 처음 페이지에서 위아래 각각 보여줄 글 수(위 5개 + 지금 글 + 아래 5개 = 11줄). 목록 맨 위·맨 아래 근처라 한쪽이 모자라면
 * 다른 쪽을 더 보여 줘 줄 수를 맞추므로, 위아래를 NEIGHBOR_PAGE_SIZE(10)개씩 받는다.
 */
const NEIGHBOR_SIZE = 5;

/**
 * 상세 하단의 주변 글 목록. 보던 목록(from)에서 지금 글 위아래 글을 한 줄씩 보여주고,
 * 이전·다음 페이지로 그 목록을 계속 넘겨 볼 수 있다(NeighborPager).
 * from이 null이면(메인에서 열면) 그 글 게시판의 기본 목록으로 묻되, 글 링크에는 쿼리를 싣지 않아 계속 "메인으로" 돌아간다.
 * "목록 전체 보기"는 제목에 적힌 그 목록으로 간다.
 * 불러오지 못했거나 위아래가 모두 비면 아무것도 그리지 않는다.
 */
export default async function NeighborList({
  post,
  board,
  from,
}: {
  post: RowPost;
  board: Board;
  from: ListQuery | null;
}) {
  const query = neighborsQuery(from, board);
  let neighbors: PostNeighbors;
  try {
    neighbors = await getNeighbors(post.id, query, NEIGHBOR_PAGE_SIZE);
  } catch (err) {
    unstable_rethrow(err);
    // 주변 글은 덧붙이는 정보라 실패해도 상세는 그대로 둔다
    console.error(err);
    return null;
  }
  if (neighbors.before.length === 0 && neighbors.after.length === 0) return null;

  // 상세 응답 전체가 아니라 한 줄에 쓰는 필드만 브라우저로 넘긴다
  const current: RowPost = {
    id: post.id,
    title: post.title,
    category: post.category,
    subcategory: post.subcategory,
    representativeImageUrl: post.representativeImageUrl,
    representativeThumbUrl: post.representativeThumbUrl,
    detailLoaded: post.detailLoaded,
    postedAt: post.postedAt,
    estimatedPostedAt: post.estimatedPostedAt,
    views: post.views,
    replies: post.replies,
  };

  return (
    <NeighborPager
      // 다른 글로 옮겨 가면 그 글을 가운데 둔 처음 페이지부터 다시 시작한다
      key={post.id}
      title={listTitle(query)}
      allHref={listHref(query)}
      query={query}
      from={from}
      currentId={post.id}
      initial={initialPage<RowPost>(neighbors.before, current, neighbors.after, NEIGHBOR_PAGE_SIZE, NEIGHBOR_SIZE)}
    />
  );
}

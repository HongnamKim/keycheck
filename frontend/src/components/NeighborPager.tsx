"use client";

import Link from "next/link";
import { useRef, useState } from "react";
import { getNeighbors, type PostCard } from "@/lib/api";
import { formatPostedDate, formatViews, postCategoryLabel } from "@/lib/format";
import { imageSources } from "@/lib/image";
import { NEIGHBOR_PAGE_SIZE, nextPage, prevPage, type NeighborPage } from "@/lib/neighbors";
import { postHref, type ListQuery } from "@/lib/query";
import styles from "./NeighborList.module.css";
import { PostedDate } from "./PostDates";
import RemoteImage from "./RemoteImage";

/** 한 줄에 쓰는 글 정보(대표 이미지 포함). 지금 글(상세)과 주변 글(목록 카드) 모두 이 필드를 가진다 */
export type RowPost = Pick<
  PostCard,
  | "id"
  | "title"
  | "category"
  | "subcategory"
  | "representativeImageUrl"
  | "representativeThumbUrl"
  | "detailLoaded"
  | "postedAt"
  | "estimatedPostedAt"
  | "views"
  | "replies"
>;

type Direction = "prev" | "next";

/**
 * 주변 글 목록과 이전·다음 페이지 버튼. 앞뒤 페이지는 지금 페이지 첫 글·마지막 글의 주변 글을 브라우저에서 불러 바꾼다.
 * 주변 글 API는 과거 글을 확보하지 않으므로 확보한 범위 끝에서 "다음"이 꺼진다. 그 뒤는 "목록 전체 보기"에서 이어진다.
 */
export default function NeighborPager({
  title,
  allHref,
  query,
  from,
  currentId,
  initial,
}: {
  title: string;
  allHref: string;
  query: ListQuery;
  from: ListQuery | null;
  currentId: number;
  initial: NeighborPage<RowPost>;
}) {
  const [page, setPage] = useState(initial);
  const [loading, setLoading] = useState<Direction | null>(null);
  const [failed, setFailed] = useState(false);
  const sectionRef = useRef<HTMLElement>(null);

  async function move(direction: Direction) {
    const anchor = direction === "next" ? page.items.at(-1) : page.items[0];
    if (!anchor || loading) return;
    setLoading(direction);
    setFailed(false);
    try {
      const neighbors = await getNeighbors(anchor.id, query, NEIGHBOR_PAGE_SIZE);
      const moved =
        direction === "next"
          ? nextPage(page, neighbors.after, NEIGHBOR_PAGE_SIZE)
          : prevPage(page, neighbors.before, NEIGHBOR_PAGE_SIZE);
      setPage(moved);
      // 페이지 아래쪽 버튼을 눌렀으니, 목록 머리가 화면 위로 벗어났으면 머리부터 보이게 올린다
      if (moved.items !== page.items) {
        const section = sectionRef.current;
        if (section && section.getBoundingClientRect().top < 0) section.scrollIntoView({ block: "start" });
      }
    } catch (err) {
      console.error(err);
      setFailed(true);
    } finally {
      setLoading(null);
    }
  }

  return (
    <section ref={sectionRef} className={styles.neighbors} aria-labelledby="neighbors-title">
      <div className={styles.head}>
        <h3 id="neighbors-title" className={styles.title}>
          {title}
        </h3>
        <Link href={allHref} className={styles.all}>
          목록 전체 보기 →
        </Link>
      </div>
      <ul className={styles.list} aria-busy={loading !== null}>
        {page.items.map((item) => (
          <li key={item.id}>
            {item.id === currentId ? (
              <div className={`${styles.row} ${styles.current}`} aria-current="page">
                <RowContent post={item} />
              </div>
            ) : (
              <Link href={postHref(item.id, from)} className={styles.row}>
                <RowContent post={item} />
              </Link>
            )}
          </li>
        ))}
      </ul>
      {(page.hasPrev || page.hasNext) && (
        <nav className={styles.pager} aria-label="주변 글 페이지">
          <button
            type="button"
            className={styles.pageButton}
            disabled={!page.hasPrev || loading !== null}
            onClick={() => move("prev")}
          >
            {loading === "prev" ? "불러오는 중…" : "‹ 이전"}
          </button>
          <button
            type="button"
            className={styles.pageButton}
            disabled={!page.hasNext || loading !== null}
            onClick={() => move("next")}
          >
            {loading === "next" ? "불러오는 중…" : "다음 ›"}
          </button>
        </nav>
      )}
      {failed && (
        <p className={styles.error} role="alert">
          글을 불러오지 못했어요. 잠시 후 다시 눌러 주세요.
        </p>
      )}
    </section>
  );
}

function RowContent({ post }: { post: RowPost }) {
  const posted = formatPostedDate(post.postedAt, post.estimatedPostedAt);
  const image = post.representativeImageUrl
    ? imageSources(post.representativeThumbUrl, post.representativeImageUrl)
    : null;

  return (
    <>
      {image ? (
        <span className={styles.thumb}>
          <RemoteImage
            src={image.src}
            fallbackSrc={image.fallback}
            alt=""
            fill
            sizes="(max-width: 640px) 128px, 208px"
            className={styles.image}
          />
        </span>
      ) : (
        <span className={`${styles.thumb} ${styles.noImage}`}>
          {/* 틀 글은 상세를 열어야 백엔드가 원문을 읽어 이미지를 채운다 */}
          {post.detailLoaded ? "이미지 없음" : "열면 불러와요"}
        </span>
      )}
      <span className={styles.body}>
        <span className={styles.category}>{postCategoryLabel(post.category, post.subcategory)}</span>
        <span className={styles.name}>{post.title}</span>
        <span className={styles.meta}>
          {posted && <PostedDate posted={posted} className={styles.piece} />}
          {posted && " · "}
          <span className={styles.piece}>조회 {formatViews(post.views)}</span>
          {" · "}
          <span className={styles.piece}>댓글 {post.replies}</span>
        </span>
      </span>
    </>
  );
}

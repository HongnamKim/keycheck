"use client";

import { useEffect, useEffectEvent, useRef, useState, type MouseEvent } from "react";
import { getPosts, type PostBatch } from "@/lib/api";
import {
  applyBatch,
  fromSnapshot,
  otherSnapshotKeys,
  readSnapshot,
  snapshotKey,
  startFeed,
  type FeedSnapshot,
  type FeedState,
} from "@/lib/feed";
import { listHref, postHref, type ListQuery } from "@/lib/query";
import { ALL_SUB_LABELS } from "./FilterBar";
import PostCard from "./PostCard";
import styles from "./PostFeed.module.css";

/** 넓은 화면 첫 줄(4장)은 바로 보이므로 지연 로딩하지 않는다 */
const EAGER_CARDS = 4;
/** 끝에 닿기 전에 미리 다음 묶음을 받기 시작하는 여유 */
const PRELOAD_MARGIN = "800px 0px";
/** 0건을 받았을 때 같은 cursor로 다시 요청하기까지 기다리는 시간 */
const RETRY_DELAY_MS = 2_500;

interface Props {
  query: ListQuery;
  /** 서버가 cursor 없이 받은 첫 묶음 */
  initial: PostBatch;
  /** 서버가 그린 시각. 카드의 상대 시간을 서버와 같게 맞춰 하이드레이션 차이를 막는다. */
  renderedAt: string;
}

/**
 * 무한 스크롤 목록. 끝의 감시 요소가 보이면 다음 묶음을 브라우저에서 받아 붙인다.
 * 카드를 누를 때 보던 목록과 스크롤 위치를 목록 주소별로 sessionStorage에 남기고, 상세에서 돌아오면 한 번 이어서 보여준다.
 * 필터·정렬을 눌러 들어온 목록은 저장본이 없어 처음부터 시작한다(목록이 보일 때 다른 목록의 저장본은 버린다).
 * 헤더 메뉴·메인의 전체 보기로 들어온 목록도 처음부터다(FreshListLink가 누를 때 저장본을 모두 지운다).
 */
export default function PostFeed({ query, initial, renderedAt }: Props) {
  const [feed, setFeed] = useState(() => startFeed(initial));
  /** 저장본을 쓸지 정하기 전에는 다음 묶음을 받지 않는다. 그 사이 받은 응답이 되돌린 목록을 덮지 않게 한다 */
  const [restoreChecked, setRestoreChecked] = useState(false);
  const sentinelRef = useRef<HTMLDivElement>(null);
  const mountedRef = useRef(false);
  /** 저장본으로 바꾼 목록과, 그것이 그려진 뒤 옮겨 갈 스크롤 위치 */
  const pendingRef = useRef<{ feed: FeedState; scrollY: number } | null>(null);
  const key = snapshotKey(listHref(query));
  const now = new Date(renderedAt);

  function load(cursor: string | null) {
    setFeed((f) => ({ ...f, status: "loading" }));
    getPosts(query.board, query.category, query.sort, cursor, query.sub)
      .then((batch) => setFeed((f) => applyBatch(f, batch)))
      .catch(() => setFeed((f) => ({ ...f, status: "failed" })));
  }

  function save(e: MouseEvent<HTMLDivElement>) {
    // 같은 탭에서 상세로 넘어가는 클릭만 남긴다. 새 탭으로 여는 클릭이나 카드 사이 빈 곳 클릭은 목록을 떠나지 않는다
    if (e.button !== 0 || e.metaKey || e.ctrlKey || e.shiftKey || e.altKey) return;
    if (!(e.target instanceof Element) || !e.target.closest("a[href]")) return;
    const { items, nextCursor, hasMore } = feed;
    const snapshot: FeedSnapshot = { items, nextCursor, hasMore, scrollY: window.scrollY, savedAt: Date.now() };
    try {
      sessionStorage.setItem(key, JSON.stringify(snapshot));
    } catch {
      // 저장하지 못하면 돌아왔을 때 처음부터 보여준다
    }
  }

  // 효과 안에서 부를 때 늘 지금의 cursor를 쓴다
  const loadNext = useEffectEvent(() => load(feed.nextCursor));

  const forgetSnapshot = useEffectEvent(() => {
    try {
      sessionStorage.removeItem(key);
    } catch {
      // 지우지 못하면 30분 뒤 저절로 버려진다
    }
  });

  // 저장본으로 바꾼 목록이 화면에 그려진 뒤에 스크롤해야 페이지 높이가 모자라 위치가 잘리지 않는다
  useEffect(() => {
    const pending = pendingRef.current;
    if (!pending || pending.feed !== feed) return;
    pendingRef.current = null;
    window.scrollTo(0, pending.scrollY);
    forgetSnapshot();
  }, [feed]);

  // 상세에서 돌아왔으면 저장본을 한 번 쓰고 지운다(위치를 되돌린 뒤 지워, 개발 모드의 효과 재실행에도 안전하다).
  // 처음 그릴 때는 목록을 저장본으로 바꾸고, 숨겨 두었던 목록이 다시 보일 때는 목록이 남아 있어 위치만 되돌린다.
  useEffect(() => {
    const firstMount = !mountedRef.current;
    mountedRef.current = true;
    // 이 효과가 끝나면 저장본을 쓸지 정해진다. 바꾼 목록과 함께 그려진 뒤에 다음 묶음 감시를 시작한다
    if (firstMount) setRestoreChecked(true);
    let snapshot: FeedSnapshot | null = null;
    try {
      for (const k of otherSnapshotKeys(Object.keys(sessionStorage), key)) sessionStorage.removeItem(k);
      snapshot = readSnapshot(sessionStorage.getItem(key), Date.now());
    } catch {
      // 읽지 못하면 서버가 준 첫 묶음 그대로
    }
    if (!snapshot || pendingRef.current) return;
    if (firstMount) {
      // sessionStorage는 하이드레이션 뒤에만 읽을 수 있어 효과 안에서 바꾼다
      const restored = fromSnapshot(snapshot);
      pendingRef.current = { feed: restored, scrollY: snapshot.scrollY };
      setFeed(restored);
      return;
    }
    const { scrollY } = snapshot;
    const frame = requestAnimationFrame(() => {
      window.scrollTo(0, scrollY);
      forgetSnapshot();
    });
    return () => cancelAnimationFrame(frame);
  }, [key]);

  // 감시 요소가 여유 범위 안에 들어오면 다음 묶음을 받는다.
  // 요청마다 loading을 거쳐 idle로 돌아오므로, 받은 뒤에도 아직 보이면 새 관찰이 곧바로 다음 묶음을 받는다.
  useEffect(() => {
    const sentinel = sentinelRef.current;
    if (!restoreChecked || feed.status !== "idle" || !sentinel) return;
    const observer = new IntersectionObserver(
      (entries) => {
        if (!entries.some((e) => e.isIntersecting)) return;
        observer.disconnect();
        loadNext();
      },
      { rootMargin: PRELOAD_MARGIN },
    );
    observer.observe(sentinel);
    return () => observer.disconnect();
  }, [feed.status, restoreChecked]);

  // geekhack에서 더 가져오는 중이면 잠시 뒤 같은 cursor로 다시 요청한다
  useEffect(() => {
    if (feed.status !== "waiting") return;
    const timer = setTimeout(loadNext, RETRY_DELAY_MS);
    return () => clearTimeout(timer);
  }, [feed.status]);

  function retry() {
    setFeed((f) => ({ ...f, emptyStreak: 0 }));
    load(feed.nextCursor);
  }

  return (
    <>
      {feed.items.length > 0 && (
        // 카드를 누르는 순간의 목록과 위치를 남긴다
        <div className={styles.grid} onClickCapture={save}>
          {feed.items.map((post, i) => (
            <PostCard key={post.id} post={post} href={postHref(post.id, query)} now={now} eager={i < EAGER_CARDS} />
          ))}
        </div>
      )}

      <div ref={sentinelRef} className={styles.status} aria-live="polite">
        {feed.status === "loading" && <p>글을 더 불러오는 중이에요</p>}
        {feed.status === "waiting" && <p>geekhack에서 더 가져오는 중이에요</p>}
        {feed.status === "failed" && (
          <>
            <p>더 불러오지 못했어요</p>
            <button type="button" className="outline-button" onClick={retry}>
              다시 시도
            </button>
          </>
        )}
        {feed.status === "end" && <p>{endMessage(query, feed.items.length > 0)}</p>}
      </div>
    </>
  );
}

/**
 * 분류 필터 목록은 과거 글을 더 받지 않는다(받은 틀 글은 미분류). 그래서 끝 안내가 다르다.
 * 세부 분류가 정해지지 않은 글은 세부 분류 목록에 없고, 같은 분류의 전체 배열·전체 제조사 목록에 있다.
 */
function endMessage(query: ListQuery, hasItems: boolean): string {
  if (query.category === "all") return hasItems ? "마지막 글이에요" : "아직 수집된 글이 없어요";
  if (query.sub) {
    const hint = `세부 분류가 정해지지 않은 글은 ${ALL_SUB_LABELS[query.category]}에서 볼 수 있어요`;
    return hasItems ? `이 세부 분류의 글은 여기까지예요. ${hint}` : `아직 이 세부 분류의 글이 없어요. ${hint}`;
  }
  return hasItems
    ? "분류된 글은 여기까지예요. 미분류 글은 전체에서 볼 수 있어요"
    : "아직 분류된 글이 없어요. 미분류 글은 전체에서 볼 수 있어요";
}

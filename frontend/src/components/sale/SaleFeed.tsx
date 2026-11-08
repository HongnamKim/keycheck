"use client";

import { useEffect, useEffectEvent, useRef, useState, type MouseEvent } from "react";
import { getSales, type SaleBatch, type SaleCard as SaleCardData } from "@/lib/api";
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
import { formatKstTime, kstDateKey } from "@/lib/format";
import { saleHref, saleListHref, type SaleListQuery } from "@/lib/saleQuery";
import { vendorLabel } from "@/lib/saleStatus";
import { dayLabel, groupByNotice } from "@/lib/saleTimeline";
import SaleRow from "./SaleRow";
import styles from "./SaleFeed.module.css";
import timeline from "./SaleTimeline.module.css";

/** 끝에 닿기 전에 미리 다음 묶음을 받기 시작하는 여유 */
const PRELOAD_MARGIN = "800px 0px";
/** 0건을 받았을 때 같은 cursor로 다시 요청하기까지 기다리는 시간 */
const RETRY_DELAY_MS = 2_500;

interface Props {
  query: SaleListQuery;
  /** 서버가 cursor 없이 받은 첫 묶음 */
  initial: SaleBatch;
  /** 서버가 받은 시각. 배지·판매 시각·날짜 머리(오늘, 어제)를 서버와 같게 맞춰 하이드레이션 차이를 막는다. */
  renderedAt: string;
  /**
   * 상세 아래 이어서 보기에서 지금 보는 판매 건. 주면 그 줄을 표시하고, 목록 저장본은 남기지도 읽지도 않는다
   * (목록의 저장본을 덮거나 지우지 않게. 뒤로 가기로 돌아오면 숨겨 두었던 화면이 그대로 보인다).
   */
  currentId?: number;
  /** 줄 링크(상세 주소)에 실을 보던 목록. 기본은 query. 메인에서 연 상세의 이어서 보기는 null이라 줄 링크도 계속 "메인으로" 돌아간다 */
  linkFrom?: SaleListQuery | null;
}

/**
 * 공지 순 보기(설계 3.7 A). 받은 판매 건을 날짜 머리 → 공지 묶음 → 판매 건 줄로 그린다.
 * 무한 스크롤 목록. 끝의 감시 요소가 보이면 다음 묶음을 브라우저에서 받아 붙인다(페이지 경계의 같은 공지는 한 묶음으로 이어진다).
 * 줄을 누를 때 보던 목록과 스크롤 위치를 목록 주소별로 sessionStorage에 남기고, 상세에서 돌아오면 한 번 이어서 보여준다.
 * 필터·정렬을 눌러 들어온 목록은 저장본이 없어 처음부터 시작한다(목록이 보일 때 다른 목록의 저장본은 버린다).
 * 헤더 메뉴·메인의 전체 보기로 들어온 목록도 처음부터다(FreshListLink가 누를 때 저장본을 모두 지운다).
 * PostFeed와 같은 방식이고, 목록 주소(saleListHref)가 저장본 키다.
 * 상세 아래 이어서 보기(currentId)도 이 목록을 쓴다. 서버가 그 판매 건의 공지부터 받은 묶음으로 시작하고 저장본은 쓰지 않는다.
 */
export default function SaleFeed({ query, initial, renderedAt, currentId, linkFrom = query }: Props) {
  const [feed, setFeed] = useState(() => startFeed<SaleCardData>(initial));
  /** 저장본 키. 상세 아래 이어서 보기는 null(저장본을 쓰지 않는다) */
  const key = currentId === undefined ? snapshotKey(saleListHref(query)) : null;
  /** 저장본을 쓸지 정하기 전에는 다음 묶음을 받지 않는다. 그 사이 받은 응답이 되돌린 목록을 덮지 않게 한다 */
  const [restoreChecked, setRestoreChecked] = useState(key === null);
  const sentinelRef = useRef<HTMLDivElement>(null);
  const mountedRef = useRef(false);
  /** 저장본으로 바꾼 목록과, 그것이 그려진 뒤 옮겨 갈 스크롤 위치 */
  const pendingRef = useRef<{ feed: FeedState<SaleCardData>; scrollY: number } | null>(null);
  const now = new Date(renderedAt);
  const today = kstDateKey(now);

  function load(cursor: string | null) {
    setFeed((f) => ({ ...f, status: "loading" }));
    getSales(query.vendor, query.category, cursor)
      .then((batch) => setFeed((f) => applyBatch(f, batch)))
      .catch(() => setFeed((f) => ({ ...f, status: "failed" })));
  }

  function save(e: MouseEvent<HTMLDivElement>) {
    if (key === null) return;
    // 같은 탭에서 상세로 넘어가는 클릭만 남긴다. 새 탭으로 여는 클릭이나 줄 사이 빈 곳 클릭은 목록을 떠나지 않는다
    if (e.button !== 0 || e.metaKey || e.ctrlKey || e.shiftKey || e.altKey) return;
    if (!(e.target instanceof Element) || !e.target.closest("a[href]")) return;
    const { items, nextCursor, hasMore } = feed;
    const snapshot: FeedSnapshot<SaleCardData> = { items, nextCursor, hasMore, scrollY: window.scrollY, savedAt: Date.now() };
    try {
      sessionStorage.setItem(key, JSON.stringify(snapshot));
    } catch {
      // 저장하지 못하면 돌아왔을 때 처음부터 보여준다
    }
  }

  // 효과 안에서 부를 때 늘 지금의 cursor를 쓴다
  const loadNext = useEffectEvent(() => load(feed.nextCursor));

  const forgetSnapshot = useEffectEvent(() => {
    if (key === null) return;
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
    if (key === null) return;
    const firstMount = !mountedRef.current;
    mountedRef.current = true;
    // 이 효과가 끝나면 저장본을 쓸지 정해진다. 바꾼 목록과 함께 그려진 뒤에 다음 묶음 감시를 시작한다
    if (firstMount) setRestoreChecked(true);
    let snapshot: FeedSnapshot<SaleCardData> | null = null;
    try {
      for (const k of otherSnapshotKeys(Object.keys(sessionStorage), key)) sessionStorage.removeItem(k);
      snapshot = readSnapshot<SaleCardData>(sessionStorage.getItem(key), Date.now());
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
        // 줄을 누르는 순간의 목록과 위치를 남긴다
        <div className={timeline.timeline} onClickCapture={save}>
          {groupByNotice(feed.items).map((day) => (
            <section key={day.day}>
              <h2 className={timeline.head}>
                <span className={timeline.dot} aria-hidden="true" />
                {dayLabel(day.day, today)}
              </h2>
              {day.notices.map((notice) => (
                <div key={notice.items[0].id} className={timeline.block}>
                  <h3 className={timeline.noticeHead}>
                    {vendorLabel(notice.vendor)}
                    <span className={timeline.noticeMeta}>
                      {formatKstTime(notice.postedAt)} 공지 · 판매 {notice.items.length}건
                    </span>
                  </h3>
                  <ul className={timeline.rows}>
                    {notice.items.map((sale) => (
                      <li key={sale.id}>
                        <SaleRow sale={sale} href={saleHref(sale.id, linkFrom)} now={now} current={sale.id === currentId} />
                      </li>
                    ))}
                  </ul>
                </div>
              ))}
            </section>
          ))}
        </div>
      )}

      <div ref={sentinelRef} className={styles.status} aria-live="polite">
        {feed.status === "loading" && <p>판매 소식을 더 불러오는 중이에요</p>}
        {feed.status === "waiting" && <p>판매 소식을 더 가져오는 중이에요</p>}
        {feed.status === "failed" && (
          <>
            <p>더 불러오지 못했어요</p>
            <button type="button" className="outline-button" onClick={retry}>
              다시 시도
            </button>
          </>
        )}
        {feed.status === "end" && <p>{feed.items.length > 0 ? "마지막 판매 소식이에요" : "아직 모은 판매 소식이 없어요"}</p>}
      </div>
    </>
  );
}

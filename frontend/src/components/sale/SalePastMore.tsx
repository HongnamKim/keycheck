"use client";

import { useState } from "react";
import { getSalePast, type SaleCard } from "@/lib/api";
import { saleHref, type SaleListQuery } from "@/lib/saleQuery";
import SaleRow from "./SaleRow";
import feed from "./SaleFeed.module.css";
import styles from "./SaleTimeline.module.css";

/**
 * 판매 일정 순 보기의 지난 판매 "더 보기"(설계 3.7 B). 서버가 그린 첫 쪽 뒤에 다음 쪽을 눌러서 받아 붙인다.
 * 배지·판매 시각은 서버가 첫 쪽을 받은 시각(fetchedAt)으로 계산해 브라우저 시계를 읽지 않는다.
 */
export default function SalePastMore({
  query,
  nextCursor,
  hasMore,
  fetchedAt,
}: {
  query: SaleListQuery;
  nextCursor: string | null;
  hasMore: boolean;
  fetchedAt: string;
}) {
  const [items, setItems] = useState<SaleCard[]>([]);
  const [cursor, setCursor] = useState(nextCursor);
  const [more, setMore] = useState(hasMore);
  const [status, setStatus] = useState<"idle" | "loading" | "failed">("idle");
  const now = new Date(fetchedAt);

  function load() {
    setStatus("loading");
    getSalePast(query.vendor, query.category, cursor)
      .then((batch) => {
        setItems((prev) => [...prev, ...batch.items]);
        setCursor(batch.nextCursor);
        setMore(batch.hasMore);
        setStatus("idle");
      })
      .catch(() => setStatus("failed"));
  }

  return (
    <>
      {items.length > 0 && (
        <ul className={`${styles.rows} ${styles.moreRows}`}>
          {items.map((sale) => (
            <li key={sale.id}>
              <SaleRow sale={sale} href={saleHref(sale.id, query)} now={now} showVendor />
            </li>
          ))}
        </ul>
      )}
      {(more || status === "failed") && (
        <div className={feed.status} aria-live="polite">
          {status === "loading" && <p>글을 더 불러오는 중이에요</p>}
          {status === "failed" && <p>더 불러오지 못했어요</p>}
          {status !== "loading" && (
            <button type="button" className="outline-button" onClick={load}>
              {status === "failed" ? "다시 시도" : "지난 판매 더 보기"}
            </button>
          )}
        </div>
      )}
    </>
  );
}

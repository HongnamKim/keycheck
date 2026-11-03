"use client";

import { useEffect, useState } from "react";
import { getStats, type PostStats } from "@/lib/api";
import { formatCheckedAt, formatViews } from "@/lib/format";

interface Props {
  postId: number;
  initial: PostStats;
  /** 서버가 그린 시각. 첫 렌더의 상대 시간을 서버와 같게 맞춰 하이드레이션 차이를 막는다. */
  renderedAt: string;
  className?: string;
}

/** 저장된 조회수·댓글 수를 먼저 보여주고, 마운트 후 /stats 값으로 바꾼다. 실패하면 저장값을 그대로 둔다. */
export default function StatsLive({ postId, initial, renderedAt, className }: Props) {
  const [stats, setStats] = useState(initial);
  const [now, setNow] = useState(renderedAt);

  useEffect(() => {
    const controller = new AbortController();
    getStats(postId, controller.signal)
      .then((fresh) => {
        setStats(fresh);
        setNow(new Date().toISOString());
      })
      .catch(() => {
        // 저장값 유지
      });
    return () => controller.abort();
  }, [postId]);

  return (
    <p className={className}>
      조회 {formatViews(stats.views)} · 댓글 {stats.replies} · {formatCheckedAt(new Date(stats.statsCheckedAt), new Date(now))}
    </p>
  );
}

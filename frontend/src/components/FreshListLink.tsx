"use client";

import Link from "next/link";
import type { ComponentProps, MouseEvent } from "react";
import { snapshotKeys } from "@/lib/feed";

/**
 * 목록에 새로 들어가는 링크(헤더 메뉴, 메인의 전체 보기). 누르면 목록 저장본을 모두 지워 처음부터 보여준다.
 * 저장본은 상세에서 "목록으로"나 뒤로 가기로 돌아올 때만 쓴다.
 */
export default function FreshListLink(props: Omit<ComponentProps<typeof Link>, "onClick">) {
  function forgetSnapshots(e: MouseEvent<HTMLAnchorElement>) {
    // 새 탭으로 여는 클릭은 이 탭을 떠나지 않으므로 저장본을 남긴다
    if (e.button !== 0 || e.metaKey || e.ctrlKey || e.shiftKey || e.altKey) return;
    try {
      for (const k of snapshotKeys(Object.keys(sessionStorage))) sessionStorage.removeItem(k);
    } catch {
      // 지우지 못하면 30분 뒤 저절로 버려진다
    }
  }

  return <Link {...props} onClick={forgetSnapshots} />;
}

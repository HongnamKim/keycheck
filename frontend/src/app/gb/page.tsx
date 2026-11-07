import type { Metadata } from "next";
import BoardListPage from "@/components/BoardListPage";
import { BOARD_INFO } from "@/lib/format";

// 필터 주소(?category= 등)는 모두 게시판 목록 주소로 모은다
export const metadata: Metadata = {
  title: BOARD_INFO.gb.title,
  description: BOARD_INFO.gb.desc,
  alternates: { canonical: "/gb" },
};

export default function GbPage({ searchParams }: PageProps<"/gb">) {
  return <BoardListPage board="gb" searchParams={searchParams} />;
}

import type { Metadata } from "next";
import BoardListPage from "@/components/BoardListPage";
import { siteOpenGraph } from "@/lib/seo";

// 검색 결과용 제목·설명. 화면의 제목·설명(BOARD_INFO)은 승인된 시안 문구라 따로 둔다.
// 필터 주소(?category= 등)는 모두 게시판 목록 주소로 모은다
export const metadata: Metadata = {
  title: "키보드·키캡 공동구매 목록",
  description:
    "geekhack Group Buys and Preorders의 키보드·키캡 공동구매 글을 한국어 요약, 공동구매 기간, 한국 벤더와 함께 모아 봐요",
  alternates: { canonical: "/gb" },
  openGraph: siteOpenGraph("/gb"),
};

export default function GbPage({ searchParams }: PageProps<"/gb">) {
  return <BoardListPage board="gb" searchParams={searchParams} />;
}

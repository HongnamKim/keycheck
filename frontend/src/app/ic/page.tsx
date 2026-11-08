import type { Metadata } from "next";
import BoardListPage from "@/components/BoardListPage";
import { siteOpenGraph } from "@/lib/seo";

// 검색 결과용 제목·설명. 화면의 제목·설명(BOARD_INFO)은 승인된 시안 문구라 따로 둔다.
// 필터 주소(?category= 등)는 모두 게시판 목록 주소로 모은다
export const metadata: Metadata = {
  title: "키보드·키캡 IC(관심 조사) 목록",
  description: "geekhack Interest Checks에 올라온 키보드·키캡 관심 조사(IC) 글을 한국어 요약과 대표 이미지로 모아 봐요",
  alternates: { canonical: "/ic" },
  openGraph: siteOpenGraph("/ic"),
};

export default function IcPage({ searchParams }: PageProps<"/ic">) {
  return <BoardListPage board="ic" searchParams={searchParams} />;
}

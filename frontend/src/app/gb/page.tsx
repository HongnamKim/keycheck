import type { Metadata } from "next";
import BoardListPage from "@/components/BoardListPage";
import { BOARD_INFO } from "@/lib/format";

export const metadata: Metadata = { title: BOARD_INFO.gb.title, description: BOARD_INFO.gb.desc };

export default function GbPage({ searchParams }: PageProps<"/gb">) {
  return <BoardListPage board="gb" searchParams={searchParams} />;
}

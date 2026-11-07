import type { Metadata } from "next";
import BoardListPage from "@/components/BoardListPage";
import { BOARD_INFO } from "@/lib/format";

export const metadata: Metadata = { title: BOARD_INFO.ic.title, description: BOARD_INFO.ic.desc };

export default function IcPage({ searchParams }: PageProps<"/ic">) {
  return <BoardListPage board="ic" searchParams={searchParams} />;
}

import type { Metadata } from "next";
import SalesListPage from "@/components/sale/SalesListPage";
import { SALE_LIST_INFO } from "@/lib/format";
import { siteOpenGraph } from "@/lib/seo";

// 필터 주소(?vendor=&category=)는 모두 /sales로 모은다
export const metadata: Metadata = {
  title: SALE_LIST_INFO.title,
  description: SALE_LIST_INFO.desc,
  alternates: { canonical: "/sales" },
  openGraph: siteOpenGraph("/sales"),
};

export default function SalesPage({ searchParams }: PageProps<"/sales">) {
  return <SalesListPage searchParams={searchParams} />;
}

import Link from "next/link";
import type { SaleCard } from "@/lib/api";
import { saleBadge, saleCategoryLabel, saleKindLabel, saleWhenText, vendorLabel } from "@/lib/saleStatus";
import RemoteImage from "../RemoteImage";
import SaleBadge from "./SaleBadge";
import styles from "./SaleRow.module.css";

/**
 * 국내 판매 타임라인의 한 줄(설계 3.7). 줄 전체가 href(상세 주소)로 가는 링크다.
 * 사진이 있으면 왼쪽에 작은 썸네일, 없으면 썸네일 자리 없이 글만. now는 배지·판매 시각의 기준(서버가 목록을 받은 시각),
 * showVendor는 구분 앞에 판매처를 붙일지(판매 일정 순 보기).
 */
export default function SaleRow({
  sale,
  href,
  now,
  showVendor = false,
}: {
  sale: SaleCard;
  href: string;
  now: Date;
  showVendor?: boolean;
}) {
  const badge = saleBadge(sale, now);
  const kind = saleKindLabel(sale.saleType, sale.phase);

  return (
    <Link href={href} className={styles.row}>
      {sale.thumbUrl && (
        <span className={styles.thumb}>
          <RemoteImage src={sale.thumbUrl} fallbackSrc={sale.largeUrl} alt="" fill sizes="64px" className={styles.image} />
        </span>
      )}
      <span className={styles.body}>
        <span className={styles.meta}>
          {badge && <SaleBadge badge={badge} />}
          <span className={styles.category}>{saleCategoryLabel(sale.category)}</span>
          <span>{showVendor ? `${vendorLabel(sale.vendor)} · ${kind}` : kind}</span>
        </span>
        <span className={styles.name}>{sale.productName}</span>
        <span className={styles.when}>{saleWhenText(sale, now)}</span>
      </span>
    </Link>
  );
}

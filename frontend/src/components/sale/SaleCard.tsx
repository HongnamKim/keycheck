import Link from "next/link";
import type { SaleCard as SaleCardData } from "@/lib/api";
import { formatShortDate } from "@/lib/format";
import { saleBadge, saleKindLabel, saleWhenText, vendorLabel } from "@/lib/saleStatus";
import RemoteImage from "../RemoteImage";
import SaleBadge from "./SaleBadge";
import styles from "./SaleCard.module.css";

/**
 * 국내 판매 목록 카드. 카드 전체가 href(상세 주소)로 가는 링크다.
 * now는 상태 배지·판매 시각을 계산할 기준 시각(서버가 목록을 받은 시각), eager는 처음부터 보이는 카드라 지연 로딩하지 않을지 여부.
 */
export default function SaleCard({
  sale,
  href,
  now,
  eager = false,
}: {
  sale: SaleCardData;
  href: string;
  now: Date;
  eager?: boolean;
}) {
  const badge = saleBadge(sale, now);

  return (
    <Link href={href} className={styles.card}>
      {sale.thumbUrl ? (
        <span className={styles.media}>
          <RemoteImage
            src={sale.thumbUrl}
            fallbackSrc={sale.largeUrl}
            alt=""
            fill
            loading={eager ? "eager" : "lazy"}
            sizes="(max-width: 640px) 100vw, (max-width: 960px) 50vw, 320px"
            className={styles.image}
          />
        </span>
      ) : (
        <span className={styles.noImage}>이미지 없음</span>
      )}
      <div className={styles.body}>
        <span className={styles.category}>
          {badge && <SaleBadge badge={badge} />}
          <span>
            {vendorLabel(sale.vendor)} · {saleKindLabel(sale.saleType, sale.phase)}
          </span>
        </span>
        <h2 className={styles.title}>{sale.productName}</h2>
        {sale.summaryFirstLine && <span className={styles.summary}>{sale.summaryFirstLine}</span>}
        <span className={styles.when}>{saleWhenText(sale, now)}</span>
        <span className={styles.meta}>공지 {formatShortDate(sale.postedAt)}</span>
      </div>
    </Link>
  );
}

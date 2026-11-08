import Link from "next/link";
import type { SaleCard } from "@/lib/api";
import { saleHref } from "@/lib/saleQuery";
import { saleBadge, saleKindLabel, vendorLabel } from "@/lib/saleStatus";
import RemoteImage from "../RemoteImage";
import SaleBadge from "../sale/SaleBadge";
import styles from "./SaleRailCard.module.css";

/**
 * 메인 국내 판매 줄의 카드 한 장(li). 공동구매 카드와 같은 크기·사진 3:2, 사진 위 왼쪽에 상태 배지.
 * now는 서버가 목록을 받은 시각. 메인에서 연 상세는 "메인으로" 돌아온다(쿼리 없는 상세 주소).
 */
export default function SaleRailCard({ sale, now }: { sale: SaleCard; now: Date }) {
  const badge = saleBadge(sale, now);

  return (
    <li className={styles.card}>
      <Link href={saleHref(sale.id)} className={styles.link}>
        <span className={styles.media}>
          {sale.thumbUrl ? (
            <RemoteImage
              src={sale.thumbUrl}
              fallbackSrc={sale.largeUrl}
              alt=""
              fill
              sizes="(max-width: 599px) 100vw, (max-width: 1023px) 50vw, 320px"
              className={styles.img}
            />
          ) : (
            <span className={styles.noImage}>이미지 없음</span>
          )}
          {badge && (
            <span className={styles.chip}>
              <SaleBadge badge={badge} />
            </span>
          )}
        </span>
        <span className={styles.title}>{sale.productName}</span>
        {sale.summaryFirstLine && <span className={styles.sum}>{sale.summaryFirstLine}</span>}
        <span className={styles.meta}>
          {vendorLabel(sale.vendor)} · {saleKindLabel(sale.saleType, sale.phase)}
        </span>
      </Link>
    </li>
  );
}

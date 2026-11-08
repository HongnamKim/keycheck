import Link from "next/link";
import type { SalePick } from "@/lib/api";
import { SALE_PICK_KICKER, wideSaleImage } from "@/lib/homePick";
import { saleHref } from "@/lib/saleQuery";
import { saleBadge, saleKindLabel, saleWhenText, vendorLabel } from "@/lib/saleStatus";
import RemoteImage from "../RemoteImage";
import SaleBadge from "../sale/SaleBadge";
import styles from "./SaleBand.module.css";

/**
 * 하이라이트 아래 국내 판매 띠(시안 c, 띠 A 밝은 카드). 왼쪽 3:2 사진, 오른쪽 키커·이름·배지와
 * 판매처 · 구분 · 판매 시각, 끝에 "자세히 →". 띠 전체가 상세로 간다. now는 서버가 판매 칸을 받은 시각.
 */
export default function SaleBand({ pick, now }: { pick: SalePick; now: Date }) {
  const { sale } = pick;
  const image = wideSaleImage(sale);
  const badge = saleBadge(sale, now);

  return (
    <Link href={saleHref(sale.id)} className={styles.band}>
      <span className={styles.media}>
        {image && (
          // 이름이 바로 옆에 있으므로 장식으로 다룬다. 첫 화면 아래쪽이라 늦게 불러온다
          <RemoteImage
            src={image.src}
            fallbackSrc={image.fallback}
            alt=""
            fill
            sizes="(max-width: 599px) 100vw, 420px"
            loading="lazy"
            className={styles.img}
          />
        )}
      </span>
      <span className={styles.text}>
        <span className={styles.kicker}>{SALE_PICK_KICKER[pick.reason]}</span>
        <span className={styles.title}>{sale.productName}</span>
        <span className={styles.meta}>
          {badge && <SaleBadge badge={badge} />}
          <span>
            {vendorLabel(sale.vendor)} · {saleKindLabel(sale.saleType, sale.phase)} · {saleWhenText(sale, now)}
          </span>
        </span>
      </span>
      <span className={styles.go}>자세히 →</span>
    </Link>
  );
}

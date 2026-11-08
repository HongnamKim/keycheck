import Link from "next/link";
import type { SalePick } from "@/lib/api";
import { pickLedgerWhen, SALE_PICK_KICKER, wideSaleImage } from "@/lib/homePick";
import { saleHref } from "@/lib/saleQuery";
import { saleBadge, saleKindLabel, vendorLabel } from "@/lib/saleStatus";
import RemoteImage from "../RemoteImage";
import SaleBadge from "../sale/SaleBadge";
import bento from "./BentoCell.module.css";
import styles from "./SaleFeatureCell.module.css";

/**
 * 하이라이트 큰 칸이 국내 판매일 때(시안 d). geekhack 큰 칸(BentoCell FEATURE)과 같은 모양에
 * 배지 줄은 상태 배지와 "국내 판매" 칩, 장부는 geekhack 반응 대신 판매 시각과 판매처·구분.
 * now는 서버가 판매 칸을 받은 시각. 칸 자리(grid-area)는 className으로 받는다.
 */
export default function SaleFeatureCell({ pick, now, className }: { pick: SalePick; now: Date; className?: string }) {
  const { sale } = pick;
  const image = wideSaleImage(sale);
  const badge = saleBadge(sale, now);
  const when = pickLedgerWhen(pick, now);

  return (
    <Link href={saleHref(sale.id)} className={`${bento.cell} ${bento.feature} ${className ?? ""}`}>
      {image && (
        <span className={bento.media}>
          {/* 기본으로 숨겨진 모양이라 늦게 불러온다(보일 때만 받는다). 국내 판매 쪽이 걸리면 homePickScript가 data-kr-hero 사진을 바로 받게 바꾼다 */}
          <RemoteImage
            src={image.src}
            fallbackSrc={image.fallback}
            alt=""
            fill
            sizes="(max-width: 599px) 100vw, (max-width: 1023px) 66vw, 540px"
            loading="lazy"
            data-kr-hero=""
            className={bento.img}
          />
        </span>
      )}
      <span className={bento.shade} />
      <span className={bento.badges}>
        {badge && (
          // 사진 위라 채움 배지가 아니면 흰 테두리·흰 글자로 바꾼다
          <span className={badge.tone === "fill" ? styles.status : `${styles.status} ${styles.onPhoto}`}>
            <SaleBadge badge={badge} />
          </span>
        )}
        <span className={styles.chip}>국내 판매</span>
      </span>
      <span className={bento.body}>
        <span className={bento.kicker}>{SALE_PICK_KICKER[pick.reason]}</span>
        <span className={bento.title} title={sale.productName}>
          {sale.productName}
        </span>
        {sale.summaryFirstLine && <span className={bento.sum}>{sale.summaryFirstLine}</span>}
        <span className={bento.ledger}>
          <span className={bento.ledgerItem}>
            <span className={bento.ledgerKey}>
              <span className={bento.dot} />
              {when.key}
            </span>
            <span className={bento.ledgerValue}>{when.value}</span>
          </span>
          <span className={bento.ledgerItem}>
            <span className={bento.ledgerKey}>
              <span className={`${bento.dot} ${bento.dotQuiet}`} />
              판매처 · 구분
            </span>
            <span className={bento.ledgerValue}>
              {vendorLabel(sale.vendor)} · {saleKindLabel(sale.saleType, sale.phase)}
            </span>
          </span>
        </span>
      </span>
    </Link>
  );
}

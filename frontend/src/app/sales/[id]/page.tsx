import type { Metadata } from "next";
import { notFound, unstable_rethrow } from "next/navigation";
import { connection } from "next/server";
import Link from "next/link";
import { Suspense } from "react";
import Gallery from "@/components/Gallery";
import LoadFailed from "@/components/LoadFailed";
import RemoteImage from "@/components/RemoteImage";
import SaleBadge from "@/components/sale/SaleBadge";
import { getSaleForPage, type SaleDetail as SaleDetailData } from "@/lib/api";
import { formatKstTime, formatMonthDay, kstDateKey } from "@/lib/format";
import { parsePostId } from "@/lib/query";
import { saleBackHref, saleHref, parseSaleDetailQuery } from "@/lib/saleQuery";
import { saleBadge, saleKindLabel, vendorLabel } from "@/lib/saleStatus";
import { saleMetadata } from "@/lib/seo";
import styles from "./page.module.css";

export async function generateMetadata({ params }: PageProps<"/sales/[id]">): Promise<Metadata> {
  const saleId = parsePostId((await params).id);
  if (saleId === null) notFound();

  let sale: SaleDetailData | null;
  try {
    sale = await getSaleForPage(saleId);
  } catch (err) {
    unstable_rethrow(err);
    // 잠깐의 장애로 색인이 빠지지 않게 robots는 넣지 않는다. 오류는 본문이 남긴다
    return { alternates: { canonical: `/sales/${saleId}` } };
  }
  if (!sale) notFound();
  return saleMetadata(sale);
}

export default function SalePage({ params, searchParams }: PageProps<"/sales/[id]">) {
  return (
    <Suspense fallback={<p className={styles.loading}>판매 정보를 불러오는 중이에요</p>}>
      <SaleDetail params={params} searchParams={searchParams} />
    </Suspense>
  );
}

/** 시작·마감 표기. 날짜만 알면 날짜만 (예: 11/6(금) 19:30, 11/6(금)). 날짜를 모르면 null */
function whenValue(date: string | null, time: string | null): string | null {
  if (!date) return null;
  const day = formatMonthDay(date, true);
  return time ? `${day} ${time}` : day;
}

async function SaleDetail({ params, searchParams }: PageProps<"/sales/[id]">) {
  const saleId = parsePostId((await params).id);
  if (saleId === null) notFound();

  const from = parseSaleDetailQuery(await searchParams);

  let sale: SaleDetailData | null;
  try {
    sale = await getSaleForPage(saleId);
  } catch (err) {
    unstable_rethrow(err);
    console.error(err);
    return (
      <LoadFailed>
        {/* 같은 주소를 새로 열어 다시 불러온다 */}
        <a href={saleHref(saleId, from)} className="outline-button">
          다시 시도
        </a>
      </LoadFailed>
    );
  }
  if (!sale) notFound();

  // 배지의 지금 시각은 서버가 요청마다 한 번 정한다(브라우저 시계를 쓰지 않는다)
  await connection();
  const now = new Date();
  const badge = saleBadge(sale, now);
  const topImage = sale.imageUrl ?? sale.largeUrl ?? sale.thumbUrl;
  const topFallback = sale.imageUrl ? (sale.largeUrl ?? sale.thumbUrl) : sale.largeUrl ? sale.thumbUrl : null;
  const rows = [
    { label: "판매처", value: vendorLabel(sale.vendor) },
    { label: "구분", value: saleKindLabel(sale.saleType, sale.phase) },
    { label: "판매 시작", value: whenValue(sale.startDate, sale.startTime) },
    { label: "마감", value: whenValue(sale.endDate, sale.endTime) },
    { label: "배송 일정", value: sale.shippingNote },
  ].filter((row): row is { label: string; value: string } => Boolean(row.value));
  const postedDay = formatMonthDay(kstDateKey(new Date(sale.postedAt)), true);

  return (
    <div className={styles.detail}>
      <div>
        <Link href={saleBackHref(from)} className="outline-button">
          {from ? "목록으로" : "메인으로"}
        </Link>
      </div>
      <div className={styles.columns}>
        <div className={styles.media}>
          {topImage ? (
            // 원본 크기를 모르므로 width·height는 비율용 값이고, 실제 크기는 CSS가 정한다
            <RemoteImage
              src={topImage}
              fallbackSrc={topFallback}
              alt={sale.productName}
              width={1200}
              height={800}
              sizes="(max-width: 900px) 100vw, 600px"
              preload
              className={styles.image}
            />
          ) : (
            <span className={styles.noImage}>이미지 없음</span>
          )}
        </div>

        <article className={styles.article}>
          <div className={styles.head}>
            <p className={styles.category}>
              {badge && <SaleBadge badge={badge} />}
              <span>
                {vendorLabel(sale.vendor)} · {saleKindLabel(sale.saleType, sale.phase)}
              </span>
            </p>
            <h1 className={styles.title}>{sale.productName}</h1>
          </div>

          {sale.summaryLines.length > 0 && (
            <section className={styles.section}>
              <h3 className={styles.sectionTitle}>핵심 요약</h3>
              <ul className={styles.points}>
                {sale.summaryLines.map((line, i) => (
                  <li key={i}>{line}</li>
                ))}
              </ul>
            </section>
          )}

          <section className={styles.section}>
            <h3 className={styles.sectionTitle}>판매 정보</h3>
            <dl className={styles.specs}>
              {rows.map((row) => (
                <div key={row.label} className={styles.specRow}>
                  <dt>{row.label}</dt>
                  <dd>{row.value}</dd>
                </div>
              ))}
            </dl>
          </section>

          {sale.links.length > 0 && (
            <div className={styles.actions}>
              {sale.links.map((link, i) => (
                <a
                  key={i}
                  href={link.url}
                  target="_blank"
                  rel="noopener noreferrer"
                  className={i === 0 ? styles.primary : "outline-button"}
                >
                  {link.text}
                </a>
              ))}
            </div>
          )}

          <p className={styles.posted}>
            디스코드 공지 {postedDay} {formatKstTime(sale.postedAt)}
          </p>
        </article>
      </div>

      {sale.images.length >= 2 && (
        // 갤러리를 따로 묶어 본문이 숨김 영역으로 밀리지 않게 한다(글 상세와 같다)
        <Suspense fallback={null}>
          <section className={styles.gallery} aria-labelledby="gallery-title">
            <h3 id="gallery-title" className={styles.sectionTitle}>
              공지 사진 {sale.images.length}장
            </h3>
            <Gallery images={sale.images.map((image) => ({ url: image.url, thumbUrl: image.thumbUrl }))} />
          </section>
        </Suspense>
      )}
    </div>
  );
}

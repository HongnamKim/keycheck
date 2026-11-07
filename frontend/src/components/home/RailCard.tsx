import Link from "next/link";
import type { BoardFilter, PostCard } from "@/lib/api";
import { displayTitle, formatShortPostedDate, formatViews, photoChipLabel } from "@/lib/format";
import { gbPeriodChip } from "@/lib/gbPeriod";
import { imageSources } from "@/lib/image";
import { postHref } from "@/lib/query";
import KoreanVendorBadge from "../KoreanVendorBadge";
import RemoteImage from "../RemoteImage";
import PeriodRange from "./PeriodRange";
import Segs from "./Segs";
import styles from "./RailCard.module.css";

/** 띄어쓰기 자리에서 줄이 갈리지 않게 붙인다(시안 &nbsp; 자리) */
const NBSP = " ";

/** 사진 sizes(실제로는 변환하지 않지만 브라우저에 카드 폭을 알린다) */
const SIZES: Record<BoardFilter, string> = {
  ic: "(max-width: 599px) 50vw, (max-width: 1023px) 33vw, 236px",
  gb: "(max-width: 599px) 100vw, (max-width: 1023px) 50vw, 320px",
};

/**
 * 메인 가로 줄의 카드 한 장(li). 사진(IC 4:5, 공동구매 3:2) 위에 분류 칩과 한국 벤더 칩, 공동구매는 기간 칩과 D-day 칩(지났으면 끝남 칩).
 * today는 서버가 구한 한국 시간 오늘. 메인에서 연 상세는 "메인으로" 돌아온다(쿼리 없는 상세 주소).
 */
export default function RailCard({ post, board, today }: { post: PostCard; board: BoardFilter; today: string }) {
  const image = post.representativeImageUrl
    ? imageSources(post.representativeThumbUrl, post.representativeImageUrl)
    : null;
  const chip = photoChipLabel(post.category, post.subcategory);
  const period = post.board === "GB" ? gbPeriodChip(post.gbStartDate, post.gbEndDate, today) : null;
  const summary = post.summaryStatus === "DONE" ? post.summaryFirstLine : null;

  return (
    <li className={`${styles.card} ${board === "gb" ? styles.gb : styles.ic}`}>
      <Link href={postHref(post.id)} className={styles.link}>
        <span className={styles.media}>
          {image ? (
            <RemoteImage
              src={image.src}
              fallbackSrc={image.fallback}
              alt=""
              fill
              sizes={SIZES[board]}
              className={styles.img}
            />
          ) : (
            <span className={styles.noImage}>{post.detailLoaded ? "이미지 없음" : "열면 불러와요"}</span>
          )}
          {(chip || post.hasKoreanVendor) && (
            <span className={styles.chips}>
              {chip && <span className={styles.chip}>{chip}</span>}
              {post.hasKoreanVendor && <KoreanVendorBadge className={`${styles.chip} ${styles.korean}`} />}
            </span>
          )}
          {period && (
            <span className={period.ended ? `${styles.chip} ${styles.date} ${styles.ended}` : `${styles.chip} ${styles.date}`}>
              <PeriodRange range={period.range} />
            </span>
          )}
          {period?.dday && <span className={`${styles.chip} ${styles.dday}`}>{period.dday}</span>}
        </span>
        <span className={styles.title} title={post.title}>
          {displayTitle(post.productName, post.title)}
        </span>
        {summary && <span className={styles.sum}>{summary}</span>}
        <Segs
          className={styles.meta}
          items={[
            `조회${NBSP}${formatViews(post.views)}`,
            `댓글${NBSP}${post.replies}`,
            formatShortPostedDate(post.postedAt, post.estimatedPostedAt),
          ]}
        />
      </Link>
    </li>
  );
}

/** 글을 받기 전 카드 자리. 사진 비율과 글 높이를 잡는다 */
export function RailCardPlaceholder({ board }: { board: BoardFilter }) {
  return (
    <li className={`${styles.card} ${board === "gb" ? styles.gb : styles.ic}`}>
      <span className={styles.link}>
        <span className={styles.media} />
        <span className={styles.pendingText} />
      </span>
    </li>
  );
}

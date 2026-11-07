import Link from "next/link";
import type { HighlightSlot, PostCard } from "@/lib/api";
import { displayTitle, formatMonthDay, formatShortDate, formatViews, photoChipLabel } from "@/lib/format";
import { gbPeriodChip } from "@/lib/gbPeriod";
import { bentoImage, slotKicker } from "@/lib/highlight";
import { postHref } from "@/lib/query";
import BoardBadge from "../BoardBadge";
import KoreanVendorBadge from "../KoreanVendorBadge";
import RemoteImage from "../RemoteImage";
import PeriodRange from "./PeriodRange";
import Segs from "./Segs";
import styles from "./BentoCell.module.css";

/** 띄어쓰기 자리에서 줄이 갈리지 않게 붙인다(시안 &nbsp; 자리) */
const NBSP = " ";

/**
 * 하이라이트 한 칸. 큰 칸(FEATURE)은 요약과 장부(공동구매 기간·geekhack 반응), 세로 칸·작은 칸은 메타,
 * 마감 띠(DEADLINE)는 사진 없이 주황 바탕에 남은 날을 크게 보여준다.
 * compact면 남은 칸을 같은 크기로 늘어놓는 격자용으로 작은 칸 모양을 쓴다.
 * 날짜 계산의 오늘은 서버가 칸을 고른 날(highlights.today)이다. 칸 자리(grid-area)는 className으로 받는다.
 */
export default function BentoCell({
  slot,
  today,
  compact,
  className,
}: {
  slot: HighlightSlot;
  today: string;
  compact: boolean;
  className?: string;
}) {
  if (slot.deadline) return <DeadlineCell slot={slot} today={today} compact={compact} className={className} />;
  return <PhotoCell slot={slot} today={today} compact={compact} className={className} />;
}

function classes(...names: (string | false | undefined)[]): string {
  return names.filter(Boolean).join(" ");
}

/** 조회·댓글 토막 */
function views(post: PostCard): string {
  return `조회${NBSP}${formatViews(post.views)}`;
}

function replies(post: PostCard): string {
  return `댓글${NBSP}${post.replies}`;
}

/**
 * 출처 배지와(큰 칸·세로 칸·마감 띠만) 분류 태그, 한국 벤더가 있으면 한국 벤더 배지.
 * onAccent면 주황 바탕 위라 흰 공동구매 배지와 흰 테두리 한국 벤더 배지
 */
function Badges({ post, withTag, onAccent = false }: { post: PostCard; withTag: boolean; onAccent?: boolean }) {
  const tag = withTag ? photoChipLabel(post.category, post.subcategory) : null;
  const boardStyle = post.board === "GB" ? (onAccent ? styles.gbOn : styles.gb) : styles.ic;
  return (
    <span className={styles.badges}>
      <BoardBadge board={post.board} className={classes(styles.badge, boardStyle)} />
      {tag && <span className={classes(styles.badge, styles.tag)}>{tag}</span>}
      {post.hasKoreanVendor && (
        <KoreanVendorBadge className={classes(styles.badge, onAccent ? styles.koreanOn : styles.korean)} />
      )}
    </span>
  );
}

/** 칸 모양: 큰 칸, 세로 칸, 마감 띠 대신 들어간 넓은 사진 칸, 작은 칸 */
function photoLook(slot: HighlightSlot, compact: boolean): string {
  if (compact) return styles.small;
  if (slot.slot === "FEATURE") return styles.feature;
  if (slot.slot === "TALL") return styles.tall;
  if (slot.slot === "DEADLINE") return styles.widePhoto;
  return styles.small;
}

/** 이미지 sizes(실제로는 변환하지 않지만 브라우저에 칸 폭을 알린다) */
function photoSizes(slot: HighlightSlot, compact: boolean): string {
  if (compact) return "(max-width: 599px) 100vw, (max-width: 1023px) 50vw, 360px";
  if (slot.slot === "FEATURE") return "(max-width: 599px) 100vw, (max-width: 1023px) 66vw, 540px";
  if (slot.slot === "DEADLINE") return "(max-width: 1023px) 100vw, 810px";
  if (slot.slot === "GB_VIEWS") return "(max-width: 599px) 100vw, (max-width: 1023px) 33vw, 270px";
  return "(max-width: 599px) 50vw, (max-width: 1023px) 33vw, 270px";
}

function PhotoCell({
  slot,
  today,
  compact,
  className,
}: {
  slot: HighlightSlot;
  today: string;
  compact: boolean;
  className?: string;
}) {
  const { post } = slot;
  const feature = slot.slot === "FEATURE";
  const image = bentoImage(post, feature);
  const posted = post.postedAt ? formatShortDate(post.postedAt) : null;
  // 고른 기준을 맨 앞에: 댓글 1위 칸은 댓글부터(시안 d)
  const meta = slot.slot === "IC_REPLIES" ? [replies(post), views(post), posted] : [views(post), replies(post), posted];
  const summary = post.summaryStatus === "DONE" ? post.summaryFirstLine : null;
  const look = photoLook(slot, compact);

  return (
    <Link
      href={postHref(post.id)}
      className={classes(
        styles.cell,
        look,
        // 밝은 사진 위 글자: 큰 칸이 아니면 아래쪽을 더 일찍 어둡게(시안 d·f의 그림자. 사진이 밝을지는 칸 자리로 알 수 없다)
        look !== styles.feature && styles.darkShade,
        // 모바일에서 한 줄을 다 쓰는 칸은 큰 칸처럼 사진이 칸 전체를 덮는다(시안 f)
        (compact || slot.slot === "GB_VIEWS") && styles.wideOnMobile,
        className,
      )}
    >
      {image && (
        <span className={styles.media}>
          {/* 제목이 바로 뒤에 나오므로 장식으로 다룬다. 큰 칸은 첫 화면의 가장 큰 사진이라 먼저 받는다 */}
          <RemoteImage
            src={image.src}
            fallbackSrc={image.fallback}
            alt=""
            fill
            sizes={photoSizes(slot, compact)}
            loading={feature ? "eager" : "lazy"}
            fetchPriority={feature ? "high" : undefined}
            className={styles.img}
          />
        </span>
      )}
      <span className={styles.shade} />
      <Badges post={post} withTag={feature || slot.slot === "TALL" || slot.slot === "DEADLINE"} />
      <span className={styles.body}>
        <Segs className={styles.kicker} items={slotKicker(slot)} />
        <span className={styles.title} title={post.title}>
          {displayTitle(post.productName, post.title)}
        </span>
        {feature && !compact ? (
          <>
            {summary && <span className={styles.sum}>{summary}</span>}
            <Ledger post={post} today={today} posted={posted} />
          </>
        ) : (
          <Segs className={styles.meta} items={meta} />
        )}
      </span>
    </Link>
  );
}

/**
 * 큰 칸 아래 두 칸: 공동구매 기간(마감 전이면 "마감까지 D-n")과 geekhack 반응.
 * 마감일을 모르면 기간 칸을 빼고 반응 칸만 전체 폭으로 둔다(설계 2장: 모르면 보여주지 않는다).
 */
function Ledger({ post, today, posted }: { post: PostCard; today: string; posted: string | null }) {
  const period = gbPeriodChip(post.gbStartDate, post.gbEndDate, today, true);
  return (
    <span className={classes(styles.ledger, !period && styles.ledgerSingle)}>
      {period && (
        <span className={styles.ledgerItem}>
          <span className={styles.ledgerKey}>
            <span className={styles.dot} />
            공동구매 기간
          </span>
          <span className={styles.ledgerValue}>
            <PeriodRange range={period.range} />
          </span>
          {period.dday && (
            <span className={styles.ledgerValue}>
              마감까지<span className={styles.dday}>{period.dday}</span>
            </span>
          )}
        </span>
      )}
      <span className={styles.ledgerItem}>
        <span className={styles.ledgerKey}>
          <span className={classes(styles.dot, styles.dotQuiet)} />
          geekhack 반응
        </span>
        <Segs className={styles.ledgerValue} items={[views(post), replies(post)]} />
        {posted && <span className={styles.ledgerValue}>{posted} 올라옴</span>}
      </span>
    </span>
  );
}

/** 마감 띠: 사진 대신 주황 바탕 + 키 배열 무늬에 마감일과 남은 날을 크게 */
function DeadlineCell({
  slot,
  today,
  compact,
  className,
}: {
  slot: HighlightSlot;
  today: string;
  compact: boolean;
  className?: string;
}) {
  const { post } = slot;
  // 서버가 같은 오늘로 "오늘 이후 마감"인 글을 골랐으므로 기간과 남은 날은 늘 있다
  const period = gbPeriodChip(post.gbStartDate, post.gbEndDate, today);

  return (
    <Link href={postHref(post.id)} className={classes(styles.cell, styles.deadline, compact && styles.compact, className)}>
      <Badges post={post} withTag onAccent />
      <span className={styles.dropText}>
        <Segs className={styles.kicker} items={slotKicker(slot)} />
        <span className={styles.dropTitle} title={post.title}>
          {displayTitle(post.productName, post.title)}
        </span>
        <Segs
          className={styles.meta}
          items={[period && <PeriodRange range={period.range} />, views(post), replies(post)]}
        />
      </span>
      {post.gbEndDate && period?.dday && (
        <span className={styles.dropTime}>
          <span className={styles.dropDate}>{formatMonthDay(post.gbEndDate, true)} 마감</span>
          <span className={styles.dropClock}>{period.dday}</span>
        </span>
      )}
    </Link>
  );
}

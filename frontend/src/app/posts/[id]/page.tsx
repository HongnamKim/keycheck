import Link from "next/link";
import { notFound, unstable_rethrow } from "next/navigation";
import { Fragment, Suspense } from "react";
import BoardBadge from "@/components/BoardBadge";
import Gallery from "@/components/Gallery";
import LoadFailed from "@/components/LoadFailed";
import NeighborList from "@/components/NeighborList";
import PostDates from "@/components/PostDates";
import RemoteImage from "@/components/RemoteImage";
import StatsLive from "@/components/StatsLive";
import SummaryWatcher from "@/components/SummaryWatcher";
import { getPost, type PostDetail as PostDetailData } from "@/lib/api";
import { postCategoryLabel } from "@/lib/format";
import { imageSources } from "@/lib/image";
import { backHref, parseDetailQuery, parsePostId, postHref } from "@/lib/query";
import { vendorLine, vendorSpecRows } from "@/lib/vendor";
import styles from "./page.module.css";

export default function PostPage({ params, searchParams }: PageProps<"/posts/[id]">) {
  return (
    <Suspense fallback={<p className={styles.loading}>글을 불러오는 중이에요</p>}>
      <PostDetail params={params} searchParams={searchParams} />
    </Suspense>
  );
}

async function PostDetail({ params, searchParams }: PageProps<"/posts/[id]">) {
  const postId = parsePostId((await params).id);
  if (postId === null) notFound();

  // 목록 카드는 보던 게시판·필터·정렬을 상세 주소에 실어 보낸다(postHref). 그 목록으로, 없으면(메인에서 열면) 메인으로 돌아간다.
  const from = parseDetailQuery(await searchParams);

  let post: PostDetailData | null;
  try {
    post = await getPost(postId);
  } catch (err) {
    unstable_rethrow(err);
    // 백엔드가 멈췄거나 느리면(시간 제한) 안내를 띄운다. 없는 글(404)은 아래 notFound로 따로 간다
    console.error(err);
    return (
      <LoadFailed>
        {/* 같은 주소를 새로 열어 다시 불러온다 */}
        <a href={postHref(postId, from)} className="outline-button">
          다시 시도
        </a>
      </LoadFailed>
    );
  }
  if (!post) notFound();

  const renderedAt = new Date().toISOString();
  const hasSummary = post.summaryStatus === "DONE" && post.summaryLines.length > 0;
  // 맨 위 큰 이미지는 대표 이미지의 큰 축소본(1280px)을 쓴다
  const image = post.representativeImageUrl
    ? imageSources(post.representativeLargeUrl, post.representativeImageUrl)
    : null;
  // 요약기가 뽑은 벤더 목록이 있으면 AI 스펙표의 벤더 줄을 빼고, 그 자리(없으면 끝)에 한국 벤더를 앞세운 벤더 줄을 새로 그린다
  // 프론트가 백엔드보다 먼저 배포되면 응답에 vendors가 없다. 그때는 벤더가 없는 글처럼 그린다
  const postVendors = post.vendors ?? [];
  const vendors = postVendors.length > 0 ? vendorLine(postVendors) : null;
  const { specs, vendorRowAt } = vendorSpecRows(post.specs, postVendors);
  const vendorRow = vendors && (
    <div className={styles.specRow}>
      <dt>벤더</dt>
      <dd>
        {vendors.korean.map((vendor, i) => (
          <Fragment key={i}>
            {i > 0 && ", "}
            {vendor.href ? (
              <a href={vendor.href} target="_blank" rel="noopener noreferrer">
                <strong>{vendor.name}</strong>
              </a>
            ) : (
              <strong>{vendor.name}</strong>
            )}
          </Fragment>
        ))}
        {vendors.korean.length > 0 && vendors.others && ", "}
        {vendors.others}
      </dd>
    </div>
  );

  return (
    <div className={styles.detail}>
      <div>
        <Link href={backHref(from)} className="outline-button">
          {from ? "목록으로" : "메인으로"}
        </Link>
      </div>
      <div className={styles.columns}>
        <div className={styles.media}>
          {image ? (
            // 원본 크기를 모르므로 width·height는 비율용 값이고, 실제 크기는 CSS(width 100%, height auto)가 정한다
            <RemoteImage
              src={image.src}
              fallbackSrc={image.fallback}
              alt={post.title}
              width={1200}
              height={800}
              sizes="(max-width: 900px) 100vw, 600px"
              preload
              className={styles.image}
            />
          ) : (
            <span className={styles.noImage}>{post.detailLoaded ? "이미지 없음" : "이미지를 아직 불러오지 못했어요"}</span>
          )}
        </div>

        <article className={styles.article}>
          <div className={styles.head}>
            <p className={styles.category}>
              <BoardBadge board={post.board} />
              <span>{postCategoryLabel(post.category, post.subcategory)}</span>
            </p>
            <h1 className={styles.title}>{post.title}</h1>
            <p className={styles.byline}>
              작성자 {post.author}
              <PostDates
                postedAt={post.postedAt}
                estimatedPostedAt={post.estimatedPostedAt}
                lastPostAt={post.lastPostAt}
                replies={post.replies}
                now={new Date(renderedAt)}
                leadingDot
              />
            </p>
          </div>

          {!post.detailLoaded ? (
            // 목록에서만 가져온 틀 글인데 백엔드가 이번 요청에서 원문을 읽지 못했다
            <section className={styles.section}>
              <p className={styles.pending}>원문을 아직 불러오지 못했어요. 잠시 뒤 다시 열어 주세요</p>
              <div>
                {/* 같은 주소를 새로 열어 백엔드가 원문을 다시 읽게 한다 */}
                <a href={postHref(post.id, from)} className="outline-button">
                  다시 시도
                </a>
              </div>
            </section>
          ) : (
            <>
              <section className={styles.section}>
                <h3 className={styles.sectionTitle}>핵심 요약</h3>
                {/* 요약 대기 안내가 요약으로 바뀌어도 이 라이브 영역은 남아 화면 낭독기가 도착을 읽어 준다 */}
                <div aria-live="polite">
                  {hasSummary ? (
                    <ul className={styles.points}>
                      {post.summaryLines.map((line, i) => (
                        <li key={i}>{line}</li>
                      ))}
                    </ul>
                  ) : post.summaryStatus === "PENDING" ? (
                    // 요약 작업이 끝나면 새로고침 없이 이 자리에 요약이 들어온다
                    <SummaryWatcher postId={post.id} />
                  ) : post.summaryStatus === "FAILED" ? (
                    <p className={styles.pending}>요약을 만들지 못했어요</p>
                  ) : (
                    <p className={styles.pending}>요약 준비 중</p>
                  )}
                </div>
              </section>

              {(specs.length > 0 || vendorRow) && (
                <section className={styles.section}>
                  <h3 className={styles.sectionTitle}>{post.board === "GB" ? "공동구매 정보" : "스펙과 일정"}</h3>
                  <dl className={styles.specs}>
                    {specs.map((spec, i) => (
                      <Fragment key={i}>
                        {i === vendorRowAt && vendorRow}
                        <div className={styles.specRow}>
                          <dt>{spec.label}</dt>
                          <dd>{spec.value}</dd>
                        </div>
                      </Fragment>
                    ))}
                    {vendorRowAt === specs.length && vendorRow}
                  </dl>
                </section>
              )}
            </>
          )}

          <p className={styles.notice}>
            가격·일정은 원문에서 확인해 주세요. 글쓴이가 본문을 갱신하지 않아 지난 정보일 수 있어요.
          </p>

          <div className={styles.actions}>
            <a href={post.originalUrl} target="_blank" rel="noopener noreferrer" className={styles.original}>
              geekhack 원문 보기
            </a>
            <StatsLive
              postId={post.id}
              initial={{ views: post.views, replies: post.replies, statsCheckedAt: post.statsCheckedAt }}
              renderedAt={renderedAt}
              className={styles.stats}
            />
          </div>

          <p className={styles.disclaimer}>
            요약은 원문 첫 글을 번역·정리한 내용이라 최신 공지와 다를 수 있어요. 구매 전에는 원문을 확인해 주세요. 글과
            이미지의 권리는 원작성자에게 있어요.
          </p>
        </article>
      </div>

      {post.detailLoaded && post.images.length > 0 && (
        <section className={styles.gallery} aria-labelledby="gallery-title">
          <h3 id="gallery-title" className={styles.sectionTitle}>
            원문 이미지 {post.images.length}장
          </h3>
          <Gallery images={post.images} />
        </section>
      )}

      {/* 보던 목록의 위아래 글. 따로 받아 본문을 막지 않고, 실패하면 섹션만 빠진다 */}
      <Suspense fallback={null}>
        <NeighborList post={post} board={post.board} from={from} />
      </Suspense>
    </div>
  );
}

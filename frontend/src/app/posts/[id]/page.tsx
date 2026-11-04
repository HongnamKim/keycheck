import Link from "next/link";
import { notFound } from "next/navigation";
import { Suspense } from "react";
import BoardBadge from "@/components/BoardBadge";
import Gallery from "@/components/Gallery";
import NeighborList from "@/components/NeighborList";
import PostDates from "@/components/PostDates";
import RemoteImage from "@/components/RemoteImage";
import StatsLive from "@/components/StatsLive";
import { getPost } from "@/lib/api";
import { postCategoryLabel } from "@/lib/format";
import { backHref, parseDetailQuery, parsePostId, postHref } from "@/lib/query";
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

  const post = await getPost(postId);
  if (!post) notFound();

  // 목록 카드는 보던 게시판·필터·정렬을 상세 주소에 실어 보낸다(postHref). 그 목록으로, 없으면(메인에서 열면) 메인으로 돌아간다.
  const from = parseDetailQuery(await searchParams);
  const renderedAt = new Date().toISOString();
  const hasSummary = post.summaryStatus === "DONE" && post.summaryLines.length > 0;

  return (
    <div className={styles.detail}>
      <div>
        <Link href={backHref(from)} className="outline-button">
          {from ? "목록으로" : "메인으로"}
        </Link>
      </div>
      <div className={styles.columns}>
        <div className={styles.media}>
          {post.representativeImageUrl ? (
            // 원본 크기를 모르므로 width·height는 비율용 값이고, 실제 크기는 CSS(width 100%, height auto)가 정한다
            <RemoteImage
              src={post.representativeImageUrl}
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
                {hasSummary ? (
                  <ul className={styles.points}>
                    {post.summaryLines.map((line, i) => (
                      <li key={i}>{line}</li>
                    ))}
                  </ul>
                ) : (
                  <p className={styles.pending}>요약 준비 중</p>
                )}
              </section>

              {post.specs.length > 0 && (
                <section className={styles.section}>
                  <h3 className={styles.sectionTitle}>{post.board === "GB" ? "공동구매 정보" : "스펙과 일정"}</h3>
                  <dl className={styles.specs}>
                    {post.specs.map((spec, i) => (
                      <div key={i} className={styles.specRow}>
                        <dt>{spec.label}</dt>
                        <dd>{spec.value}</dd>
                      </div>
                    ))}
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

      {post.detailLoaded && post.imageUrls.length > 0 && (
        <section className={styles.gallery} aria-labelledby="gallery-title">
          <h3 id="gallery-title" className={styles.sectionTitle}>
            원문 이미지 {post.imageUrls.length}장
          </h3>
          <Gallery urls={post.imageUrls} />
        </section>
      )}

      {/* 보던 목록의 위아래 글. 따로 받아 본문을 막지 않고, 실패하면 섹션만 빠진다 */}
      <Suspense fallback={null}>
        <NeighborList post={post} board={post.board} from={from} />
      </Suspense>
    </div>
  );
}

import { unstable_rethrow } from "next/navigation";
import { ImageResponse } from "next/og";
import { getPost, type PostDetail } from "@/lib/api";
import { boardLabel, displayTitle } from "@/lib/format";
import { OG_COLORS, OG_FONT_FAMILY, OG_SITE_LINE, OgBoardBadge, siteCardImage } from "@/lib/ogCard";
import { loadOgFonts } from "@/lib/ogFont";
import { parsePostId } from "@/lib/query";
import { cardPhotoType, isCompleteCard, ogPhotoUrl } from "@/lib/seo";

/** 사진(Cloudflare 변환)을 기다리는 시간. 넘으면 사진 없이 그린다 */
const PHOTO_TIMEOUT_MS = 5000;

/**
 * 카드 주소에는 내용으로 만든 버전(?v=)이 붙어 내용이 바뀌면 주소가 바뀐다. 그래서 다 그린 카드는 오래 캐시한다.
 * 사진이나 글꼴을 받지 못했거나 글 대신 기본 카드를 그렸으면 이 헤더를 붙이지 않는다(잠깐의 실패가 1년 동안 남지 않게).
 */
const IMMUTABLE_CACHE = "public, max-age=86400, s-maxage=31536000, immutable";

/**
 * 글 미리보기 카드(1200×630 PNG). 대표 사진을 깔고 아래를 어둡게 한 뒤 게시판 배지, 상품 이름, 출처 줄을 얹는다.
 * 사진이 없으면 사이트 색 바탕에 글자만 둔다. 없는 글이거나 백엔드가 실패하면 사이트 기본 카드를 그린다.
 */
export async function GET(_request: Request, ctx: RouteContext<"/posts/[id]/og.png">) {
  const postId = parsePostId((await ctx.params).id);
  if (postId === null) return siteCardImage();

  let post: PostDetail | null;
  try {
    // 카드를 그리려고 틀 글의 원문을 읽지 않는다
    post = await getPost(postId, undefined, { fill: false });
  } catch (err) {
    unstable_rethrow(err);
    console.error(err);
    return siteCardImage();
  }
  if (!post) return siteCardImage();

  const name = displayTitle(post.productName, post.title);
  const photoUrl = ogPhotoUrl(post);
  // 수집기가 기다리다 끊지 않게 사진과 글꼴을 함께 받는다. 줄이 넘쳐 잘리면 satori가 …를 그린다
  const [photo, fonts] = await Promise.all([
    photoUrl ? loadPhoto(photoUrl) : null,
    loadOgFonts(`${boardLabel(post.board)}${name}${OG_SITE_LINE}…`),
  ]);
  const complete = isCompleteCard(photoUrl !== null, photo !== null, fonts !== undefined);

  const card = photo ? <PhotoCard post={post} name={name} photo={photo} /> : <PlainCard post={post} name={name} />;
  return new ImageResponse(card, {
    width: 1200,
    height: 630,
    fonts,
    headers: complete ? { "Cache-Control": IMMUTABLE_CACHE } : undefined,
  });
}

/**
 * 변환한 사진을 data URL로 받는다. 변환이 꺼져 있거나 한도를 넘으면 오류나 다른 형식이 오므로,
 * 카드 도구가 읽을 수 있는 JPEG·PNG가 아니면 null이다(WebP는 빈 화면으로 그려진다).
 */
async function loadPhoto(url: string): Promise<string | null> {
  try {
    const res = await fetch(url, { signal: AbortSignal.timeout(PHOTO_TIMEOUT_MS) });
    const type = cardPhotoType(res.headers.get("content-type"));
    if (!res.ok || !type) {
      console.error(`카드 사진을 받지 못함: ${res.status} ${res.headers.get("content-type")} ${url}`);
      return null;
    }
    return `data:${type};base64,${Buffer.from(await res.arrayBuffer()).toString("base64")}`;
  } catch (err) {
    unstable_rethrow(err);
    console.error(err);
    return null;
  }
}

/** 사진을 깐 카드. 메인 하이라이트 큰 칸처럼 아래로 갈수록 어둡게 하고 흰 글자를 얹는다 */
function PhotoCard({ post, name, photo }: { post: PostDetail; name: string; photo: string }) {
  return (
    <div
      style={{
        width: "100%",
        height: "100%",
        display: "flex",
        position: "relative",
        background: OG_COLORS.photoBg,
        fontFamily: OG_FONT_FAMILY,
      }}
    >
      {/* next/image는 카드 도구에서 쓸 수 없다 */}
      {/* eslint-disable-next-line @next/next/no-img-element */}
      <img
        src={photo}
        alt=""
        width={1200}
        height={630}
        style={{ position: "absolute", top: 0, left: 0, width: 1200, height: 630, objectFit: "cover" }}
      />
      <div
        style={{
          position: "absolute",
          top: 0,
          left: 0,
          width: "100%",
          height: "100%",
          backgroundImage:
            "linear-gradient(180deg, rgba(12, 13, 14, 0.05) 25%, rgba(12, 13, 14, 0.62) 55%, rgba(12, 13, 14, 0.92) 100%)",
        }}
      />
      <div
        style={{
          position: "absolute",
          left: 72,
          right: 72,
          bottom: 60,
          display: "flex",
          flexDirection: "column",
          alignItems: "flex-start",
          color: "#ffffff",
        }}
      >
        <OgBoardBadge board={post.board} onPhoto />
        <div style={{ display: "block", marginTop: 22, fontSize: 68, fontWeight: 700, lineHeight: 1.15, lineClamp: 2 }}>
          {name}
        </div>
        <div style={{ marginTop: 26, fontSize: 30, fontWeight: 500, color: "rgba(255, 255, 255, 0.86)" }}>
          {OG_SITE_LINE}
        </div>
      </div>
    </div>
  );
}

/** 사진이 없는 카드. 사이트 기본 카드처럼 밝은 바탕에 진한 글자와 헤더의 진한 선을 둔다 */
function PlainCard({ post, name }: { post: PostDetail; name: string }) {
  return (
    <div
      style={{
        width: "100%",
        height: "100%",
        display: "flex",
        flexDirection: "column",
        justifyContent: "space-between",
        padding: "84px 88px 72px",
        background: OG_COLORS.bg,
        color: OG_COLORS.text,
        fontFamily: OG_FONT_FAMILY,
      }}
    >
      <div style={{ display: "flex", flexDirection: "column", alignItems: "flex-start" }}>
        <OgBoardBadge board={post.board} onPhoto={false} />
        <div style={{ display: "block", marginTop: 30, fontSize: 80, fontWeight: 700, lineHeight: 1.15, lineClamp: 3 }}>
          {name}
        </div>
      </div>
      <div
        style={{
          display: "flex",
          paddingTop: 32,
          borderTop: `3px solid ${OG_COLORS.text}`,
          fontSize: 34,
          fontWeight: 700,
          color: OG_COLORS.accent,
        }}
      >
        {OG_SITE_LINE}
      </div>
    </div>
  );
}

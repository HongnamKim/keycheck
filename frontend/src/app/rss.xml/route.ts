import { unstable_rethrow } from "next/navigation";
import { connection } from "next/server";
import { getFeedPosts, type FeedPost } from "@/lib/api";
import { rssXml } from "@/lib/rss";

/**
 * RSS 2.0 피드(/rss.xml). 요청 때 백엔드에서 요약을 마친 글을 받아 만든다(빌드가 백엔드에 기대지 않는다).
 * CDN에 10분 남기고, 그 뒤 1시간까지는 낡은 피드를 주면서 새로 만든다. 백엔드가 실패하면 503으로 끝내고 남기지 않는다.
 */
export async function GET() {
  await connection();

  let posts: FeedPost[];
  try {
    posts = await getFeedPosts();
  } catch (err) {
    unstable_rethrow(err);
    console.error(err);
    return new Response("RSS를 만들지 못했어요. 잠시 뒤 다시 시도해 주세요\n", {
      status: 503,
      headers: { "Content-Type": "text/plain; charset=utf-8", "Cache-Control": "no-store" },
    });
  }

  return new Response(rssXml(posts, new Date()), {
    headers: {
      "Content-Type": "application/rss+xml; charset=utf-8",
      "Cache-Control": "public, max-age=0, s-maxage=600, stale-while-revalidate=3600",
    },
  });
}

"use client";

/**
 * 화면을 그리기 전에 도는 인라인 스크립트(Next 가이드 preventing-flash-before-hydration의 InlineScript).
 * 서버가 그린 HTML에서는 text/javascript라 읽는 즉시 돌고, 브라우저에서 그릴 때(사이트 안 이동)는 text/plain이라 돌지 않는다.
 * 브라우저 렌더에서 type이 갈리도록 클라이언트 컴포넌트로 둔다. 어긋난 type은 suppressHydrationWarning으로 넘긴다.
 */
export default function InlineScript({ code }: { code: string }) {
  return (
    <script
      type={typeof window === "undefined" ? "text/javascript" : "text/plain"}
      suppressHydrationWarning
      dangerouslySetInnerHTML={{ __html: code }}
    />
  );
}

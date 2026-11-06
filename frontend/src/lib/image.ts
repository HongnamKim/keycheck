/**
 * 화면에 쓸 이미지 주소. 백엔드가 만든 축소본(thumbUrl)이 있으면 그것을 먼저 읽고, 못 읽으면(저장소에서 사라짐 등)
 * fallback인 원본으로 바꾼다. 축소본이 아직 없으면 원본을 바로 읽는다.
 */
export function imageSources(thumbUrl: string | null, originalUrl: string): { src: string; fallback: string | null } {
  return thumbUrl ? { src: thumbUrl, fallback: originalUrl } : { src: originalUrl, fallback: null };
}

/**
 * RemoteImage가 지금 읽을 주소. 읽지 못한 주소(failedSrc)가 지금의 src이고 fallback이 있을 때만 fallback을 읽는다.
 * 실패를 주소로 기억하므로, 같은 자리에 새 src(새로고침으로 들어온 새 축소본 등)가 오면 그 주소부터 다시 읽는다.
 */
export function shownImageSrc(src: string, fallback: string | null, failedSrc: string | null): string {
  return failedSrc === src && fallback ? fallback : src;
}

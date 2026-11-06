"use client";

import Image, { type ImageProps } from "next/image";
import { useState } from "react";
import { shownImageSrc } from "@/lib/image";

/**
 * 외부 이미지를 브라우저가 주소 그대로 읽는다(Next 이미지 최적화를 쓰지 않음, next.config.ts).
 * src(축소본)를 읽지 못하면(저장소에서 사라짐 등) fallbackSrc(원본)가 있을 때 한 번만 그 주소로 바꾼다.
 * 실패는 주소로 기억해, 새로고침으로 같은 자리에 새 src가 오면 그 주소부터 다시 읽는다.
 * fallbackSrc는 꼭 넘긴다(바꿀 주소가 없으면 null). 빠뜨린 자리만 축소본이 사라졌을 때 깨진 이미지로 남는 일을 타입 검사로 막는다.
 */
export default function RemoteImage({
  alt,
  src,
  fallbackSrc,
  ...props
}: Omit<ImageProps, "src"> & { src: string; fallbackSrc: string | null }) {
  const [failedSrc, setFailedSrc] = useState<string | null>(null);

  return (
    <Image
      {...props}
      src={shownImageSrc(src, fallbackSrc, failedSrc)}
      alt={alt}
      referrerPolicy="no-referrer"
      onError={() => setFailedSrc(src)}
    />
  );
}

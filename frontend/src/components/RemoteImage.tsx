"use client";

import Image, { type ImageProps } from "next/image";
import { useState } from "react";

/**
 * 원본 이미지 주소를 Next 이미지 최적화로 줄여 보여준다.
 * 최적화가 실패하면(원본 응답이 7초를 넘는 등) 원본 주소를 그대로 불러온다.
 */
export default function RemoteImage({ alt, ...props }: ImageProps) {
  const [optimizeFailed, setOptimizeFailed] = useState(false);

  return (
    <Image
      {...props}
      alt={alt}
      unoptimized={optimizeFailed}
      referrerPolicy="no-referrer"
      onError={() => setOptimizeFailed(true)}
    />
  );
}

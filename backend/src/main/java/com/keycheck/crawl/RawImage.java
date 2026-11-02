package com.keycheck.crawl;

import com.keycheck.image.ImageSource;

/** 글 페이지에서 뽑아낸, 아직 저장하지 않은 이미지 */
public record RawImage(String url, ImageSource source, int position, boolean inSignature) {}

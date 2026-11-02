package com.keycheck.crawl

import com.keycheck.image.ImageSource

/** 글 페이지에서 뽑아낸, 아직 저장하지 않은 이미지 */
data class RawImage(
    val url: String,
    val source: ImageSource,
    val position: Int,
    val inSignature: Boolean,
)

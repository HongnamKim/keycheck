package com.keycheck.image

import com.keycheck.crawl.RawImage

interface RepresentativeImageSelector {
    fun select(images: List<RawImage>): Selection
}

/**
 * @property representativeIndex 대표 이미지의 images 인덱스. 후보가 없으면 null.
 * @property excludedReasons images와 같은 순서의 제외 사유. 후보면 null.
 */
data class Selection(
    val representativeIndex: Int?,
    val excludedReasons: List<String?>,
)

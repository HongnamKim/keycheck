package com.keycheck.image;

import java.util.List;

/**
 * @param representativeIndex 대표 이미지의 images 인덱스. 후보가 없으면 null.
 * @param excludedReasons images와 같은 순서의 제외 사유. 후보면 null.
 */
public record Selection(Integer representativeIndex, List<String> excludedReasons) {}

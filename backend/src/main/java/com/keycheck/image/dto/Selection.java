package com.keycheck.image.dto;

import java.util.List;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public class Selection {
  /** 대표 이미지의 images 인덱스. 후보가 없으면 null. */
  private final Integer representativeIndex;

  /** images와 같은 순서의 제외 사유. 후보면 null. */
  private final List<String> excludedReasons;
}

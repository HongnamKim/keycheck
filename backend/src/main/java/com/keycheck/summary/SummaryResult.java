package com.keycheck.summary;

import com.keycheck.post.SpecItem;
import java.util.List;

/**
 * @param lines 한국어 요약 문장 목록
 * @param specs 스펙표 (레이아웃, 마운트, 소재 등)
 */
public record SummaryResult(List<String> lines, List<SpecItem> specs) {}

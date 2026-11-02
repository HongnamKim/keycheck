package com.keycheck.post

/** 스펙표 한 줄 (예: label = "레이아웃", value = "65%"). post.specs jsonb에 목록으로 저장된다. */
data class SpecItem(val label: String, val value: String)

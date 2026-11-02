package com.keycheck.post

import org.springframework.data.domain.Sort
import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException

/** 목록 필터. API 값은 소문자(all, keyboard, keycap) */
enum class CategoryFilter(val category: Category?) {
    ALL(null), KEYBOARD(Category.KEYBOARD), KEYCAP(Category.KEYCAP);

    companion object {
        fun from(value: String): CategoryFilter = parseEnum(value)
    }
}

/** 목록 정렬. API 값은 소문자(recent, views) */
enum class PostSort(val sort: Sort) {
    RECENT(Sort.by(Sort.Order.desc("postedAt").nullsLast(), Sort.Order.desc("id"))),
    VIEWS(Sort.by(Sort.Order.desc("views"), Sort.Order.desc("id")));

    companion object {
        fun from(value: String): PostSort = parseEnum(value)
    }
}

private inline fun <reified E : Enum<E>> parseEnum(value: String): E =
    enumValues<E>().find { it.name.equals(value, ignoreCase = true) }
        ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "허용되지 않는 값: $value")

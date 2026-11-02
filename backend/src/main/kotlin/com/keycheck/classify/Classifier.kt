package com.keycheck.classify

import com.keycheck.post.Category
import org.springframework.stereotype.Component

/**
 * 설계 4.4: 제목으로 키캡·기타를 먼저 가려내고, 못 정하면 본문의 사양 단어로 판단한다.
 * 그래도 못 정하면 키보드로 본다 (IC 게시판 글은 대부분 키보드다).
 */
@Component
class Classifier {

    fun classify(title: String, bodyText: String): Category {
        if (KEYCAP_TITLE_TERMS.any { it.containsMatchIn(title) }) return Category.KEYCAP
        if (OTHER_TITLE_TERMS.any { it.containsMatchIn(title) }) return Category.OTHER

        val keyboardScore = KEYBOARD_BODY_TERMS.count { it.containsMatchIn(bodyText) }
        val keycapScore = KEYCAP_BODY_TERMS.count { it.containsMatchIn(bodyText) }
        return if (keycapScore > keyboardScore) Category.KEYCAP else Category.KEYBOARD
    }

    companion object {
        /** 단어 경계·대소문자 무시 정규식. "SA"가 "SALE"에 걸리지 않게 한다. */
        private fun terms(vararg patterns: String) =
            patterns.map { Regex("""\b(?:$it)\b""", RegexOption.IGNORE_CASE) }

        private val KEYCAP_TITLE_TERMS = terms(
            "GMK", "KAT", "MT3", "SA", "DSA", "DCS", "XDA", "e?PBT", "keycaps?", "keysets?",
        )

        private val OTHER_TITLE_TERMS = terms(
            "deskmats?", "desk ?mats?", "switch(?:es)?", "artisans?", "cables?",
        )

        /** 키보드 사양 항목 단어 (종류 수를 센다) */
        private val KEYBOARD_BODY_TERMS = terms(
            "plates?", "PCBs?", "gaskets?", "mount(?:ed|ing)?", "typing angle", "weight",
            "hot-?swap(?:pable)?", "case",
        )

        /** 키캡 사양 항목 단어 (종류 수를 센다) */
        private val KEYCAP_BODY_TERMS = terms(
            "base kits?", "alphas?", "modifiers?", "novelt(?:y|ies)", "spacebars?", "colou?rways?", "profile",
        )
    }
}

package com.keycheck.image

import com.keycheck.crawl.RawImage
import org.springframework.stereotype.Component

/** 설계 4.5: 규칙으로 부적합한 이미지를 거르고, 남은 후보 중 첫 번째를 대표로 고른다. */
@Component
class RuleBasedImageSelector : RepresentativeImageSelector {

    override fun select(images: List<RawImage>): Selection {
        val reasons = images.map { exclusionReason(it) }
        return Selection(
            representativeIndex = reasons.indexOfFirst { it == null }.takeIf { it >= 0 },
            excludedReasons = reasons,
        )
    }

    private fun exclusionReason(image: RawImage): String? {
        val url = image.url.lowercase()
        val fileName = url.substringBefore('?').substringAfterLast('/')
        return when {
            image.inSignature -> REASON_SIGNATURE
            SITE_ASSET_PATTERNS.any { it in url } -> REASON_SITE_ASSET
            BUTTON_PATTERNS.any { it in url } -> REASON_BUTTON
            DECORATION_FILE_KEYWORDS.any { it in fileName } -> REASON_DECORATION
            else -> null
        }
    }

    companion object {
        const val REASON_SIGNATURE = "signature"
        const val REASON_SITE_ASSET = "site-asset"
        const val REASON_BUTTON = "button"
        const val REASON_DECORATION = "decoration"

        /** geekhack 이모티콘·아바타·테마 이미지 */
        private val SITE_ASSET_PATTERNS = listOf("/smileys/", "/avatars/", "/themes/", ";type=avatar")

        /** 후원·디스코드·배지 버튼 */
        private val BUTTON_PATTERNS = listOf(
            "ko-fi.com", "paypal", "patreon", "buymeacoffee", "shields.io", "discord.com/api/guilds",
        )

        /** 파일명에 들어 있으면 장식용으로 보는 단어 */
        private val DECORATION_FILE_KEYWORDS = listOf("logo", "banner", "divider")
    }
}

package com.keycheck.image

import com.keycheck.crawl.RawImage
import com.keycheck.image.RuleBasedImageSelector.Companion.REASON_BUTTON
import com.keycheck.image.RuleBasedImageSelector.Companion.REASON_DECORATION
import com.keycheck.image.RuleBasedImageSelector.Companion.REASON_SIGNATURE
import com.keycheck.image.RuleBasedImageSelector.Companion.REASON_SITE_ASSET
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class RuleBasedImageSelectorTest {

    private val selector = RuleBasedImageSelector()

    private fun image(url: String, position: Int = 0, inSignature: Boolean = false) =
        RawImage(url, ImageSource.EXTERNAL, position, inSignature)

    @ParameterizedTest
    @CsvSource(
        "https://geekhack.org/Smileys/default/smiley.gif, site-asset",
        "https://geekhack.org/avatars/user.png, site-asset",
        "https://geekhack.org/Themes/default/images/on.png, site-asset",
        "https://geekhack.org/index.php?action=dlattach;attach=1;type=avatar, site-asset",
        "https://storage.ko-fi.com/cdn/kofi2.png, button",
        "https://img.shields.io/discord/123.svg, button",
        "https://discord.com/api/guilds/123/widget.png?style=banner2, button",
        "https://i.imgur.com/ProjectLogo.png, decoration",
        "https://example.com/img/BANNER_v2.jpg?w=1200, decoration",
        "https://example.com/divider.png, decoration",
    )
    fun `규칙에 맞는 이미지는 사유와 함께 제외한다`(url: String, reason: String) {
        val selection = selector.select(listOf(image(url)))

        assertEquals(listOf<String?>(reason), selection.excludedReasons)
        assertNull(selection.representativeIndex)
    }

    @Test
    fun `서명 영역 이미지는 제외한다`() {
        val selection = selector.select(listOf(image("https://i.imgur.com/render.jpg", inSignature = true)))

        assertEquals(listOf<String?>(REASON_SIGNATURE), selection.excludedReasons)
    }

    @Test
    fun `첫 이미지가 배너면 두 번째 이미지를 대표로 고른다`() {
        val selection = selector.select(
            listOf(
                image("https://i.imgur.com/banner.png", 0),
                image("https://i.imgur.com/render1.jpg", 1),
                image("https://i.imgur.com/render2.jpg", 2),
            )
        )

        assertEquals(1, selection.representativeIndex)
        assertEquals(listOf(REASON_DECORATION, null, null), selection.excludedReasons)
    }

    @Test
    fun `첨부 이미지와 쿼리 문자열만 있는 주소는 후보로 둔다`() {
        val attachment = RawImage(
            "https://geekhack.org/index.php?action=dlattach;topic=127200.0;attach=318371;image",
            ImageSource.ATTACHMENT, 0, false,
        )

        assertEquals(0, selector.select(listOf(attachment)).representativeIndex)
    }

    @Test
    fun `후보가 없으면 대표 이미지도 없다`() {
        val selection = selector.select(
            listOf(image("https://storage.ko-fi.com/cdn/kofi2.png"), image("https://x.com/logo.svg", 1))
        )

        assertNull(selection.representativeIndex)
        assertEquals(listOf(REASON_BUTTON, REASON_DECORATION), selection.excludedReasons)
    }

    @Test
    fun `이미지가 없으면 빈 결과를 돌려준다`() {
        val selection = selector.select(emptyList())

        assertNull(selection.representativeIndex)
        assertEquals(emptyList(), selection.excludedReasons)
    }

    @Test
    fun `사이트 이미지 규칙이 버튼·파일명 규칙보다 먼저 적용된다`() {
        val selection = selector.select(listOf(image("https://geekhack.org/Themes/default/images/logo.png")))

        assertEquals(listOf<String?>(REASON_SITE_ASSET), selection.excludedReasons)
    }
}

package com.keycheck.classify

import com.keycheck.post.Category
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import kotlin.test.Test
import kotlin.test.assertEquals

class ClassifierTest {

    private val classifier = Classifier()

    // 샘플 8건의 짧은 이름과 기타 예시. 본문 없이 제목만으로 판정되는지 본다.
    @ParameterizedTest
    @CsvSource(
        "[IC] Della 960, KEYBOARD",
        "[IC] F8X, KEYBOARD",
        "[IC] RHINO60, KEYBOARD",
        "[IC] Constance 60, KEYBOARD",
        "[IC] GMK CYL Blanks, KEYCAP",
        "[IC] GMK Knight, KEYCAP",
        "[IC] DCS Nights, KEYCAP",
        "[IC] DCS 더블샷 한글, KEYCAP",
        "[IC] KAT Lich keycaps, KEYCAP",
        "[IC] ePBT Kingdom, KEYCAP",
        "[IC] Mountain Deskmat, OTHER",
        "[IC] Desk Mats collection, OTHER",
        "[IC] Linear Switches v2, OTHER",
        "[IC] Coiled Cables, OTHER",
        "[IC] Dragon Artisan, OTHER",
    )
    fun `제목으로 분류한다`(title: String, expected: Category) {
        assertEquals(expected, classifier.classify(title, ""))
    }

    @Test
    fun `제목의 키캡 단어가 기타 단어보다 우선한다`() {
        assertEquals(Category.KEYCAP, classifier.classify("[IC] GMK Ocean + matching deskmat", ""))
    }

    @Test
    fun `제목이 본문보다 우선한다`() {
        val keyboardHeavyBody = "Render on a 65% keyboard. Plate: brass. PCB: hotswap. Gasket mount. Case weight 2kg."
        assertEquals(Category.KEYCAP, classifier.classify("[IC] GMK Knight", keyboardHeavyBody))
    }

    @Test
    fun `제목으로 못 정하면 본문에서 키캡 사양 단어가 많은 쪽을 고른다`() {
        val body = "Kits: Base kit, Alphas, Novelties, Spacebars. Two colorways. Shown on a TKL keyboard with a brass plate."
        assertEquals(Category.KEYCAP, classifier.classify("[IC] Sakura", body))
    }

    @Test
    fun `제목으로 못 정하면 본문에서 키보드 사양 단어가 많은 쪽을 고른다`() {
        val body = "Layout 65%. Gasket mount, FR4 plate, hotswap PCB. Typing angle 7 degrees. Shown with GMK keycaps (base kit)."
        assertEquals(Category.KEYBOARD, classifier.classify("[IC] Aurora", body))
    }

    @Test
    fun `본문 단어 수가 같거나 없으면 키보드로 본다`() {
        assertEquals(Category.KEYBOARD, classifier.classify("[IC] Sakura", "plate and profile"))
        assertEquals(Category.KEYBOARD, classifier.classify("[IC] Sakura", "Coming soon"))
    }

    @Test
    fun `같은 단어가 여러 번 나와도 한 종류로 센다`() {
        val body = "Alphas alphas ALPHAS alphas. Plate, PCB."
        assertEquals(Category.KEYBOARD, classifier.classify("[IC] Sakura", body))
    }

    @Test
    fun `단어 경계로 비교한다`() {
        // SALE의 SA, Kathmandu의 KAT, Switchblade의 Switch는 걸리지 않는다
        assertEquals(Category.KEYBOARD, classifier.classify("[IC] SALE Kathmandu Switchblade", ""))
    }
}

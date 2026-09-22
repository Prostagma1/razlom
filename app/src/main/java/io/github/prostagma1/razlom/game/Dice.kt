package io.github.prostagma1.razlom.game

import kotlin.math.floor
import kotlin.random.Random

/**
 * Кости в настольной записи: «2к6+3» — две шестигранных плюс три.
 * Без костей (count = 0) это просто ровное число — так живут старые
 * сохранения, где урон был фиксированным.
 */
data class Dice(val count: Int, val sides: Int, val bonus: Int = 0) {

    val min: Int get() = if (count == 0) bonus else count + bonus
    val max: Int get() = count * sides + bonus

    /** Среднее, округлённое до целого: для подписей и для старых сохранений. */
    val nominal: Int get() = floor(count * (sides + 1) / 2.0 + bonus + 0.5).toInt()

    fun plus(extra: Int): Dice = if (extra == 0) this else copy(bonus = bonus + extra)

    fun roll(rng: Random): Roll = Roll(this, List(count) { rng.nextInt(1, sides + 1) })

    /**
     * Точное распределение суммы. Нужно предпросмотру: «добьёт ли» считается
     * как вероятность, а не на глаз по среднему.
     */
    fun distribution(): Map<Int, Double> {
        var sums = mapOf(0 to 1.0)
        repeat(count) {
            val next = HashMap<Int, Double>()
            for ((sum, p) in sums) {
                for (face in 1..sides) {
                    next.merge(sum + face, p / sides, Double::plus)
                }
            }
            sums = next
        }
        return sums.mapKeys { it.key + bonus }
    }

    /** «2к6+3» — как пишут в настольных играх. */
    override fun toString(): String = when {
        count == 0 -> "$bonus"
        bonus > 0 -> "${count}к$sides+$bonus"
        bonus < 0 -> "${count}к$sides$bonus"
        else -> "${count}к$sides"
    }

    /** Та же запись латиницей — для файла сохранения. */
    fun encode(): String = if (count == 0) "$bonus" else "${count}d$sides${signed(bonus)}"

    companion object {
        fun flat(value: Int) = Dice(0, 0, value)

        private val pattern = Regex("""^(\d+)[dDкК](\d+)([+-]\d+)?$""")

        /** Понимает «2d6+3», «2к6-1» и просто «7». */
        fun parse(text: String): Dice? {
            val t = text.trim()
            t.toIntOrNull()?.let { return flat(it) }
            val m = pattern.matchEntire(t) ?: return null
            return Dice(
                count = m.groupValues[1].toInt(),
                sides = m.groupValues[2].toInt(),
                bonus = m.groupValues[3].takeIf { it.isNotEmpty() }?.toInt() ?: 0,
            )
        }

        private fun signed(v: Int) = when {
            v > 0 -> "+$v"
            v < 0 -> "$v"
            else -> ""
        }
    }
}

/** Выпавшие грани. Итог — сумма граней плюс бонус костей. */
data class Roll(val dice: Dice, val faces: List<Int>) {
    val total: Int get() = faces.sum() + dice.bonus

    /** «[3][1]+3 = 7» — для журнала боя. */
    fun describe(): String = buildString {
        faces.forEach { append("[$it]") }
        when {
            faces.isEmpty() -> append(dice.bonus)
            dice.bonus > 0 -> append("+${dice.bonus}")
            dice.bonus < 0 -> append(dice.bonus)
        }
        if (faces.isNotEmpty()) append(" = $total")
    }
}

/**
 * Удар — это бросок, пропущенный через множитель: полтора за фланг,
 * половина по соседям, 180% у прицельного выстрела. Хранить именно так
 * нужно, чтобы предпросмотр и настоящий удар считались одной формулой.
 */
class Hit(val dice: Dice, val scale: (Int) -> Int = { it }) {
    fun then(next: (Int) -> Int) = Hit(dice) { next(scale(it)) }

    fun distribution(): Map<Int, Double> {
        val out = HashMap<Int, Double>()
        for ((value, p) in dice.distribution()) out.merge(scale(value), p, Double::plus)
        return out
    }
}

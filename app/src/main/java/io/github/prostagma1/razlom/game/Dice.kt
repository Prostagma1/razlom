package io.github.prostagma1.razlom.game

import kotlin.math.floor
import kotlin.random.Random

/**
 * Правила броска урона. Меняются реликвиями, поэтому живут отдельно от костей:
 * одни и те же кости у разных сторон бросаются по-разному.
 */
data class RollRules(
    /** Единицы перебрасываются один раз. */
    val rerollOnes: Boolean = false,
    /** Во сколько раз бьёт крит — все грани на максимуме. */
    val critMultiplier: Int = 2,
)

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

    fun roll(rng: Random, rules: RollRules = RollRules()): Roll {
        val first = List(count) { rng.nextInt(1, sides + 1) }
        if (!rules.rerollOnes) return Roll(this, first)
        val rerolled = first.indices.filter { first[it] == 1 }.toSet()
        val faces = first.mapIndexed { i, face -> if (i in rerolled) rng.nextInt(1, sides + 1) else face }
        return Roll(this, faces, rerolled)
    }

    /** Крит — это все грани на максимуме, то есть ровно максимальная сумма. */
    fun isCrit(sum: Int): Boolean = count > 0 && sum == max

    /**
     * Точное распределение суммы. Нужно предпросмотру: «добьёт ли» считается
     * как вероятность, а не на глаз по среднему.
     */
    fun distribution(rerollOnes: Boolean = false): Map<Int, Double> {
        // Одна кость: с переброской единица выпадает, только если выпала дважды подряд.
        val face = (1..sides).associateWith { f ->
            val plain = 1.0 / sides
            if (!rerollOnes) plain else if (f == 1) plain * plain else plain + plain * plain
        }
        var sums = mapOf(0 to 1.0)
        repeat(count) {
            val next = HashMap<Int, Double>()
            for ((sum, p) in sums) {
                for ((f, pf) in face) next.merge(sum + f, p * pf, Double::plus)
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

/**
 * Выпавшие грани. Итог — сумма граней плюс бонус костей. Крит здесь только
 * отмечается, а не умножается: здоровье тоже бросается костями, и шесть
 * шестёрок на нём не должны давать вдвое больше жизни.
 */
data class Roll(
    val dice: Dice,
    val faces: List<Int>,
    /** Какие кости перебрасывались (счастливые кости). */
    val rerolled: Set<Int> = emptySet(),
) {
    val total: Int get() = faces.sum() + dice.bonus
    val crit: Boolean get() = dice.isCrit(total)

    /** «[3][1]+3 = 7» — для журнала боя. */
    fun describe(): String = buildString {
        faces.forEachIndexed { i, face -> append(if (i in rerolled) "[$face↻]" else "[$face]") }
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

    /** Во что превращается сумма граней: крит умножает до множителей удара. */
    fun value(sum: Int, rules: RollRules): Int =
        scale(if (dice.isCrit(sum)) sum * rules.critMultiplier else sum)

    /** Исходы удара: итог, вероятность и был ли это крит. */
    fun outcomes(rules: RollRules): List<Outcome> =
        dice.distribution(rules.rerollOnes).map { (sum, p) ->
            Outcome(value(sum, rules), p, dice.isCrit(sum))
        }

    data class Outcome(val value: Int, val chance: Double, val crit: Boolean)
}

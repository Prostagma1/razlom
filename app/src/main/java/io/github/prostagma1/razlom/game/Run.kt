package io.github.prostagma1.razlom.game

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import kotlin.random.Random

/** Боец отряда: живёт между боями, копит улучшения и раны. */
class Hero(
    val uid: Int,
    val type: UnitType,
    bonusHp: Int = 0,
    bonusAtk: Int = 0,
) {
    var bonusHp by mutableIntStateOf(bonusHp)
    var bonusAtk by mutableIntStateOf(bonusAtk)
    var hp by mutableIntStateOf(type.maxHp + bonusHp)

    val maxHp: Int get() = type.maxHp + bonusHp
    val attack: Int get() = type.attack + bonusAtk

    fun heal(amount: Int) {
        hp = (hp + amount).coerceIn(0, maxHp)
    }
}

enum class NodeKind(val label: String) {
    BATTLE("Схватка"),
    ELITE("Логово"),
    REST("Привал"),
    RECRUIT("Наёмники"),
    SHOP("Лавка"),
    BOSS("Пожиратель"),
}

class MapNode(
    val id: Int,
    val row: Int,
    val col: Int,
    val kind: NodeKind,
    val next: MutableList<Int> = mutableListOf(),
)

class RunMap(val rows: List<List<MapNode>>) {
    val nodes: Map<Int, MapNode> = rows.flatten().associateBy { it.id }
    val depth: Int get() = rows.size
}

object MapGenerator {
    const val DEPTH = 8

    fun generate(rng: Random): RunMap {
        var nextId = 0
        val rows = mutableListOf<List<MapNode>>()

        rows += listOf(MapNode(nextId++, 0, 1, NodeKind.BATTLE))

        for (row in 1 until DEPTH - 1) {
            val count = rng.nextInt(2, 4)
            val cols = (0 until 3).shuffled(rng).take(count).sorted()
            rows += cols.map { col -> MapNode(nextId++, row, col, pickKind(row, rng)) }
        }

        rows += listOf(MapNode(nextId++, DEPTH - 1, 1, NodeKind.BOSS))

        // Каждый узел ведёт к ближайшим по колонке узлам следующего ряда,
        // и каждый узел следующего ряда обязательно кому-то доступен.
        for (i in 0 until rows.size - 1) {
            val here = rows[i]
            val there = rows[i + 1]
            here.forEach { node ->
                val sorted = there.sortedBy { kotlin.math.abs(it.col - node.col) }
                node.next += sorted.take(if (sorted.size > 1 && node.col != 1) 1 else 2).map { it.id }
            }
            there.forEach { target ->
                if (here.none { target.id in it.next }) {
                    here.minByOrNull { kotlin.math.abs(it.col - target.col) }!!.next += target.id
                }
            }
        }
        return RunMap(rows)
    }

    private fun pickKind(row: Int, rng: Random): NodeKind = when {
        row == 1 -> NodeKind.BATTLE
        else -> when (rng.nextInt(100)) {
            in 0..38 -> NodeKind.BATTLE
            in 39..59 -> NodeKind.ELITE
            in 60..74 -> NodeKind.REST
            in 75..87 -> NodeKind.SHOP
            else -> NodeKind.RECRUIT
        }
    }
}

/** Награда после узла — одна карточка выбора. */
sealed interface Reward {
    val title: String
    val description: String

    data class Recruit(val type: UnitType) : Reward {
        override val title = "Нанять: ${type.name}"
        override val description =
            "${type.hint}. HP ${type.maxHp}, атака ${type.attack}, дальность ${type.range}, ход ${type.move}"
    }

    data class Trophy(val relic: Relic) : Reward {
        override val title = "Реликвия: ${relic.title}"
        override val description = relic.description
    }

    data object PartyAttack : Reward {
        override val title = "Точильный камень"
        override val description = "+2 к атаке всему отряду"
    }

    data object PartyHealth : Reward {
        override val title = "Походный рацион"
        override val description = "+6 к максимальному здоровью всему отряду"
    }

    data object FullHeal : Reward {
        override val title = "Целебный отвар"
        override val description = "Полностью восстанавливает здоровье отряда"
    }
}

/** Товар в лавке. Цена в золоте, заработанном за бои. */
sealed interface ShopOffer {
    val title: String
    val description: String
    val price: Int

    data class Trinket(val relic: Relic) : ShopOffer {
        override val title = relic.title
        override val description = relic.description
        override val price = relic.price
    }

    data class Hire(val type: UnitType) : ShopOffer {
        override val title = "Нанять: ${type.name}"
        override val description =
            "${type.hint}. HP ${type.maxHp}, атака ${type.attack}, дальность ${type.range}"
        override val price = 45
    }

    data object Mend : ShopOffer {
        override val title = "Лекарь"
        override val description = "Полностью лечит отряд"
        override val price = 30
    }

    data object Whetstone : ShopOffer {
        override val title = "Заточка"
        override val description = "+2 к атаке всему отряду"
        override val price = 40
    }
}

object Encounters {
    const val MAX_PARTY = 5

    fun foesFor(kind: NodeKind, row: Int, rng: Random): List<Pair<UnitType, Int>> {
        val bonus = row * 3
        return when (kind) {
            NodeKind.BOSS ->
                listOf(Roster.DEVOURER to bonus) +
                    List(2) { Roster.commonFoes.random(rng) to bonus }

            NodeKind.ELITE ->
                List(2 + row / 3) { Roster.eliteFoes.random(rng) to bonus + 4 }

            else ->
                List(2 + row / 2) { Roster.commonFoes.random(rng) to bonus }
        }
    }

    fun goldFor(kind: NodeKind, row: Int, relics: Set<Relic>): Int {
        val base = when (kind) {
            NodeKind.BOSS -> 60
            NodeKind.ELITE -> 35
            else -> 20
        }
        return base + row * 3 + if (Relic.COIN in relics) 8 else 0
    }

    fun rewards(
        partySize: Int,
        unlocked: Set<String>,
        owned: Set<Relic>,
        rng: Random,
    ): List<Reward> {
        val pool = mutableListOf<Reward>(Reward.PartyAttack, Reward.PartyHealth, Reward.FullHeal)
        val hires = Roster.recruitable.filter { it.id in unlocked }
        if (partySize < MAX_PARTY && hires.isNotEmpty()) {
            pool += Reward.Recruit(hires.random(rng))
        }
        (Relic.entries - owned).takeIf { it.isNotEmpty() }?.let {
            pool += Reward.Trophy(it.random(rng))
        }
        return pool.shuffled(rng).take(3)
    }

    fun recruitOffer(unlocked: Set<String>, rng: Random): List<Reward> =
        Roster.recruitable.filter { it.id in unlocked }.shuffled(rng).take(3).map { Reward.Recruit(it) }

    fun shopOffers(
        partySize: Int,
        unlocked: Set<String>,
        owned: Set<Relic>,
        rng: Random,
    ): List<ShopOffer> {
        val offers = mutableListOf<ShopOffer>()
        (Relic.entries - owned).shuffled(rng).take(2).forEach { offers += ShopOffer.Trinket(it) }
        val hires = Roster.recruitable.filter { it.id in unlocked }
        if (partySize < MAX_PARTY && hires.isNotEmpty()) offers += ShopOffer.Hire(hires.random(rng))
        offers += ShopOffer.Mend
        offers += ShopOffer.Whetstone
        return offers
    }
}

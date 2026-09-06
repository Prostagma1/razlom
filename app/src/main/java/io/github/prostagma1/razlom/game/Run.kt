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
            in 0..44 -> NodeKind.BATTLE
            in 45..66 -> NodeKind.ELITE
            in 67..84 -> NodeKind.REST
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

    fun rewards(partySize: Int, rng: Random): List<Reward> {
        val pool = mutableListOf<Reward>(Reward.PartyAttack, Reward.PartyHealth, Reward.FullHeal)
        if (partySize < MAX_PARTY) {
            pool += Reward.Recruit(Roster.recruitable.random(rng))
            pool += Reward.Recruit(Roster.recruitable.random(rng))
        }
        return pool.shuffled(rng).take(3)
    }

    fun recruitOffer(rng: Random): List<Reward> =
        Roster.recruitable.shuffled(rng).take(3).map { Reward.Recruit(it) }
}

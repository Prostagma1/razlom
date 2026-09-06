package io.github.prostagma1.razlom.game

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlin.math.abs

data class Pos(val x: Int, val y: Int) {
    fun dist(other: Pos): Int = abs(x - other.x) + abs(y - other.y)
}

enum class Team { PLAYER, ENEMY }

/** Пассивная особенность юнита, меняющая правила его атаки. */
enum class Ability {
    NONE,

    /** Вместо урона восстанавливает здоровье союзнику. */
    HEAL,

    /** Половина урона расходится по соседям цели. */
    SPLASH,

    /** +50% урона, если рядом с целью стоит ещё один союзник атакующего. */
    FLANK,

    /** Задевает того, кто стоит за целью на той же линии. */
    PIERCE,
}

data class UnitType(
    val id: String,
    val name: String,
    val glyph: String,
    val maxHp: Int,
    val attack: Int,
    val range: Int,
    val move: Int,
    val speed: Int,
    val ability: Ability = Ability.NONE,
    val hint: String,
)

object Roster {
    val LATNIK = UnitType("latnik", "Латник", "Л", 34, 8, 1, 3, 4, Ability.NONE, "Толстый, бьёт вплотную")
    val KOPEYSHCHIK = UnitType("kopye", "Копейщик", "К", 26, 7, 2, 3, 5, Ability.PIERCE, "Достаёт через клетку и пробивает насквозь")
    val LUCHNIK = UnitType("luchnik", "Лучник", "Ц", 18, 6, 4, 2, 6, Ability.NONE, "Стреляет далеко, умирает быстро")
    val MAG = UnitType("mag", "Маг", "М", 16, 9, 3, 2, 3, Ability.SPLASH, "Задевает всех рядом с целью")
    val ZNAHAR = UnitType("znahar", "Знахарь", "З", 20, 9, 3, 3, 7, Ability.HEAL, "Лечит союзников вместо атаки")
    val RAZBOYNIK = UnitType("razboy", "Разбойник", "Р", 22, 7, 1, 5, 8, Ability.FLANK, "Быстрый; бьёт сильнее в паре")

    /** Из кого можно набирать отряд. */
    val recruitable = listOf(LATNIK, KOPEYSHCHIK, LUCHNIK, MAG, ZNAHAR, RAZBOYNIK)

    /** Стартовый отряд. */
    val starting = listOf(LATNIK, LUCHNIK, ZNAHAR)

    val GHOUL = UnitType("ghoul", "Упырь", "у", 20, 6, 1, 4, 5, Ability.NONE, "")
    val BONE_ARCHER = UnitType("bonearcher", "Костяной стрелок", "с", 14, 5, 4, 2, 6, Ability.NONE, "")
    val MARAUDER = UnitType("marauder", "Мародёр", "м", 26, 7, 1, 3, 4, Ability.NONE, "")
    val SPITTER = UnitType("spitter", "Плевун", "п", 16, 7, 3, 2, 3, Ability.SPLASH, "")
    val HOWLER = UnitType("howler", "Вопящий", "в", 22, 6, 2, 4, 7, Ability.FLANK, "")
    val DEVOURER = UnitType("devourer", "Пожиратель", "П", 90, 12, 2, 3, 5, Ability.SPLASH, "")

    val commonFoes = listOf(GHOUL, BONE_ARCHER, MARAUDER)
    val eliteFoes = listOf(MARAUDER, SPITTER, HOWLER, BONE_ARCHER)
}

/** Боец на поле боя. Живёт ровно один бой. */
class Combatant(
    val id: Int,
    val type: UnitType,
    val team: Team,
    val maxHp: Int,
    val attack: Int,
    hp: Int,
    pos: Pos,
    /** Ссылка на героя отряда, чтобы перенести здоровье обратно после боя. */
    val heroUid: Int? = null,
) {
    var hp by mutableIntStateOf(hp)
    var pos by mutableStateOf(pos)

    val alive: Boolean get() = hp > 0
    val speed: Int get() = type.speed
}

package io.github.prostagma1.razlom.game

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlin.math.abs

data class Pos(val x: Int, val y: Int) {
    fun dist(other: Pos): Int = abs(x - other.x) + abs(y - other.y)
}

enum class Team { PLAYER, ENEMY }

/** Пассивная особенность юнита, меняющая правила его обычной атаки. */
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

/** Временные состояния бойца. Считаются в ходах владельца. */
enum class Status(val label: String, val glyph: String) {
    /** Урон в начале своего хода. */
    POISON("Яд", "☠"),

    /** Ход пропускается. */
    STUN("Оглушение", "✷"),
}

enum class SkillTarget { SELF, ALLY, ENEMY }

enum class SkillKind {
    /** Щит себе и соседним союзникам. */
    GUARD,

    /** Удар с оглушением. */
    TRIP,

    /** Усиленный выстрел. */
    VOLLEY,

    /** Урон по цели и всем врагам вокруг неё. */
    FIRESTORM,

    /** Сильное лечение, снимающее яд и оглушение. */
    TONIC,

    /** Удар с ядом. */
    POISON_BLADE,

    /** Плевок ядом. */
    SPIT,

    /** Оглушающий вой. */
    HOWL,

    /** Удар босса: урон по площади с оглушением. */
    RIFT,
}

data class ActiveSkill(
    val kind: SkillKind,
    val name: String,
    val cooldown: Int,
    val range: Int,
    val target: SkillTarget,
    val description: String,
)

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
    val active: ActiveSkill? = null,
    val hint: String = "",
)

object Roster {
    val LATNIK = UnitType(
        "latnik", "Латник", "Л", 34, 8, 1, 3, 4, Ability.NONE,
        ActiveSkill(SkillKind.GUARD, "Стена", 3, 0, SkillTarget.SELF, "Щит 10 себе и соседним союзникам"),
        "Толстый, бьёт вплотную",
    )
    val KOPEYSHCHIK = UnitType(
        "kopye", "Копейщик", "К", 26, 7, 2, 3, 5, Ability.PIERCE,
        ActiveSkill(SkillKind.TRIP, "Подсечка", 3, 2, SkillTarget.ENEMY, "Удар и оглушение на ход"),
        "Достаёт через клетку и пробивает насквозь",
    )
    val LUCHNIK = UnitType(
        "luchnik", "Лучник", "Ц", 18, 6, 4, 2, 6, Ability.NONE,
        ActiveSkill(SkillKind.VOLLEY, "Прицельный", 2, 4, SkillTarget.ENEMY, "Выстрел на 180% урона"),
        "Стреляет далеко, умирает быстро",
    )
    val MAG = UnitType(
        "mag", "Маг", "М", 16, 9, 3, 2, 3, Ability.SPLASH,
        ActiveSkill(SkillKind.FIRESTORM, "Вихрь", 3, 3, SkillTarget.ENEMY, "Полный урон цели и всем врагам рядом с ней"),
        "Задевает всех рядом с целью",
    )
    val ZNAHAR = UnitType(
        "znahar", "Знахарь", "З", 20, 9, 3, 3, 7, Ability.HEAL,
        ActiveSkill(SkillKind.TONIC, "Отвар", 2, 3, SkillTarget.ALLY, "Лечит на 150% и снимает яд с оглушением"),
        "Лечит союзников вместо атаки",
    )
    val RAZBOYNIK = UnitType(
        "razboy", "Разбойник", "Р", 22, 7, 1, 5, 8, Ability.FLANK,
        ActiveSkill(SkillKind.POISON_BLADE, "Яд на клинок", 2, 1, SkillTarget.ENEMY, "Удар и яд на три хода"),
        "Быстрый; бьёт сильнее в паре",
    )

    /** Все бойцы, которых в принципе можно нанять. */
    val recruitable = listOf(LATNIK, KOPEYSHCHIK, LUCHNIK, MAG, ZNAHAR, RAZBOYNIK)

    fun byId(id: String): UnitType? = recruitable.firstOrNull { it.id == id }

    val GHOUL = UnitType("ghoul", "Упырь", "у", 20, 6, 1, 4, 5)
    val BONE_ARCHER = UnitType("bonearcher", "Костяной стрелок", "с", 14, 5, 4, 2, 6)
    val MARAUDER = UnitType("marauder", "Мародёр", "м", 26, 7, 1, 3, 4)
    val SPITTER = UnitType(
        "spitter", "Плевун", "п", 16, 7, 3, 2, 3, Ability.SPLASH,
        ActiveSkill(SkillKind.SPIT, "Едкий плевок", 3, 3, SkillTarget.ENEMY, "Урон и яд"),
    )
    val HOWLER = UnitType(
        "howler", "Вопящий", "в", 22, 6, 2, 4, 7, Ability.FLANK,
        ActiveSkill(SkillKind.HOWL, "Вой", 3, 2, SkillTarget.ENEMY, "Оглушение"),
    )
    val DEVOURER = UnitType(
        "devourer", "Пожиратель", "П", 90, 12, 2, 3, 5, Ability.SPLASH,
        ActiveSkill(SkillKind.RIFT, "Разлом", 4, 2, SkillTarget.ENEMY, "Урон по площади и оглушение"),
    )

    val commonFoes = listOf(GHOUL, BONE_ARCHER, MARAUDER)
    val eliteFoes = listOf(MARAUDER, SPITTER, HOWLER, BONE_ARCHER)

    /** Все типы, включая врагов — для восстановления боя из сохранения. */
    val all = recruitable + listOf(GHOUL, BONE_ARCHER, MARAUDER, SPITTER, HOWLER, DEVOURER)

    fun anyById(id: String): UnitType? = all.firstOrNull { it.id == id }
}

/** Боец на поле боя. Живёт ровно один бой. */
class Combatant(
    val id: Int,
    val type: UnitType,
    val team: Team,
    val maxHp: Int,
    baseAttack: Int,
    hp: Int,
    pos: Pos,
    /** Ссылка на героя отряда, чтобы перенести здоровье обратно после боя. */
    val heroUid: Int? = null,
) {
    var hp by mutableIntStateOf(hp)
    var pos by mutableStateOf(pos)

    /** Поглощает урон до того, как он дойдёт до здоровья. */
    var shield by mutableIntStateOf(0)

    /** Прибавка к урону, набранная за бой (реликвии, эффекты). */
    var battleAtk by mutableIntStateOf(0)

    /** Сколько ходов осталось до готовности способности. */
    var cooldown by mutableIntStateOf(0)

    /** Статус и сколько ходов он ещё продержится. */
    val statuses = mutableStateMapOf<Status, Int>()

    private val baseAttack = baseAttack

    val attack: Int get() = baseAttack + battleAtk
    val alive: Boolean get() = hp > 0
    val speed: Int get() = type.speed
    val skill: ActiveSkill? get() = type.active
    val skillReady: Boolean get() = type.active != null && cooldown == 0

    fun has(status: Status) = (statuses[status] ?: 0) > 0

    fun apply(status: Status, turns: Int) {
        statuses[status] = maxOf(statuses[status] ?: 0, turns)
    }

    fun clearStatuses() = statuses.clear()
}

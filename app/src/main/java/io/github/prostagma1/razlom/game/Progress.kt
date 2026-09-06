package io.github.prostagma1.razlom.game

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue

/** Готовый стартовый состав. Открывается за глубину прошлых забегов. */
data class Squad(
    val id: String,
    val name: String,
    val members: List<UnitType>,
    val requiredRow: Int,
    val hint: String,
)

object Squads {
    val ZASTAVA = Squad(
        "zastava", "Застава",
        listOf(Roster.LATNIK, Roster.LUCHNIK, Roster.ZNAHAR), 0,
        "Ровный состав: есть кому держать удар и кому лечить",
    )
    val STROY = Squad(
        "stroy", "Строй",
        listOf(Roster.LATNIK, Roster.KOPEYSHCHIK, Roster.KOPEYSHCHIK), 3,
        "Копья бьют через голову латника и пробивают насквозь",
    )
    val LOVCHIE = Squad(
        "lovchie", "Ловчие",
        listOf(Roster.RAZBOYNIK, Roster.LUCHNIK, Roster.LUCHNIK), 5,
        "Быстрые и хрупкие: убивайте раньше, чем дойдут",
    )
    val KOSTER = Squad(
        "koster", "Костёр",
        listOf(Roster.LATNIK, Roster.MAG, Roster.ZNAHAR), 6,
        "Маг выжигает толпу из-за спины латника",
    )

    val all = listOf(ZASTAVA, STROY, LOVCHIE, KOSTER)

    fun byId(id: String): Squad? = all.firstOrNull { it.id == id }
}

/** Какой класс открывается на какой глубине. */
object Unlocks {
    private val gates = mapOf(
        Roster.KOPEYSHCHIK.id to 2,
        Roster.RAZBOYNIK.id to 4,
        Roster.MAG.id to 5,
    )

    private val always = setOf(Roster.LATNIK.id, Roster.LUCHNIK.id, Roster.ZNAHAR.id)

    fun unitsFor(bestRow: Int): Set<String> =
        always + gates.filterValues { bestRow >= it }.keys

    /** Что открылось ровно на этой глубине — чтобы показать на экране итогов. */
    fun openedAt(row: Int): List<String> {
        val units = gates.filterValues { it == row }.keys.mapNotNull { Roster.byId(it)?.name }
        val squads = Squads.all.filter { it.requiredRow == row }.map { "состав «${it.name}»" }
        return units + squads
    }
}

/**
 * Что игрок накопил за все забеги. Живёт дольше одного прохождения
 * и определяет доступные классы и стартовые составы.
 */
class Profile(bestRow: Int = 0, runsPlayed: Int = 0, runsWon: Int = 0) {
    var bestRow by mutableIntStateOf(bestRow)
        private set
    var runsPlayed by mutableIntStateOf(runsPlayed)
        private set
    var runsWon by mutableIntStateOf(runsWon)
        private set

    val unlockedUnits: Set<String> get() = Unlocks.unitsFor(bestRow)
    val squads: List<Squad> get() = Squads.all.filter { bestRow >= it.requiredRow }

    internal fun restore(bestRow: Int, runsPlayed: Int, runsWon: Int) {
        this.bestRow = bestRow
        this.runsPlayed = runsPlayed
        this.runsWon = runsWon
    }

    fun startRun() {
        runsPlayed++
    }

    /**
     * Записывает итог забега и возвращает то, что открылось благодаря ему.
     * Глубина считается по номеру ряда, до которого дошёл отряд.
     */
    fun finishRun(reachedRow: Int, won: Boolean): List<String> {
        if (won) runsWon++
        if (reachedRow <= bestRow) return emptyList()
        val opened = ((bestRow + 1)..reachedRow).flatMap { Unlocks.openedAt(it) }
        bestRow = reachedRow
        return opened
    }
}

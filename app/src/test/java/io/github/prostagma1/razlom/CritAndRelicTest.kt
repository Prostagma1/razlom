package io.github.prostagma1.razlom

import io.github.prostagma1.razlom.game.BattleState
import io.github.prostagma1.razlom.game.Combatant
import io.github.prostagma1.razlom.game.Dice
import io.github.prostagma1.razlom.game.Game
import io.github.prostagma1.razlom.game.Hero
import io.github.prostagma1.razlom.game.MemoryStorage
import io.github.prostagma1.razlom.game.Pos
import io.github.prostagma1.razlom.game.Relic
import io.github.prostagma1.razlom.game.RollRules
import io.github.prostagma1.razlom.game.Roster
import io.github.prostagma1.razlom.game.Screen
import io.github.prostagma1.razlom.game.Squads
import io.github.prostagma1.razlom.game.Status
import io.github.prostagma1.razlom.game.Team
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.random.Random

class CritAndRelicTest {

    private fun latnik() = Combatant(
        0, Roster.LATNIK, Team.PLAYER,
        Roster.LATNIK.hp.nominal, Roster.LATNIK.damage, Roster.LATNIK.hp.nominal, Pos(1, 1),
    )

    private fun dummy(hp: Int = 200) =
        Combatant(1, Roster.SPITTER, Team.ENEMY, hp, Dice.flat(7), hp, Pos(1, 2))

    private fun arena(seed: Int, relics: Set<Relic> = emptySet()): Pair<BattleState, Combatant> {
        val foe = dummy()
        return BattleState(6, 6, listOf(latnik(), foe), emptyMap(), relics, rng = Random(seed)) to foe
    }

    /** Ищем зерно, на котором латник выбросит обе четвёрки. */
    private fun critSeed(relics: Set<Relic> = emptySet()): Int =
        (0 until 5000).first { seed ->
            val (battle, foe) = arena(seed, relics)
            battle.act(foe)
            battle.lastRoll!!.crit
        }

    @Test
    fun `крит — все грани на максимуме, и он бьёт вдвое`() {
        val seed = critSeed()
        val (battle, foe) = arena(seed)
        battle.act(foe)

        val roll = battle.lastRoll!!
        assertTrue(roll.roll.faces.all { it == 4 })
        assertEquals("2к4+3 на максимуме — 11, крит вдвое", 22, 200 - foe.hp)
        assertTrue(battle.log.any { "КРИТ" in it })
    }

    @Test
    fun `без всех максимумов крита нет`() {
        repeat(200) { seed ->
            val (battle, foe) = arena(seed)
            battle.act(foe)
            val roll = battle.lastRoll!!
            if (!roll.roll.faces.all { it == 4 }) {
                assertFalse(roll.crit)
                assertEquals(roll.roll.total, 200 - foe.hp)
            }
        }
    }

    @Test
    fun `шанс крита в предпросмотре честный`() {
        val plain = arena(1).let { (b, foe) -> b.forecast(foe)!! }
        assertEquals("две к4 обе на четвёрке — 1 из 16", 1.0 / 16, plain.critChance, 1e-9)
        assertEquals(22, plain.critAmount)
        // 11 выпадает только на двух четвёрках, а это всегда крит — обычный удар 5…10.
        assertEquals("обычный разброс без крита", 5..10, plain.plainMin..plain.plainMax)

        // С переброской единиц четвёрка на одной кости выпадает в 5/16 случаев.
        val lucky = arena(1, setOf(Relic.LUCKY_BONES)).let { (b, foe) -> b.forecast(foe)!! }
        assertEquals((5.0 / 16) * (5.0 / 16), lucky.critChance, 1e-9)
    }

    @Test
    fun `зазубренный клинок бьёт втрое`() {
        val jagged = setOf(Relic.JAGGED)
        val f = arena(1, jagged).let { (b, foe) -> b.forecast(foe)!! }
        assertEquals(33, f.critAmount)

        val (battle, foe) = arena(critSeed(jagged), jagged)
        battle.act(foe)
        assertEquals(33, 200 - foe.hp)
    }

    @Test
    fun `счастливые кости перебрасывают единицы`() {
        val rng = Random(11)
        val dice = Dice(1, 4, 0)
        val rolls = List(20000) { dice.roll(rng, RollRules(rerollOnes = true)) }
        val ones = rolls.count { it.faces[0] == 1 } / 20000.0
        assertTrue("единиц должно быть 1/16, а не 1/4: вышло $ones", abs(ones - 1.0 / 16) < 0.01)
        assertTrue("переброшенные кости отмечены", rolls.any { 0 in it.rerolled })
        assertTrue(rolls.first { 0 in it.rerolled }.describe().contains("↻"))
    }

    @Test
    fun `переброска работает только на отряд`() {
        val foeFirst = Combatant(
            0, Roster.GHOUL, Team.ENEMY, 20, Roster.GHOUL.damage, 20, Pos(1, 1),
        )
        val me = Combatant(1, Roster.LATNIK, Team.PLAYER, 200, Dice.flat(1), 200, Pos(1, 2))
        val battle = BattleState(
            6, 6, listOf(foeFirst, me), emptyMap(), setOf(Relic.LUCKY_BONES, Relic.JAGGED),
            rng = Random(1),
        )
        val f = battle.forecast(me)!!
        assertEquals("упырь бросает 1к6 без переброски: крит 1 из 6", 1.0 / 6, f.critChance, 1e-9)
        assertEquals("и крит у него обычный, вдвое", (6 + 2) * 2, f.critAmount)
    }

    @Test
    fun `благословенные кости дают здоровье не хуже`() {
        val plain = List(3000) { Hero.recruit(it, Roster.LATNIK, Random(it)).maxHp }.average()
        val blessed = List(3000) { Hero.recruit(it, Roster.LATNIK, Random(it), twice = true).maxHp }.average()
        assertTrue("лучшее из двух в среднем выше: $blessed против $plain", blessed > plain + 1)

        val range = Roster.LATNIK.hp.min..Roster.LATNIK.hp.max
        assertTrue(List(500) { Hero.recruit(it, Roster.LATNIK, Random(it), twice = true).maxHp }.all { it in range })
    }

    @Test
    fun `бой сохраняется после каждого хода`() {
        val storage = MemoryStorage()
        val live = Game(Random(21), storage).apply { newRun(Squads.ZASTAVA) }
        live.enterNode(live.available.first())
        val battle = live.battle!!

        // Играем несколько ходов и НЕ зовём save() руками — как будто процесс убили.
        repeat(7) {
            if (battle.outcome == null) {
                if (battle.active?.team == Team.PLAYER) battle.endTurn() else battle.aiTakeTurn()
            }
        }

        val restored = Game(Random(1), storage)
        assertTrue(restored.continueRun())
        assertEquals(Screen.BATTLE, restored.screen)
        val back = restored.battle
        assertNotNull(back)
        assertEquals("бой должен продолжиться с того же раунда", battle.round, back!!.round)
        assertEquals(battle.active?.id, back.active?.id)
        assertEquals(battle.units.map { it.hp }, back.units.map { it.hp })
        assertEquals(battle.units.map { it.pos }, back.units.map { it.pos })
    }

    /**
     * Регрессия: всплывающее число красилось в крит по «последнему броску в бою».
     * Яд, тикнувший после чужого крита, выглядел как ещё один крит.
     */
    @Test
    fun `яд после крита не считается критом`() {
        val (battle, foe) = arena(critSeed())
        battle.act(foe)
        assertTrue(battle.lastRoll!!.crit)
        assertEquals("крит задел цель ровно один раз", 1, foe.critsTaken)

        // Травим цель и крутим ходы: яд бьёт, а новых критов по ней нет.
        foe.apply(Status.POISON, 3)
        val before = foe.hp
        repeat(6) { if (battle.outcome == null) battle.endTurn() }
        assertTrue("яд должен был ударить", foe.hp < before)
        assertEquals("яд не крит", 1, foe.critsTaken)
    }

    @Test
    fun `обычный удар крит не засчитывает`() {
        repeat(100) { seed ->
            val (battle, foe) = arena(seed)
            battle.act(foe)
            assertEquals("бросок $seed", if (battle.lastRoll!!.crit) 1 else 0, foe.critsTaken)
        }
    }

    /** Регрессия: полоска держала последний бросок, пока кто-нибудь снова не бросит. */
    @Test
    fun `бросок помнит свой ход, и ходы идут дальше`() {
        val (battle, foe) = arena(1)
        battle.act(foe)
        val rolledAt = battle.lastRoll!!.turn

        // Удар завершил ход — следующий боец уже ходит.
        assertEquals(rolledAt + 1, battle.turnCount)
        repeat(3) { battle.endTurn() }
        assertTrue(
            "через несколько ходов бросок устаревает: ${battle.turnCount} против $rolledAt",
            battle.turnCount - rolledAt > 2,
        )
    }
}


package io.github.prostagma1.razlom

import io.github.prostagma1.razlom.game.BattleState
import io.github.prostagma1.razlom.game.Combatant
import io.github.prostagma1.razlom.game.Outcome
import io.github.prostagma1.razlom.game.Pos
import io.github.prostagma1.razlom.game.Roster
import io.github.prostagma1.razlom.game.Team
import io.github.prostagma1.razlom.game.Terrain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class BattleTest {

    private fun fighter(id: Int, type: io.github.prostagma1.razlom.game.UnitType, team: Team, pos: Pos) =
        Combatant(id, type, team, type.hp.nominal, type.damage, type.hp.nominal, pos)

    /** Мишень с запасом здоровья: даже крит её не добьёт, и урон виден целиком. */
    private fun target(id: Int, pos: Pos) =
        Combatant(id, Roster.SPITTER, Team.ENEMY, 200, Roster.SPITTER.damage, 200, pos)

    /** Латник (скорость 4) против Плевуна (скорость 3): первым ходит латник. */
    private fun duel(): BattleState = BattleState(
        width = 5,
        height = 5,
        combatants = listOf(
            fighter(0, Roster.LATNIK, Team.PLAYER, Pos(0, 4)),
            fighter(1, Roster.SPITTER, Team.ENEMY, Pos(4, 0)),
        ),
        terrain = emptyMap(),
    )

    @Test
    fun `ходит первым тот, у кого выше скорость`() {
        assertEquals(Team.PLAYER, duel().active?.team)
    }

    @Test
    fun `дойти можно ровно на запас хода`() {
        val battle = duel()
        val reachable = battle.reachable()
        assertTrue(Pos(0, 1) in reachable) // три шага вверх
        assertFalse(Pos(0, 0) in reachable) // четыре — уже нет
        assertFalse(battle.active!!.pos in reachable)
    }

    @Test
    fun `препятствия и бойцы не пропускают сквозь себя`() {
        val battle = BattleState(
            width = 3,
            height = 3,
            combatants = listOf(fighter(0, Roster.LATNIK, Team.PLAYER, Pos(1, 2))),
            terrain = mapOf(
                Pos(0, 1) to Terrain.ROCK,
                Pos(1, 1) to Terrain.ROCK,
                Pos(2, 1) to Terrain.ROCK,
            ),
        )
        assertTrue(battle.reachable().keys.all { it.y == 2 })
    }

    @Test
    fun `атака наносит урон и передаёт ход`() {
        repeat(100) { seed ->
            val victim = target(1, Pos(1, 2))
            val battle = BattleState(
                width = 3,
                height = 3,
                combatants = listOf(fighter(0, Roster.LATNIK, Team.PLAYER, Pos(1, 1)), victim),
                terrain = emptyMap(),
                rng = Random(seed),
            )
            battle.act(victim)

            val lost = 200 - victim.hp
            // Снято ровно столько, сколько выпало на костях (с критом — вдвое).
            assertEquals("бросок $seed", battle.lastRoll!!.amount, lost)
            assertTrue(lost in Roster.LATNIK.damage.min..Roster.LATNIK.damage.max * 2)
            assertEquals(Team.ENEMY, battle.active?.team)
        }
    }

    @Test
    fun `знахарь лечит союзника, а не бьёт врага`() {
        val healer = fighter(0, Roster.ZNAHAR, Team.PLAYER, Pos(0, 0))
        val wounded = fighter(1, Roster.LATNIK, Team.PLAYER, Pos(1, 0))
        val foe = fighter(2, Roster.GHOUL, Team.ENEMY, Pos(2, 0))
        wounded.hp = 5
        val battle = BattleState(4, 4, listOf(healer, wounded, foe), emptyMap())

        assertTrue(battle.canTarget(healer, wounded))
        assertFalse(battle.canTarget(healer, foe))

        battle.act(wounded)
        val healed = wounded.hp - 5
        assertEquals(minOf(battle.lastRoll!!.amount, wounded.maxHp - 5), healed)
    }

    @Test
    fun `маг задевает соседей цели`() {
        repeat(100) { seed ->
            val mage = fighter(0, Roster.MAG, Team.PLAYER, Pos(0, 0))
            val main = target(1, Pos(2, 0))
            val neighbour = target(2, Pos(3, 0))
            val battle = BattleState(5, 5, listOf(mage, main, neighbour), emptyMap(), rng = Random(seed))

            battle.act(main)
            val hit = 200 - main.hp
            assertEquals("бросок $seed", battle.lastRoll!!.amount, hit)
            // Кости бросаются один раз: сосед получает ровно половину того же броска.
            assertEquals("бросок $seed", hit / 2, 200 - neighbour.hp)
        }
    }

    @Test
    fun `бой заканчивается победой, когда враги кончились`() {
        val hero = fighter(0, Roster.LATNIK, Team.PLAYER, Pos(0, 0))
        val foe = fighter(1, Roster.SPITTER, Team.ENEMY, Pos(1, 0))
        foe.hp = 1
        val battle = BattleState(3, 3, listOf(hero, foe), emptyMap())

        assertNull(battle.outcome)
        battle.act(foe)
        assertEquals(Outcome.VICTORY, battle.outcome)
    }

    @Test
    fun `ИИ доходит до игрока и не зацикливается`() {
        val hero = fighter(0, Roster.LATNIK, Team.PLAYER, Pos(0, 8))
        val foe = fighter(1, Roster.GHOUL, Team.ENEMY, Pos(0, 0))
        val battle = BattleState(3, 9, listOf(hero, foe), emptyMap())

        // Игрок пропускает ходы, враг обязан дойти и добить.
        repeat(60) {
            if (battle.outcome != null) return@repeat
            if (battle.active?.team == Team.PLAYER) battle.endTurn() else battle.aiTakeTurn()
        }
        assertEquals(Outcome.DEFEAT, battle.outcome)
    }
}

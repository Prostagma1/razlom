package io.github.prostagma1.razlom

import io.github.prostagma1.razlom.game.BattleState
import io.github.prostagma1.razlom.game.Combatant
import io.github.prostagma1.razlom.game.Outcome
import io.github.prostagma1.razlom.game.Pos
import io.github.prostagma1.razlom.game.Roster
import io.github.prostagma1.razlom.game.Team
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BattleTest {

    private fun fighter(id: Int, type: io.github.prostagma1.razlom.game.UnitType, team: Team, pos: Pos) =
        Combatant(id, type, team, type.maxHp, type.attack, type.maxHp, pos)

    /** Латник (скорость 4) против Плевуна (скорость 3): первым ходит латник. */
    private fun duel(): BattleState = BattleState(
        width = 5,
        height = 5,
        combatants = listOf(
            fighter(0, Roster.LATNIK, Team.PLAYER, Pos(0, 4)),
            fighter(1, Roster.SPITTER, Team.ENEMY, Pos(4, 0)),
        ),
        obstacles = emptySet(),
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
            obstacles = setOf(Pos(0, 1), Pos(1, 1), Pos(2, 1)),
        )
        assertTrue(battle.reachable().keys.all { it.y == 2 })
    }

    @Test
    fun `атака наносит урон и передаёт ход`() {
        val battle = BattleState(
            width = 3,
            height = 3,
            combatants = listOf(
                fighter(0, Roster.LATNIK, Team.PLAYER, Pos(1, 1)),
                fighter(1, Roster.SPITTER, Team.ENEMY, Pos(1, 2)),
            ),
            obstacles = emptySet(),
        )
        val victim = battle.units.first { it.team == Team.ENEMY }
        battle.act(victim)

        assertEquals(Roster.SPITTER.maxHp - Roster.LATNIK.attack, victim.hp)
        assertEquals(Team.ENEMY, battle.active?.team)
    }

    @Test
    fun `знахарь лечит союзника, а не бьёт врага`() {
        val healer = fighter(0, Roster.ZNAHAR, Team.PLAYER, Pos(0, 0))
        val wounded = fighter(1, Roster.LATNIK, Team.PLAYER, Pos(1, 0))
        val foe = fighter(2, Roster.GHOUL, Team.ENEMY, Pos(2, 0))
        wounded.hp = 5
        val battle = BattleState(4, 4, listOf(healer, wounded, foe), emptySet())

        assertTrue(battle.canTarget(healer, wounded))
        assertFalse(battle.canTarget(healer, foe))

        battle.act(wounded)
        assertEquals(5 + Roster.ZNAHAR.attack, wounded.hp)
    }

    @Test
    fun `маг задевает соседей цели`() {
        val mage = fighter(0, Roster.MAG, Team.PLAYER, Pos(0, 0))
        val target = fighter(1, Roster.SPITTER, Team.ENEMY, Pos(2, 0))
        val neighbour = fighter(2, Roster.SPITTER, Team.ENEMY, Pos(3, 0))
        val battle = BattleState(5, 5, listOf(mage, target, neighbour), emptySet())

        battle.act(target)
        assertEquals(Roster.SPITTER.maxHp - Roster.MAG.attack, target.hp)
        assertEquals(Roster.SPITTER.maxHp - Roster.MAG.attack / 2, neighbour.hp)
    }

    @Test
    fun `бой заканчивается победой, когда враги кончились`() {
        val hero = fighter(0, Roster.LATNIK, Team.PLAYER, Pos(0, 0))
        val foe = fighter(1, Roster.SPITTER, Team.ENEMY, Pos(1, 0))
        foe.hp = 1
        val battle = BattleState(3, 3, listOf(hero, foe), emptySet())

        assertNull(battle.outcome)
        battle.act(foe)
        assertEquals(Outcome.VICTORY, battle.outcome)
    }

    @Test
    fun `ИИ доходит до игрока и не зацикливается`() {
        val hero = fighter(0, Roster.LATNIK, Team.PLAYER, Pos(0, 8))
        val foe = fighter(1, Roster.GHOUL, Team.ENEMY, Pos(0, 0))
        val battle = BattleState(3, 9, listOf(hero, foe), emptySet())

        // Игрок пропускает ходы, враг обязан дойти и добить.
        repeat(60) {
            if (battle.outcome != null) return@repeat
            if (battle.active?.team == Team.PLAYER) battle.endTurn() else battle.aiTakeTurn()
        }
        assertEquals(Outcome.DEFEAT, battle.outcome)
    }
}

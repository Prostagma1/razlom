package io.github.prostagma1.razlom

import io.github.prostagma1.razlom.game.Ability
import io.github.prostagma1.razlom.game.BattleState
import io.github.prostagma1.razlom.game.Combatant
import io.github.prostagma1.razlom.game.Pos
import io.github.prostagma1.razlom.game.Roster
import io.github.prostagma1.razlom.game.SkillKind
import io.github.prostagma1.razlom.game.SkillTarget
import io.github.prostagma1.razlom.game.Team
import io.github.prostagma1.razlom.game.UnitType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ForecastAndUndoTest {

    private fun hero(type: UnitType, pos: Pos) =
        Combatant(0, type, Team.PLAYER, type.maxHp, type.attack, type.maxHp, pos)

    /** Мешок для битья: живучий и медленный, чтобы ходил всегда наш боец. */
    private fun dummy(id: Int, team: Team, pos: Pos) =
        Combatant(id, Roster.SPITTER, team, 200, 7, 200, pos)

    @Test
    fun `предпросмотр обычного удара совпадает с настоящим`() {
        Roster.recruitable.forEach { type ->
            val me = hero(type, Pos(1, 1))
            val ally = dummy(1, Team.PLAYER, Pos(2, 1)).also { it.hp = 40 }
            val foe = dummy(2, Team.ENEMY, Pos(1, 2))
            val battle = BattleState(6, 6, listOf(me, ally, foe), emptyMap())

            assertEquals("ходить должен ${type.name}", me.id, battle.active?.id)

            val target = if (type.ability == Ability.HEAL) ally else foe
            val forecast = battle.forecast(target)
            assertNotNull("нет предпросмотра для ${type.name}", forecast)

            battle.act(target)
            assertEquals(
                "${type.name}: предпросмотр обещал ${forecast!!.remaining}",
                forecast.remaining,
                target.hp,
            )
        }
    }

    @Test
    fun `предпросмотр способности совпадает с настоящей`() {
        Roster.recruitable.forEach { type ->
            val skill = type.active ?: return@forEach
            if (skill.kind == SkillKind.GUARD) return@forEach // щит, а не удар

            val me = hero(type, Pos(1, 1))
            val ally = dummy(1, Team.PLAYER, Pos(2, 1)).also { it.hp = 40 }
            val foe = dummy(2, Team.ENEMY, Pos(1, 2))
            val battle = BattleState(6, 6, listOf(me, ally, foe), emptyMap())

            val target = if (skill.target == SkillTarget.ALLY) ally else foe
            assertTrue("${type.name}: цель недоступна", target in battle.skillTargets())

            val forecast = battle.forecast(target, withSkill = true)!!
            battle.useSkill(target)

            assertEquals(
                "${type.name} (${skill.name}): предпросмотр обещал ${forecast.remaining}",
                forecast.remaining,
                target.hp,
            )
        }
    }

    @Test
    fun `предпросмотр учитывает щит цели`() {
        val me = hero(Roster.LATNIK, Pos(1, 1))
        val foe = dummy(1, Team.ENEMY, Pos(1, 2)).also { it.shield = 5 }
        val battle = BattleState(6, 6, listOf(me, foe), emptyMap())

        val forecast = battle.forecast(foe)!!
        assertEquals("щит должен съесть пять", 5, forecast.absorbed)
        assertEquals(Roster.LATNIK.attack - 5, forecast.amount)

        battle.act(foe)
        assertEquals(forecast.remaining, foe.hp)
        assertEquals(0, foe.shield)
    }

    @Test
    fun `смертельный удар помечен как смертельный`() {
        val me = hero(Roster.LATNIK, Pos(1, 1))
        val foe = dummy(1, Team.ENEMY, Pos(1, 2)).also { it.hp = 3 }
        val battle = BattleState(6, 6, listOf(me, foe), emptyMap())

        val forecast = battle.forecast(foe)!!
        assertTrue("удар на ${Roster.LATNIK.attack} по трём HP обязан быть смертельным", forecast.lethal)
        assertEquals(0, forecast.remaining)
    }

    @Test
    fun `перемещение можно отменить, пока не ударил`() {
        val me = hero(Roster.LATNIK, Pos(1, 1))
        val foe = dummy(1, Team.ENEMY, Pos(5, 5))
        val battle = BattleState(6, 6, listOf(me, foe), emptyMap())

        val start = me.pos
        val moves = battle.movesLeft
        assertFalse("отменять пока нечего", battle.canUndoMove)

        val step = battle.reachable().keys.first { it != start }
        battle.moveActiveTo(step)
        assertTrue(battle.canUndoMove)
        assertTrue("шаги должны были потратиться", battle.movesLeft < moves)

        battle.undoMove()
        assertEquals(start, me.pos)
        assertEquals(moves, battle.movesLeft)
        assertFalse(battle.canUndoMove)
    }

    @Test
    fun `после удара отменять уже нечего`() {
        val me = hero(Roster.LATNIK, Pos(1, 1))
        val foe = dummy(1, Team.ENEMY, Pos(1, 2))
        val battle = BattleState(6, 6, listOf(me, foe), emptyMap())

        battle.moveActiveTo(battle.reachable().keys.first { it.reach(foe.pos) <= 1 })
        battle.act(foe)

        // Ход ушёл к следующему бойцу — отменять чужое перемещение нельзя.
        assertFalse(battle.canUndoMove)
    }
}

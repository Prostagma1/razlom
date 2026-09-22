package io.github.prostagma1.razlom

import io.github.prostagma1.razlom.game.Ability
import io.github.prostagma1.razlom.game.BattleState
import io.github.prostagma1.razlom.game.Combatant
import io.github.prostagma1.razlom.game.Dice
import io.github.prostagma1.razlom.game.Pos
import io.github.prostagma1.razlom.game.Roster
import io.github.prostagma1.razlom.game.SkillKind
import io.github.prostagma1.razlom.game.SkillTarget
import io.github.prostagma1.razlom.game.Team
import io.github.prostagma1.razlom.game.UnitType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.random.Random

class ForecastAndUndoTest {

    private fun hero(type: UnitType, pos: Pos) =
        Combatant(0, type, Team.PLAYER, type.hp.nominal, type.damage, type.hp.nominal, pos)

    /** Мешок для битья: живучий и медленный, чтобы ходил всегда наш боец. */
    private fun dummy(id: Int, team: Team, pos: Pos, hp: Int = 200) =
        Combatant(id, Roster.SPITTER, team, hp, Dice.flat(7), hp, pos)

    private fun arena(me: Combatant, vararg others: Combatant, seed: Int) =
        BattleState(6, 6, listOf(me, *others), emptyMap(), rng = Random(seed))

    @Test
    fun `разброс предпросмотра вмещает настоящий удар`() {
        Roster.recruitable.forEach { type ->
            repeat(30) { seed ->
                val me = hero(type, Pos(1, 1))
                val ally = dummy(1, Team.PLAYER, Pos(2, 1)).also { it.hp = 40 }
                val foe = dummy(2, Team.ENEMY, Pos(1, 2))
                val battle = arena(me, ally, foe, seed = seed)
                assertEquals("ходить должен ${type.name}", me.id, battle.active?.id)

                val target = if (type.ability == Ability.HEAL) ally else foe
                val f = battle.forecast(target)!!
                battle.act(target)

                assertTrue(
                    "${type.name}, бросок $seed: ${target.hp} вне ${f.minRemaining}..${f.maxRemaining}",
                    target.hp in f.minRemaining..f.maxRemaining,
                )
            }
        }
    }

    @Test
    fun `разброс способности вмещает настоящую`() {
        Roster.recruitable.forEach { type ->
            val skill = type.active ?: return@forEach
            if (skill.kind == SkillKind.GUARD) return@forEach // щит, а не удар

            repeat(30) { seed ->
                val me = hero(type, Pos(1, 1))
                val ally = dummy(1, Team.PLAYER, Pos(2, 1)).also { it.hp = 40 }
                val foe = dummy(2, Team.ENEMY, Pos(1, 2))
                val battle = arena(me, ally, foe, seed = seed)

                val target = if (skill.target == SkillTarget.ALLY) ally else foe
                val f = battle.forecast(target, withSkill = true)!!
                battle.useSkill(target)

                assertTrue(
                    "${type.name} (${skill.name}), бросок $seed: ${target.hp} вне ${f.minRemaining}..${f.maxRemaining}",
                    target.hp in f.minRemaining..f.maxRemaining,
                )
            }
        }
    }

    /**
     * Латник бьёт 2к4+3. По цели с 8 HP он убивает, если на двух к4 выпало 5+:
     * это 10 исходов из 16, ровно 62,5%. Предпросмотр обязан назвать именно это
     * число, а частота на тысячах боёв — сойтись с ним.
     */
    @Test
    fun `шанс убить — это настоящая вероятность`() {
        fun forecastAt(seed: Int): Pair<BattleState.Forecast, Boolean> {
            val me = hero(Roster.LATNIK, Pos(1, 1))
            val foe = dummy(1, Team.ENEMY, Pos(1, 2), hp = 8)
            val battle = arena(me, foe, seed = seed)
            val f = battle.forecast(foe)!!
            battle.act(foe)
            return f to !foe.alive
        }

        val (f, _) = forecastAt(0)
        assertEquals(0.625, f.lethalChance, 1e-9)
        assertFalse("62% — это не «смертельно»", f.lethal)

        val kills = (0 until 4000).count { forecastAt(it).second }
        val observed = kills / 4000.0
        assertTrue("на деле убил в ${observed * 100}% боёв", abs(observed - 0.625) < 0.03)
    }

    @Test
    fun `предпросмотр учитывает щит цели`() {
        val me = hero(Roster.LATNIK, Pos(1, 1))
        val foe = dummy(1, Team.ENEMY, Pos(1, 2)).also { it.shield = 5 }
        val battle = arena(me, foe, seed = 1)

        val f = battle.forecast(foe)!!
        assertEquals("меньше пяти латник не выбрасывает — щит съест все пять", 5, f.absorbed)
        assertEquals(Roster.LATNIK.damage.min - 5, f.minAmount)
        // Обычный удар без крита — до 10, крит на 22 щит срежет до 17.
        assertEquals(Roster.LATNIK.damage.max - 1 - 5, f.plainMax)
        assertEquals(Roster.LATNIK.damage.max * 2 - 5, f.critAmount)

        battle.act(foe)
        assertTrue(foe.hp in f.minRemaining..f.maxRemaining)
        assertEquals(0, foe.shield)
    }

    @Test
    fun `удар по почти мёртвому помечен смертельным, по толстому — нет`() {
        val weak = dummy(1, Team.ENEMY, Pos(1, 2), hp = 1)
        val sure = arena(hero(Roster.LATNIK, Pos(1, 1)), weak, seed = 1).forecast(weak)!!
        assertTrue(sure.lethal)

        val thick = dummy(1, Team.ENEMY, Pos(1, 2), hp = 200)
        val never = arena(hero(Roster.LATNIK, Pos(1, 1)), thick, seed = 1).forecast(thick)!!
        assertEquals(0.0, never.lethalChance, 0.0)
    }

    @Test
    fun `перемещение можно отменить, пока не ударил`() {
        val me = hero(Roster.LATNIK, Pos(1, 1))
        val foe = dummy(1, Team.ENEMY, Pos(5, 5))
        val battle = arena(me, foe, seed = 1)

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
        val battle = arena(me, foe, seed = 1)

        battle.moveActiveTo(battle.reachable().keys.first { it.reach(foe.pos) <= 1 })
        battle.act(foe)

        assertFalse(battle.canUndoMove)
    }
}

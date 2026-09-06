package io.github.prostagma1.razlom

import io.github.prostagma1.razlom.game.BattleState
import io.github.prostagma1.razlom.game.Combatant
import io.github.prostagma1.razlom.game.Pos
import io.github.prostagma1.razlom.game.Relic
import io.github.prostagma1.razlom.game.Roster
import io.github.prostagma1.razlom.game.Status
import io.github.prostagma1.razlom.game.Team
import io.github.prostagma1.razlom.game.UnitType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StatusAndSkillTest {

    private fun fighter(id: Int, type: UnitType, team: Team, pos: Pos) =
        Combatant(id, type, team, type.maxHp, type.attack, type.maxHp, pos)

    @Test
    fun `яд бьёт в начале хода и через три хода спадает`() {
        val hero = fighter(0, Roster.LATNIK, Team.PLAYER, Pos(0, 0))
        val foe = fighter(1, Roster.SPITTER, Team.ENEMY, Pos(4, 4))
        val battle = BattleState(6, 6, listOf(hero, foe), emptySet())

        hero.apply(Status.POISON, 2)
        val before = hero.hp

        battle.endTurn() // ход врага
        battle.endTurn() // снова ход латника: срабатывает яд

        assertTrue("яд должен был снять здоровье", hero.hp < before)
        assertEquals(1, hero.statuses[Status.POISON])
    }

    @Test
    fun `оглушённый пропускает ход`() {
        val fast = fighter(0, Roster.RAZBOYNIK, Team.PLAYER, Pos(0, 0))
        val slow = fighter(1, Roster.LATNIK, Team.PLAYER, Pos(1, 0))
        val foe = fighter(2, Roster.SPITTER, Team.ENEMY, Pos(5, 5))
        val battle = BattleState(6, 6, listOf(fast, slow, foe), emptySet())

        assertEquals(fast.id, battle.active?.id)
        slow.apply(Status.STUN, 1)
        battle.endTurn()

        // Латник должен был ходить вторым, но оглушение съело его ход.
        assertEquals(foe.id, battle.active?.id)
        assertFalse(slow.has(Status.STUN))
    }

    @Test
    fun `подсечка копейщика оглушает и уходит на перезарядку`() {
        val spear = fighter(0, Roster.KOPEYSHCHIK, Team.PLAYER, Pos(0, 0))
        val foe = fighter(1, Roster.SPITTER, Team.ENEMY, Pos(2, 0))
        val battle = BattleState(6, 6, listOf(spear, foe), emptySet())

        assertTrue(foe in battle.skillTargets())
        battle.useSkill(foe)

        assertTrue("удар должен был пройти", foe.hp < foe.maxHp)
        assertFalse("способность ушла на перезарядку", spear.skillReady)
        // Плевун был оглушён, пропустил ход, и круг сразу пошёл на второй раунд.
        assertEquals(2, battle.round)
        assertFalse(foe.has(Status.STUN))
    }

    @Test
    fun `отвар знахаря лечит и снимает яд`() {
        val healer = fighter(0, Roster.ZNAHAR, Team.PLAYER, Pos(0, 0))
        val wounded = fighter(1, Roster.LATNIK, Team.PLAYER, Pos(1, 0))
        val foe = fighter(2, Roster.SPITTER, Team.ENEMY, Pos(5, 5))
        wounded.hp = 5
        wounded.apply(Status.POISON, 3)
        val battle = BattleState(6, 6, listOf(healer, wounded, foe), emptySet())

        battle.useSkill(wounded)

        assertEquals(5 + Roster.ZNAHAR.attack * 3 / 2, wounded.hp)
        assertFalse(wounded.has(Status.POISON))
    }

    @Test
    fun `щит съедает урон раньше здоровья`() {
        val hero = fighter(0, Roster.LATNIK, Team.PLAYER, Pos(0, 0))
        val foe = fighter(1, Roster.SPITTER, Team.ENEMY, Pos(1, 0))
        hero.shield = 100
        val battle = BattleState(6, 6, listOf(hero, foe), emptySet())

        battle.act(foe) // ходит латник — он быстрее
        battle.aiTakeTurn() // плевун отвечает

        assertEquals(hero.maxHp, hero.hp)
        assertTrue("щит должен был потратиться", hero.shield < 100)
    }

    @Test
    fun `каменная кожа даёт щит только отряду игрока`() {
        val hero = fighter(0, Roster.LATNIK, Team.PLAYER, Pos(0, 0))
        val foe = fighter(1, Roster.GHOUL, Team.ENEMY, Pos(5, 5))
        BattleState(6, 6, listOf(hero, foe), emptySet(), setOf(Relic.STONE_SKIN))

        assertEquals(5, hero.shield)
        assertEquals(0, foe.shield)
    }

    @Test
    fun `оберег удерживает первого павшего на ногах`() {
        val hero = fighter(0, Roster.LUCHNIK, Team.PLAYER, Pos(0, 0))
        val foe = fighter(1, Roster.GHOUL, Team.ENEMY, Pos(1, 0))
        hero.hp = 1
        val battle = BattleState(6, 6, listOf(hero, foe), emptySet(), setOf(Relic.TALISMAN))

        battle.endTurn() // лучник быстрее, пропускаем его ход
        battle.aiTakeTurn()

        assertTrue("оберег должен был спасти", hero.alive)
        assertEquals(1, hero.hp)
    }

    @Test
    fun `сапоги гонца добавляют шаг`() {
        fun field(relics: Set<Relic>) = BattleState(
            8, 8,
            listOf(
                fighter(0, Roster.LATNIK, Team.PLAYER, Pos(0, 0)),
                fighter(1, Roster.SPITTER, Team.ENEMY, Pos(7, 7)),
            ),
            emptySet(),
            relics,
        )

        val plain = field(emptySet())
        val booted = field(setOf(Relic.BOOTS))

        assertEquals(Team.PLAYER, plain.active?.team)
        assertTrue(booted.reachable().size > plain.reachable().size)
    }
}

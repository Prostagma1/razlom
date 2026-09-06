package io.github.prostagma1.razlom

import io.github.prostagma1.razlom.game.BattleState
import io.github.prostagma1.razlom.game.Combatant
import io.github.prostagma1.razlom.game.Encounters
import io.github.prostagma1.razlom.game.NodeKind
import io.github.prostagma1.razlom.game.Pos
import io.github.prostagma1.razlom.game.Reward
import io.github.prostagma1.razlom.game.Roster
import io.github.prostagma1.razlom.game.Team
import io.github.prostagma1.razlom.game.Terrain
import io.github.prostagma1.razlom.game.UnitType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class FogAndTerrainTest {

    private fun fighter(id: Int, type: UnitType, team: Team, pos: Pos) =
        Combatant(id, type, team, type.maxHp, type.attack, type.maxHp, pos)

    /** Стена во всю ширину, кроме одного прохода справа. */
    private fun wall(y: Int, width: Int, gap: Int) =
        (0 until width).filter { it != gap }.associate { Pos(it, y) to Terrain.ROCK }

    @Test
    fun `враг обходит стену, а не утыкается в неё`() {
        val hero = fighter(0, Roster.LATNIK, Team.PLAYER, Pos(0, 6))
        val foe = fighter(1, Roster.GHOUL, Team.ENEMY, Pos(0, 0))
        val battle = BattleState(5, 7, listOf(hero, foe), wall(y = 3, width = 5, gap = 4))

        // Игрок стоит на месте: у врага нет причин не дойти.
        repeat(30) {
            if (battle.outcome == null) {
                if (battle.active?.team == Team.PLAYER) battle.endTurn() else battle.aiTakeTurn()
            }
        }

        assertTrue("враг застрял перед стеной на y=${foe.pos.y}", foe.pos.y > 3)
    }

    @Test
    fun `бить можно наискосок`() {
        val hero = fighter(0, Roster.LATNIK, Team.PLAYER, Pos(1, 1))
        val foe = fighter(1, Roster.SPITTER, Team.ENEMY, Pos(2, 2))
        val battle = BattleState(6, 6, listOf(hero, foe), emptyMap())

        assertEquals(1, hero.pos.reach(foe.pos))
        assertTrue("латник должен доставать по диагонали", battle.canTarget(hero, foe))

        battle.act(foe)
        assertTrue(foe.hp < foe.maxHp)
    }

    @Test
    fun `дерево прячет врага от отряда`() {
        fun field(terrain: Map<Pos, Terrain>) = BattleState(
            6, 9,
            listOf(
                fighter(0, Roster.LUCHNIK, Team.PLAYER, Pos(0, 0)),
                fighter(1, Roster.GHOUL, Team.ENEMY, Pos(0, 4)),
            ),
            terrain,
        )

        assertTrue("в чистом поле лучник видит на 6", field(emptyMap()).isVisible(Pos(0, 4)))
        assertFalse(
            "дерево должно обрывать луч",
            field(mapOf(Pos(0, 2) to Terrain.TREE)).isVisible(Pos(0, 4)),
        )
        // Само дерево при этом видно — иначе препятствия были бы невидимыми.
        assertTrue(field(mapOf(Pos(0, 2) to Terrain.TREE)).isVisible(Pos(0, 2)))
    }

    @Test
    fun `дальше своего обзора отряд не видит`() {
        val battle = BattleState(
            6, 9,
            listOf(
                fighter(0, Roster.LATNIK, Team.PLAYER, Pos(0, 8)), // обзор 3
                fighter(1, Roster.GHOUL, Team.ENEMY, Pos(0, 0)),
            ),
            emptyMap(),
        )
        assertTrue(battle.isVisible(Pos(0, 6)))
        assertFalse(battle.isVisible(Pos(0, 0)))
    }

    @Test
    fun `по невидимому врагу нельзя бить`() {
        val archer = fighter(0, Roster.LUCHNIK, Team.PLAYER, Pos(0, 0))
        val foe = fighter(1, Roster.GHOUL, Team.ENEMY, Pos(0, 4))
        val battle = BattleState(
            6, 9,
            listOf(archer, foe),
            mapOf(Pos(0, 2) to Terrain.TREE),
        )

        assertEquals(4, archer.pos.reach(foe.pos)) // в дальность стрельбы попадает
        assertFalse("за деревом цели не видно", battle.canTarget(archer, foe))
        assertNull(battle.skillTargets().firstOrNull { it.id == foe.id })
    }

    @Test
    fun `бурелом стоит двух шагов, камень не пройти вовсе`() {
        val hero = fighter(0, Roster.LATNIK, Team.PLAYER, Pos(0, 0))
        val foe = fighter(1, Roster.SPITTER, Team.ENEMY, Pos(5, 5))
        val battle = BattleState(
            6, 6,
            listOf(hero, foe),
            mapOf(Pos(1, 0) to Terrain.BRAMBLE, Pos(0, 1) to Terrain.ROCK),
        )

        val reach = battle.reachable()
        assertEquals("бурелом обходится дороже чистой клетки", 2, reach[Pos(1, 0)])
        assertFalse("сквозь камень не ходят", reach.containsKey(Pos(0, 1)))
        assertEquals("до дерева за буреломом остаётся один шаг", 3, reach[Pos(2, 0)])
    }

    @Test
    fun `в дереве можно спрятаться`() {
        val battle = BattleState(
            6, 6,
            listOf(
                fighter(0, Roster.RAZBOYNIK, Team.PLAYER, Pos(0, 0)),
                fighter(1, Roster.SPITTER, Team.ENEMY, Pos(5, 5)),
            ),
            mapOf(Pos(1, 0) to Terrain.TREE),
        )
        assertTrue("дерево проходимо", battle.reachable().containsKey(Pos(1, 0)))
    }

    @Test
    fun `логово платит щедрее обычной схватки`() {
        val unlocked = Roster.recruitable.map { it.id }.toSet()

        val plain = Encounters.rewards(3, unlocked, emptySet(), Random(3))
        val rich = Encounters.rewards(3, unlocked, emptySet(), Random(3), generous = true)

        assertEquals(3, plain.size)
        assertEquals(4, rich.size)
        assertTrue("за логово реликвия обязана быть", rich.any { it is Reward.Trophy })
        assertTrue(
            "и золота за логово больше",
            Encounters.goldFor(NodeKind.ELITE, 3, emptySet()) >
                Encounters.goldFor(NodeKind.BATTLE, 3, emptySet()),
        )
    }
}

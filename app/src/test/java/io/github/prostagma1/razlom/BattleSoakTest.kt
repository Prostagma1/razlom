package io.github.prostagma1.razlom

import io.github.prostagma1.razlom.game.BattleFactory
import io.github.prostagma1.razlom.game.BattleState
import io.github.prostagma1.razlom.game.Encounters
import io.github.prostagma1.razlom.game.Hero
import io.github.prostagma1.razlom.game.NodeKind
import io.github.prostagma1.razlom.game.Roster
import io.github.prostagma1.razlom.game.Team
import org.junit.Assert.assertNotNull
import org.junit.Assert.fail
import org.junit.Test
import kotlin.random.Random

/**
 * Прогон множества боёв целиком. Ловит две беды разом: «очередь встала»
 * (ходить некому, а бой не кончился) и бой, который не сходится к развязке.
 */
class BattleSoakTest {

    private fun party() = listOf(
        Hero(0, Roster.LATNIK),
        Hero(1, Roster.LUCHNIK),
        Hero(2, Roster.ZNAHAR),
    )

    private fun playPlayerTurn(battle: BattleState, rng: Random) {
        val me = battle.active ?: return

        // Иногда лупим способностью, иногда обычной атакой, иногда просто идём.
        battle.skillTargets().takeIf { it.isNotEmpty() && rng.nextInt(3) == 0 }?.let {
            battle.useSkill(it.random(rng))
            return
        }
        battle.units.filter { it.alive && it.team != me.team && battle.canTarget(me, it) }
            .takeIf { it.isNotEmpty() }
            ?.let {
                battle.act(it.random(rng))
                return
            }

        val steps = battle.reachable().keys.toList()
        if (steps.isNotEmpty() && rng.nextInt(4) != 0) battle.moveActiveTo(steps.random(rng))
        battle.endTurn()
    }

    @Test
    fun `бой всегда доигрывается и очередь не встаёт`() {
        repeat(400) { seed ->
            val rng = Random(seed)
            val kind = listOf(NodeKind.BATTLE, NodeKind.ELITE, NodeKind.BOSS)[seed % 3]
            val row = seed % 8
            val battle = BattleFactory.create(party(), Encounters.foesFor(kind, row, rng), rng)

            var turns = 0
            while (battle.outcome == null) {
                if (turns++ > 4000) fail("бой $seed ($kind, ряд $row) не сходится")

                val actor = battle.active
                if (actor == null) {
                    fail(
                        "очередь встала на бою $seed ($kind, ряд $row): ходить некому, " +
                            "живых бойцов ${battle.units.count { it.alive }}, раунд ${battle.round}",
                    )
                    return@repeat
                }

                if (actor.team == Team.PLAYER) playPlayerTurn(battle, rng) else battle.aiTakeTurn()
            }
            assertNotNull(battle.outcome)
        }
    }

    /** Отдельно: отряд стоит на месте — враг обязан сам довести бой до конца. */
    @Test
    fun `враг доводит бой до конца, даже если отряд не двигается`() {
        repeat(120) { seed ->
            val rng = Random(seed + 1000)
            val battle = BattleFactory.create(
                party(),
                Encounters.foesFor(NodeKind.BATTLE, seed % 8, rng),
                rng,
            )

            var turns = 0
            while (battle.outcome == null && turns++ < 3000) {
                val actor = battle.active
                if (actor == null) {
                    fail("очередь встала на бою $seed: ходить некому, раунд ${battle.round}")
                    return@repeat
                }
                if (actor.team == Team.PLAYER) battle.endTurn() else battle.aiTakeTurn()
            }

            assertNotNull("бой $seed завис: враг не дошёл до отряда", battle.outcome)
        }
    }
}

package io.github.prostagma1.razlom

import io.github.prostagma1.razlom.game.Game
import io.github.prostagma1.razlom.game.MemoryStorage
import io.github.prostagma1.razlom.game.Profile
import io.github.prostagma1.razlom.game.Relic
import io.github.prostagma1.razlom.game.Roster
import io.github.prostagma1.razlom.game.Screen
import io.github.prostagma1.razlom.game.Squads
import io.github.prostagma1.razlom.game.Storage
import io.github.prostagma1.razlom.game.Team
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class SaveAndProgressTest {

    private fun freshRun(storage: Storage): Game =
        Game(Random(7), storage).apply { newRun(Squads.ZASTAVA) }

    @Test
    fun `забег переживает перезапуск приложения`() {
        val storage = MemoryStorage()
        val first = freshRun(storage)
        first.enterNode(first.available.first()) // первый узел всегда бой
        first.gold = 123
        first.relics += Relic.BOOTS
        first.save()

        val battleBefore = first.battle!!
        battleBefore.reachable().keys.firstOrNull()?.let { battleBefore.moveActiveTo(it) }
        first.save()

        val restored = Game(Random(99), storage)
        assertTrue(restored.hasSavedRun)
        assertTrue(restored.continueRun())

        assertEquals(Screen.BATTLE, restored.screen)
        assertEquals(123, restored.gold)
        assertEquals(listOf(Relic.BOOTS), restored.relics.toList())
        assertEquals(first.party.map { it.type.id }, restored.party.map { it.type.id })
        assertEquals(first.map.nodes.size, restored.map.nodes.size)

        val battleAfter = restored.battle!!
        assertEquals(battleBefore.units.size, battleAfter.units.size)
        assertEquals(battleBefore.units.map { it.pos }, battleAfter.units.map { it.pos })
        assertEquals(battleBefore.units.map { it.hp }, battleAfter.units.map { it.hp })
        assertEquals(battleBefore.active?.id, battleAfter.active?.id)
        assertEquals(battleBefore.movesLeft, battleAfter.movesLeft)
        assertEquals(battleBefore.round, battleAfter.round)
    }

    @Test
    fun `после поражения сохранение стирается`() {
        val storage = MemoryStorage()
        val game = freshRun(storage)
        game.enterNode(game.available.first())

        // Добиваем свой отряд руками и подтверждаем исход.
        val battle = game.battle!!
        battle.units.filter { it.team == Team.PLAYER }.forEach { it.hp = 0 }
        battle.endTurn()
        game.resolveBattle()

        assertEquals(Screen.GAME_OVER, game.screen)
        assertFalse(game.hasSavedRun)
        assertEquals(null, storage.read(Storage.RUN))
        assertNotNull("профиль должен пережить поражение", storage.read(Storage.PROFILE))
    }

    @Test
    fun `испорченное сохранение не роняет игру`() {
        val storage = MemoryStorage()
        storage.write(Storage.RUN, "мусор\nv=999\n")
        val game = Game(Random(1), storage)

        assertFalse(game.continueRun())
        assertFalse(game.hasSavedRun)
    }

    @Test
    fun `профиль переживает перезапуск и копит забеги`() {
        val storage = MemoryStorage()
        val game = freshRun(storage)
        game.profile.finishRun(4, won = false)
        game.saveProfile()

        val restored = Game(Random(1), storage)
        assertEquals(4, restored.profile.bestRow)
        assertEquals(1, restored.profile.runsPlayed)
    }

    @Test
    fun `глубина забега открывает классы и составы`() {
        val profile = Profile()
        assertFalse(Roster.KOPEYSHCHIK.id in profile.unlockedUnits)
        assertEquals(listOf(Squads.ZASTAVA), profile.squads)

        val opened = profile.finishRun(3, won = false)

        assertTrue(Roster.KOPEYSHCHIK.id in profile.unlockedUnits)
        assertTrue(opened.any { it.contains("Копейщик") })
        assertTrue(opened.any { it.contains("Строй") })
        assertTrue(Squads.STROY in profile.squads)

        // Повторный забег той же глубины ничего не открывает.
        assertTrue(profile.finishRun(3, won = false).isEmpty())
    }
}

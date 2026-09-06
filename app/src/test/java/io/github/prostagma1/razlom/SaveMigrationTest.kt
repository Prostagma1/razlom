package io.github.prostagma1.razlom

import io.github.prostagma1.razlom.game.Game
import io.github.prostagma1.razlom.game.MemoryStorage
import io.github.prostagma1.razlom.game.Screen
import io.github.prostagma1.razlom.game.SaveCodec
import io.github.prostagma1.razlom.game.Squads
import io.github.prostagma1.razlom.game.Storage
import io.github.prostagma1.razlom.game.Terrain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class SaveMigrationTest {

    /**
     * Забег, начатый в версии 1.1, обязан открыться в 1.2: сохранение тогда
     * знало только сплошные препятствия, а не разные типы местности.
     */
    @Test
    fun `сохранение прошлой версии открывается и превращает препятствия в камни`() {
        val storage = MemoryStorage()

        // Готовим настоящее сохранение и опускаем его до старого формата.
        val original = Game(Random(11), storage).apply { newRun(Squads.ZASTAVA) }
        original.enterNode(original.available.first())
        val terrain = original.battle!!.terrain
        val fresh = SaveCodec.encodeRun(original)

        val legacy = fresh.lineSequence().map { line ->
            when {
                line.startsWith("v=") -> "v=2"
                line.startsWith("bterr=") -> "bobst=" + terrain.keys.joinToString(";") { "${it.x},${it.y}" }
                else -> line
            }
        }.joinToString("\n")
        storage.write(Storage.RUN, legacy)

        val restored = Game(Random(1), storage)
        assertTrue("старое сохранение должно читаться", restored.continueRun())
        assertEquals(Screen.BATTLE, restored.screen)

        val moved = restored.battle!!.terrain
        assertEquals(terrain.keys, moved.keys)
        assertTrue("всё старое становится камнем", moved.values.all { it == Terrain.ROCK })
    }
}

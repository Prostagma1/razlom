package io.github.prostagma1.razlom

import io.github.prostagma1.razlom.game.Dice
import io.github.prostagma1.razlom.game.Game
import io.github.prostagma1.razlom.game.Hero
import io.github.prostagma1.razlom.game.MemoryStorage
import io.github.prostagma1.razlom.game.Roster
import io.github.prostagma1.razlom.game.Squads
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.random.Random

class DiceTest {

    @Test
    fun `запись разбирается и собирается обратно`() {
        assertEquals(Dice(2, 6, 3), Dice.parse("2к6+3"))
        assertEquals(Dice(2, 6, 3), Dice.parse("2d6+3"))
        assertEquals(Dice(1, 8, -1), Dice.parse("1d8-1"))
        assertEquals(Dice.flat(7), Dice.parse("7"))
        assertNull(Dice.parse("чепуха"))

        listOf(Dice(3, 6, 23), Dice(1, 8, 1), Dice(2, 6, 0), Dice.flat(9)).forEach {
            assertEquals(it, Dice.parse(it.encode()))
        }
        assertEquals("2к6+3", Dice(2, 6, 3).toString())
        assertEquals("2к6", Dice(2, 6, 0).toString())
    }

    @Test
    fun `бросок всегда в пределах костей`() {
        val rng = Random(1)
        val dice = Dice(3, 6, 2)
        repeat(2000) {
            val roll = dice.roll(rng)
            assertEquals(3, roll.faces.size)
            assertTrue(roll.faces.all { it in 1..6 })
            assertTrue(roll.total in dice.min..dice.max)
        }
    }

    @Test
    fun `распределение честное`() {
        val dist = Dice(2, 6, 0).distribution()
        assertEquals(1.0, dist.values.sum(), 1e-9)
        assertEquals(6.0 / 36, dist.getValue(7), 1e-9) // самая частая сумма двух к6
        assertEquals(1.0 / 36, dist.getValue(2), 1e-9)
        assertEquals(2..12, dist.keys.min()..dist.keys.max())

        // И совпадает с тем, что выпадает на деле.
        val rng = Random(7)
        val mean = (0 until 20000).sumOf { Dice(2, 6, 0).roll(rng).total } / 20000.0
        assertTrue("среднее $mean", abs(mean - 7.0) < 0.1)
    }

    /**
     * Кости заменили ровные числа, но баланс сдвигаться не должен: среднее
     * каждой кости равно прежнему значению. Если кто-то поменяет кости — этот
     * тест скажет, что баланс поехал.
     */
    @Test
    fun `среднее костей совпадает с прежними числами`() {
        val before = mapOf(
            "latnik" to (34 to 8), "kopye" to (26 to 7), "luchnik" to (18 to 6),
            "mag" to (16 to 9), "znahar" to (20 to 9), "razboy" to (22 to 7),
            "ghoul" to (20 to 6), "bonearcher" to (14 to 5), "marauder" to (26 to 7),
            "spitter" to (16 to 7), "howler" to (22 to 6), "devourer" to (90 to 12),
        )
        Roster.all.forEach { type ->
            val (hp, damage) = before.getValue(type.id)
            assertEquals("${type.name}: HP", hp, type.hp.nominal)
            assertEquals("${type.name}: урон", damage, type.damage.nominal)
        }
    }

    @Test
    fun `здоровье нанятых выбрасывается и бывает разным`() {
        val rng = Random(3)
        val rolls = List(200) { Hero.recruit(it, Roster.LATNIK, rng).maxHp }
        assertTrue(rolls.all { it in Roster.LATNIK.hp.min..Roster.LATNIK.hp.max })
        assertTrue("все двести латников одинаковые — кости не бросаются", rolls.toSet().size > 5)
    }

    @Test
    fun `выпавшее здоровье переживает сохранение`() {
        val storage = MemoryStorage()
        val first = Game(Random(5), storage).apply { newRun(Squads.ZASTAVA) }
        val rolled = first.party.map { it.baseHp }

        val restored = Game(Random(99), storage)
        assertTrue(restored.continueRun())
        assertEquals(rolled, restored.party.map { it.baseHp })
        assertEquals(first.party.map { it.maxHp }, restored.party.map { it.maxHp })
    }

    @Test
    fun `улучшение урона прибавляется к броску`() {
        val hero = Hero(0, Roster.LATNIK, bonusAtk = 2)
        assertEquals(Dice(2, 4, 5), hero.damage)
    }
}

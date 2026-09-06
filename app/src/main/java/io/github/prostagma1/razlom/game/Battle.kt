package io.github.prostagma1.razlom.game

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlin.random.Random

enum class Outcome { VICTORY, DEFEAT }

/**
 * Пошаговый бой на прямоугольной сетке. Ходы раздаются по инициативе,
 * каждый раунд очередь пересобирается заново.
 */
class BattleState(
    val width: Int,
    val height: Int,
    combatants: List<Combatant>,
    val obstacles: Set<Pos>,
) {
    val units = mutableStateListOf<Combatant>().apply { addAll(combatants) }
    val log = mutableStateListOf<String>()

    var round by mutableIntStateOf(1)
        private set
    var movesLeft by mutableIntStateOf(0)
        private set
    var outcome by mutableStateOf<Outcome?>(null)
        private set

    private val queue = mutableStateListOf<Int>()

    val active: Combatant?
        get() = queue.firstOrNull()?.let { id -> units.firstOrNull { it.id == id && it.alive } }

    /** Очередь на текущий раунд — для полоски ходов в интерфейсе. */
    val turnOrder: List<Combatant>
        get() = queue.mapNotNull { id -> units.firstOrNull { it.id == id && it.alive } }

    /** Счётчик проведённых атак: интерфейс использует его как триггер анимации. */
    var fxSeq by mutableIntStateOf(0)
        private set
    var fxAttacker by mutableStateOf<Int?>(null)
        private set
    var fxFrom by mutableStateOf<Pos?>(null)
        private set
    var fxTarget by mutableStateOf<Pos?>(null)
        private set

    init {
        fillQueue()
        beginTurn()
    }

    fun unitAt(p: Pos): Combatant? = units.firstOrNull { it.alive && it.pos == p }

    fun blocked(p: Pos): Boolean =
        p.x !in 0 until width || p.y !in 0 until height || p in obstacles || unitAt(p) != null

    // ---- очередь ходов -------------------------------------------------

    private fun fillQueue() {
        queue.clear()
        units.filter { it.alive }
            .sortedWith(compareByDescending<Combatant> { it.speed }.thenBy { it.id })
            .forEach { queue.add(it.id) }
    }

    private fun beginTurn() {
        movesLeft = active?.type?.move ?: 0
    }

    fun endTurn() {
        if (outcome != null) return
        if (queue.isNotEmpty()) queue.removeAt(0)
        while (queue.isNotEmpty() && units.firstOrNull { it.id == queue[0] }?.alive != true) {
            queue.removeAt(0)
        }
        if (queue.isEmpty()) {
            round++
            fillQueue()
        }
        beginTurn()
        checkOutcome()
    }

    private fun checkOutcome() {
        if (outcome != null) return
        val playersAlive = units.any { it.team == Team.PLAYER && it.alive }
        val enemiesAlive = units.any { it.team == Team.ENEMY && it.alive }
        outcome = when {
            !enemiesAlive -> Outcome.VICTORY
            !playersAlive -> Outcome.DEFEAT
            else -> null
        }
    }

    // ---- перемещение ---------------------------------------------------

    /** Клетки, куда активный боец может дойти, и цена пути до каждой. */
    fun reachable(): Map<Pos, Int> {
        val start = active?.pos ?: return emptyMap()
        val budget = movesLeft
        val cost = mutableMapOf(start to 0)
        var frontier = listOf(start)
        while (frontier.isNotEmpty()) {
            val next = mutableListOf<Pos>()
            for (p in frontier) {
                val c = cost.getValue(p)
                if (c >= budget) continue
                for (n in neighbours(p)) {
                    if (n in cost || blocked(n)) continue
                    cost[n] = c + 1
                    next += n
                }
            }
            frontier = next
        }
        cost.remove(start)
        return cost
    }

    private fun neighbours(p: Pos) = listOf(
        Pos(p.x + 1, p.y), Pos(p.x - 1, p.y), Pos(p.x, p.y + 1), Pos(p.x, p.y - 1),
    )

    fun moveActiveTo(target: Pos): Boolean {
        val unit = active ?: return false
        val cost = reachable()[target] ?: return false
        unit.pos = target
        movesLeft -= cost
        return true
    }

    // ---- атака ---------------------------------------------------------

    fun canTarget(attacker: Combatant, target: Combatant): Boolean {
        if (!target.alive || attacker.pos.dist(target.pos) > attacker.type.range) return false
        return if (attacker.type.ability == Ability.HEAL) {
            target.team == attacker.team && target.hp < target.maxHp
        } else {
            target.team != attacker.team
        }
    }

    /** Проводит атаку (или лечение) и завершает ход бойца. */
    fun act(target: Combatant) {
        val attacker = active ?: return
        if (!canTarget(attacker, target)) return

        fxAttacker = attacker.id
        fxFrom = attacker.pos
        fxTarget = target.pos
        fxSeq++

        when (attacker.type.ability) {
            Ability.HEAL -> {
                val healed = minOf(attacker.attack, target.maxHp - target.hp)
                target.hp += healed
                log += "${attacker.type.name}: лечение ${target.type.name} +$healed"
            }

            Ability.SPLASH -> {
                damage(attacker, target, attacker.attack)
                units.filter {
                    it.alive && it.team != attacker.team && it.id != target.id &&
                        it.pos.dist(target.pos) == 1
                }.forEach { damage(attacker, it, attacker.attack / 2) }
            }

            Ability.FLANK -> {
                val supported = units.any {
                    it.alive && it.team == attacker.team && it.id != attacker.id &&
                        it.pos.dist(target.pos) == 1
                }
                damage(attacker, target, if (supported) attacker.attack * 3 / 2 else attacker.attack)
            }

            Ability.PIERCE -> {
                damage(attacker, target, attacker.attack)
                val dx = (target.pos.x - attacker.pos.x).coerceIn(-1, 1)
                val dy = (target.pos.y - attacker.pos.y).coerceIn(-1, 1)
                if (dx == 0 || dy == 0) {
                    unitAt(Pos(target.pos.x + dx, target.pos.y + dy))
                        ?.takeIf { it.team != attacker.team }
                        ?.let { damage(attacker, it, attacker.attack / 2) }
                }
            }

            Ability.NONE -> damage(attacker, target, attacker.attack)
        }

        checkOutcome()
        if (outcome == null) endTurn()
    }

    private fun damage(from: Combatant, to: Combatant, amount: Int) {
        val dealt = amount.coerceAtLeast(1)
        to.hp -= dealt
        log += "${from.type.name} -> ${to.type.name}: $dealt урона"
        if (to.hp <= 0) {
            to.hp = 0
            log += "${to.type.name} пал"
        }
    }

    // ---- ИИ ------------------------------------------------------------

    /** Один шаг вражеского бойца: подойти и ударить. */
    fun aiTakeTurn() {
        val me = active ?: return
        if (me.team != Team.ENEMY || outcome != null) return

        val foes = units.filter { it.alive && it.team != me.team }
        if (foes.isEmpty()) {
            endTurn()
            return
        }

        foes.filter { canTarget(me, it) }.minByOrNull { it.hp }?.let {
            act(it)
            return
        }

        // Иначе подходим ближе к самой уязвимой цели.
        val prey = foes.minByOrNull { me.pos.dist(it.pos) * 100 + it.hp } ?: foes.first()
        val step = reachable().keys.minByOrNull { it.dist(prey.pos) }
        if (step != null && step.dist(prey.pos) < me.pos.dist(prey.pos)) moveActiveTo(step)

        foes.filter { canTarget(me, it) }.minByOrNull { it.hp }?.let {
            act(it)
            return
        }
        endTurn()
    }
}

object BattleFactory {
    const val WIDTH = 7
    const val HEIGHT = 9

    fun create(party: List<Hero>, foes: List<Pair<UnitType, Int>>, rng: Random): BattleState {
        var nextId = 0
        val units = mutableListOf<Combatant>()

        party.filter { it.hp > 0 }.forEachIndexed { i, hero ->
            units += Combatant(
                id = nextId++,
                type = hero.type,
                team = Team.PLAYER,
                maxHp = hero.maxHp,
                attack = hero.attack,
                hp = hero.hp,
                pos = Pos(spread(i), HEIGHT - 1 - i / WIDTH),
                heroUid = hero.uid,
            )
        }

        foes.forEachIndexed { i, (type, bonus) ->
            units += Combatant(
                id = nextId++,
                type = type,
                team = Team.ENEMY,
                maxHp = type.maxHp + bonus,
                attack = type.attack + bonus / 6,
                hp = type.maxHp + bonus,
                pos = Pos(spread(i), i / WIDTH),
            )
        }

        val taken = units.map { it.pos }.toSet()
        val obstacles = buildSet {
            repeat(rng.nextInt(3, 7)) {
                val p = Pos(rng.nextInt(WIDTH), rng.nextInt(2, HEIGHT - 2))
                if (p !in taken) add(p)
            }
        }
        return BattleState(WIDTH, HEIGHT, units, obstacles)
    }

    /** Расставляет бойцов от центра ряда к краям. */
    private fun spread(index: Int): Int {
        val i = index % WIDTH
        val offset = (i + 1) / 2 * if (i % 2 == 0) 1 else -1
        return (WIDTH / 2 + offset).coerceIn(0, WIDTH - 1)
    }
}

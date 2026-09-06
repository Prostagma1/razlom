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
    val relics: Set<Relic> = emptySet(),
    restore: Restore? = null,
) {
    /** Снимок незаконченного боя, поднятый из сохранения. */
    data class Restore(
        val round: Int,
        val queue: List<Int>,
        val movesLeft: Int,
        val talismanSpent: Boolean,
    )

    val units = mutableStateListOf<Combatant>().apply { addAll(combatants) }
    val log = mutableStateListOf<String>()

    var round by mutableIntStateOf(1)
        private set
    var movesLeft by mutableIntStateOf(0)
        private set
    var outcome by mutableStateOf<Outcome?>(null)
        private set

    private val queue = mutableStateListOf<Int>()
    internal var talismanSpent = false
        private set

    /** Очередь как список идентификаторов — для сохранения. */
    internal val queueIds: List<Int> get() = queue.toList()

    val active: Combatant?
        get() = queue.firstOrNull()?.let { id -> units.firstOrNull { it.id == id && it.alive } }

    /** Очередь на текущий раунд — для полоски ходов в интерфейсе. */
    val turnOrder: List<Combatant>
        get() = queue.mapNotNull { id -> units.firstOrNull { it.id == id && it.alive } }

    /** Счётчик проведённых действий: интерфейс использует его как триггер анимации. */
    var fxSeq by mutableIntStateOf(0)
        private set
    var fxAttacker by mutableStateOf<Int?>(null)
        private set
    var fxFrom by mutableStateOf<Pos?>(null)
        private set
    var fxTarget by mutableStateOf<Pos?>(null)
        private set

    init {
        if (restore != null) {
            round = restore.round
            queue.addAll(restore.queue)
            movesLeft = restore.movesLeft
            talismanSpent = restore.talismanSpent
            checkOutcome()
        } else {
            if (Relic.STONE_SKIN in relics) {
                units.filter { it.team == Team.PLAYER }.forEach { it.shield += 5 }
            }
            fillQueue()
            advance()
        }
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

    private fun dropCurrent() {
        if (queue.isNotEmpty()) queue.removeAt(0)
        while (queue.isNotEmpty() && units.firstOrNull { it.id == queue[0] }?.alive != true) {
            queue.removeAt(0)
        }
        if (queue.isEmpty()) {
            round++
            fillQueue()
        }
    }

    /**
     * Передаёт ход дальше, пока не найдётся боец, который в состоянии ходить:
     * яд может добить, оглушение — заставить пропустить ход.
     */
    private fun advance() {
        var guard = 0
        while (outcome == null && guard++ < 500) {
            val unit = active ?: run { checkOutcome(); return }

            val poison = unit.statuses[Status.POISON] ?: 0
            if (poison > 0) {
                unit.statuses[Status.POISON] = poison - 1
                hurt(unit, 3, "яд")
                checkOutcome()
                if (outcome != null) return
                if (!unit.alive) {
                    dropCurrent()
                    continue
                }
            }

            val stun = unit.statuses[Status.STUN] ?: 0
            if (stun > 0) {
                unit.statuses[Status.STUN] = stun - 1
                log += "${unit.type.name} оглушён и пропускает ход"
                dropCurrent()
                continue
            }

            if (unit.cooldown > 0) unit.cooldown--
            movesLeft = unit.type.move + if (unit.team == Team.PLAYER && Relic.BOOTS in relics) 1 else 0
            return
        }
        checkOutcome()
    }

    fun endTurn() {
        if (outcome != null) return
        dropCurrent()
        advance()
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

    /** Урон бойца с учётом реликвий, действующих прямо сейчас. */
    private fun power(unit: Combatant): Int {
        val warStone = unit.team == Team.PLAYER && Relic.WAR_STONE in relics && round == 1
        return unit.attack + if (warStone) 3 else 0
    }

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
        markFx(attacker, target)

        when (attacker.type.ability) {
            Ability.HEAL -> heal(attacker, target, power(attacker))

            Ability.SPLASH -> {
                strike(attacker, target, power(attacker))
                units.filter {
                    it.alive && it.team != attacker.team && it.id != target.id &&
                        it.pos.dist(target.pos) == 1
                }.forEach { strike(attacker, it, power(attacker) / 2) }
            }

            Ability.FLANK -> {
                val supported = units.any {
                    it.alive && it.team == attacker.team && it.id != attacker.id &&
                        it.pos.dist(target.pos) == 1
                }
                strike(attacker, target, if (supported) power(attacker) * 3 / 2 else power(attacker))
            }

            Ability.PIERCE -> {
                strike(attacker, target, power(attacker))
                val dx = (target.pos.x - attacker.pos.x).coerceIn(-1, 1)
                val dy = (target.pos.y - attacker.pos.y).coerceIn(-1, 1)
                if (dx == 0 || dy == 0) {
                    unitAt(Pos(target.pos.x + dx, target.pos.y + dy))
                        ?.takeIf { it.team != attacker.team }
                        ?.let { strike(attacker, it, power(attacker) / 2) }
                }
            }

            Ability.NONE -> strike(attacker, target, power(attacker))
        }

        finishAction()
    }

    // ---- способности ---------------------------------------------------

    /** Кого активный боец может задеть своей способностью прямо сейчас. */
    fun skillTargets(): List<Combatant> {
        val unit = active ?: return emptyList()
        val skill = unit.skill ?: return emptyList()
        if (!unit.skillReady) return emptyList()
        return when (skill.target) {
            SkillTarget.SELF -> listOf(unit)
            SkillTarget.ALLY -> units.filter {
                it.alive && it.team == unit.team && unit.pos.dist(it.pos) <= skill.range
            }

            SkillTarget.ENEMY -> units.filter {
                it.alive && it.team != unit.team && unit.pos.dist(it.pos) <= skill.range
            }
        }
    }

    fun useSkill(target: Combatant) {
        val unit = active ?: return
        val skill = unit.skill ?: return
        if (target !in skillTargets()) return
        markFx(unit, target)
        log += "${unit.type.name}: ${skill.name}"

        when (skill.kind) {
            SkillKind.GUARD -> {
                val covered = units.filter {
                    it.alive && it.team == unit.team && unit.pos.dist(it.pos) <= 1
                }
                covered.forEach { it.shield += 10 }
                log += "Щит держат: ${covered.joinToString { it.type.name }}"
            }

            SkillKind.TRIP -> {
                strike(unit, target, power(unit))
                if (target.alive) target.apply(Status.STUN, 1)
            }

            SkillKind.VOLLEY -> strike(unit, target, power(unit) * 9 / 5)

            SkillKind.FIRESTORM -> {
                val caught = units.filter {
                    it.alive && it.team != unit.team &&
                        (it.id == target.id || it.pos.dist(target.pos) == 1)
                }
                caught.forEach { strike(unit, it, power(unit)) }
            }

            SkillKind.TONIC -> {
                heal(unit, target, power(unit) * 3 / 2)
                target.clearStatuses()
            }

            SkillKind.POISON_BLADE -> {
                strike(unit, target, power(unit))
                if (target.alive) target.apply(Status.POISON, 3)
            }

            SkillKind.SPIT -> {
                strike(unit, target, power(unit) * 4 / 5)
                if (target.alive) target.apply(Status.POISON, 3)
            }

            SkillKind.HOWL -> target.apply(Status.STUN, 1)

            SkillKind.RIFT -> {
                val caught = units.filter {
                    it.alive && it.team != unit.team &&
                        (it.id == target.id || it.pos.dist(target.pos) == 1)
                }
                caught.forEach { strike(unit, it, power(unit) * 6 / 5) }
                if (target.alive) target.apply(Status.STUN, 1)
            }
        }

        unit.cooldown = skill.cooldown
        finishAction()
    }

    private fun markFx(from: Combatant, to: Combatant) {
        fxAttacker = from.id
        fxFrom = from.pos
        fxTarget = to.pos
        fxSeq++
    }

    private fun finishAction() {
        checkOutcome()
        if (outcome == null) endTurn()
    }

    // ---- урон и лечение ------------------------------------------------

    private fun heal(healer: Combatant, target: Combatant, amount: Int) {
        val boosted = if (healer.team == Team.PLAYER && Relic.BANNER in relics) amount * 3 / 2 else amount
        val healed = minOf(boosted, target.maxHp - target.hp)
        target.hp += healed
        log += "${healer.type.name}: лечение ${target.type.name} +$healed"
    }

    private fun strike(from: Combatant, to: Combatant, amount: Int) {
        val dealt = hurt(to, amount, from.type.name)
        val poisonous = from.team == Team.PLAYER && Relic.VIALS in relics && from.type.range >= 3
        if (poisonous && to.alive && dealt > 0) to.apply(Status.POISON, 2)
    }

    /** Наносит урон с учётом щита, оберега и тотема. Возвращает снятое здоровье. */
    private fun hurt(to: Combatant, amount: Int, source: String): Int {
        var incoming = amount.coerceAtLeast(1)
        if (to.shield > 0) {
            val absorbed = minOf(to.shield, incoming)
            to.shield -= absorbed
            incoming -= absorbed
            log += "Щит ${to.type.name} держит $absorbed"
        }
        if (incoming <= 0) return 0

        to.hp -= incoming
        log += "$source → ${to.type.name}: $incoming урона"

        if (to.hp <= 0) {
            val saved = to.team == Team.PLAYER && Relic.TALISMAN in relics && !talismanSpent
            if (saved) {
                talismanSpent = true
                to.hp = 1
                log += "Оберег удержал ${to.type.name} на ногах"
            } else {
                to.hp = 0
                to.clearStatuses()
                to.shield = 0
                log += "${to.type.name} пал"
                if (to.team == Team.PLAYER && Relic.FURY in relics) {
                    units.filter { it.alive && it.team == Team.PLAYER }.forEach { it.battleAtk += 3 }
                    log += "Тотем ярости: отряд бьёт сильнее"
                }
            }
        }
        return incoming
    }

    // ---- ИИ ------------------------------------------------------------

    /** Один шаг вражеского бойца: способность, если готова, иначе подойти и ударить. */
    fun aiTakeTurn() {
        val me = active ?: return
        if (me.team != Team.ENEMY || outcome != null) return

        val foes = units.filter { it.alive && it.team != me.team }
        if (foes.isEmpty()) {
            endTurn()
            return
        }

        skillTargets().minByOrNull { it.hp }?.let {
            useSkill(it)
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

        skillTargets().minByOrNull { it.hp }?.let {
            useSkill(it)
            return
        }
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

    fun create(
        party: List<Hero>,
        foes: List<Pair<UnitType, Int>>,
        rng: Random,
        relics: Set<Relic> = emptySet(),
    ): BattleState {
        var nextId = 0
        val units = mutableListOf<Combatant>()

        party.filter { it.hp > 0 }.forEachIndexed { i, hero ->
            units += Combatant(
                id = nextId++,
                type = hero.type,
                team = Team.PLAYER,
                maxHp = hero.maxHp,
                baseAttack = hero.attack,
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
                baseAttack = type.attack + bonus / 6,
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
        return BattleState(WIDTH, HEIGHT, units, obstacles, relics)
    }

    /** Расставляет бойцов от центра ряда к краям. */
    private fun spread(index: Int): Int {
        val i = index % WIDTH
        val offset = (i + 1) / 2 * if (i % 2 == 0) 1 else -1
        return (WIDTH / 2 + offset).coerceIn(0, WIDTH - 1)
    }
}

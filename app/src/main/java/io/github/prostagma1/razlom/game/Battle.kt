package io.github.prostagma1.razlom.game

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlin.math.abs
import kotlin.random.Random

enum class Outcome { VICTORY, DEFEAT }

/**
 * Пошаговый бой на прямоугольной сетке. Ходы раздаются по инициативе,
 * каждый раунд очередь пересобирается заново.
 *
 * Ходят по сторонам клетки, а бьют и видят по-королевски — наискосок тоже.
 */
class BattleState(
    val width: Int,
    val height: Int,
    combatants: List<Combatant>,
    val terrain: Map<Pos, Terrain>,
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

    /** Откуда боец начал ход — чтобы можно было вернуть его на место. */
    private var turnStart by mutableStateOf<Pos?>(null)
    private var turnStartMoves by mutableIntStateOf(0)

    /** Есть ли что отменять: боец сдвинулся, но ещё не ударил. */
    val canUndoMove: Boolean
        get() = active?.let { turnStart != null && it.pos != turnStart } == true

    /** Возвращает бойца туда, откуда он пошёл, и отдаёт потраченные шаги. */
    fun undoMove() {
        val unit = active ?: return
        val start = turnStart ?: return
        unit.pos = start
        movesLeft = turnStartMoves
    }

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
            // Из сохранения отменять нечего: ход начался ещё до записи.
            turnStart = active?.pos
            turnStartMoves = restore.movesLeft
            checkOutcome()
        } else {
            if (Relic.STONE_SKIN in relics) {
                units.filter { it.team == Team.PLAYER }.forEach { it.shield += 5 }
            }
            fillQueue()
            advance()
        }
    }

    // ---- местность -----------------------------------------------------

    fun unitAt(p: Pos): Combatant? = units.firstOrNull { it.alive && it.pos == p }

    fun inBounds(p: Pos) = p.x in 0 until width && p.y in 0 until height

    /** Можно ли в принципе стоять на клетке — без учёта того, кто там сейчас. */
    fun passable(p: Pos) = inBounds(p) && terrain[p]?.blocksMove != true

    fun blocked(p: Pos): Boolean = !passable(p) || unitAt(p) != null

    private fun moveCost(p: Pos): Int = terrain[p]?.moveCost?.takeIf { it > 0 } ?: 1

    // ---- туман войны ---------------------------------------------------

    /** Видит ли клетку хоть кто-то из живых бойцов отряда. */
    fun isVisible(p: Pos): Boolean = units.any {
        it.alive && it.team == Team.PLAYER &&
            it.pos.reach(p) <= it.type.vision && lineOfSight(it.pos, p)
    }

    /** Все клетки, открытые отряду прямо сейчас. */
    fun visibleCells(): Set<Pos> = buildSet {
        for (y in 0 until height) {
            for (x in 0 until width) {
                val p = Pos(x, y)
                if (isVisible(p)) add(p)
            }
        }
    }

    /**
     * Прямая видимость по Брезенхэму. Камень и дерево обрывают луч, но сама
     * загораживающая клетка видна — иначе препятствия были бы невидимыми.
     */
    private fun lineOfSight(from: Pos, to: Pos): Boolean {
        if (from == to) return true
        var x = from.x
        var y = from.y
        val dx = abs(to.x - x)
        val dy = abs(to.y - y)
        val sx = if (x < to.x) 1 else -1
        val sy = if (y < to.y) 1 else -1
        var err = dx - dy

        while (true) {
            val doubled = 2 * err
            if (doubled > -dy) {
                err -= dy
                x += sx
            }
            if (doubled < dx) {
                err += dx
                y += sy
            }
            if (x == to.x && y == to.y) return true
            if (terrain[Pos(x, y)]?.blocksSight == true) return false
        }
    }

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
            log += "— Раунд $round —"
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
            turnStart = unit.pos
            turnStartMoves = movesLeft
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

    /**
     * Клетки, куда активный боец может дойти, и цена пути до каждой.
     * Бурелом и деревья стоят двух шагов, поэтому считаем не обходом в ширину,
     * а простыми релаксациями — поле маленькое, это дешевле любой очереди.
     */
    fun reachable(): Map<Pos, Int> {
        val start = active?.pos ?: return emptyMap()
        val budget = movesLeft
        val best = mutableMapOf(start to 0)

        var changed = true
        while (changed) {
            changed = false
            for ((p, cost) in best.toList()) {
                for (n in neighbours(p)) {
                    if (blocked(n)) continue
                    val next = cost + moveCost(n)
                    if (next <= budget && next < (best[n] ?: Int.MAX_VALUE)) {
                        best[n] = next
                        changed = true
                    }
                }
            }
        }
        best.remove(start)
        return best
    }

    private fun neighbours(p: Pos) = listOf(
        Pos(p.x + 1, p.y), Pos(p.x - 1, p.y), Pos(p.x, p.y + 1), Pos(p.x, p.y - 1),
    )

    /**
     * Сколько шагов до каждой клетки, если идти в обход препятствий.
     * Бойцы не учитываются: они разойдутся, а стены — нет.
     */
    private fun distanceField(from: Pos): Map<Pos, Int> {
        val dist = mutableMapOf(from to 0)
        var frontier = listOf(from)
        while (frontier.isNotEmpty()) {
            val next = mutableListOf<Pos>()
            for (p in frontier) {
                for (n in neighbours(p)) {
                    if (n in dist || !passable(n)) continue
                    dist[n] = dist.getValue(p) + 1
                    next += n
                }
            }
            frontier = next
        }
        return dist
    }

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

    /**
     * Сколько придётся по главной цели обычной атакой. Одна формула и для
     * удара, и для предпросмотра — иначе цифры на экране разойдутся с боем.
     */
    private fun mainAmount(attacker: Combatant, target: Combatant): Int =
        when (attacker.type.ability) {
            Ability.FLANK -> {
                val supported = units.any {
                    it.alive && it.team == attacker.team && it.id != attacker.id &&
                        it.pos.reach(target.pos) == 1
                }
                if (supported) power(attacker) * 3 / 2 else power(attacker)
            }

            else -> power(attacker)
        }

    /** Сколько придётся по главной цели способностью. */
    private fun skillAmount(unit: Combatant, kind: SkillKind): Int = when (kind) {
        SkillKind.VOLLEY -> power(unit) * 9 / 5
        SkillKind.SPIT -> power(unit) * 4 / 5
        SkillKind.RIFT -> power(unit) * 6 / 5
        SkillKind.TONIC -> power(unit) * 3 / 2
        SkillKind.TRIP, SkillKind.POISON_BLADE, SkillKind.FIRESTORM -> power(unit)
        SkillKind.GUARD, SkillKind.HOWL -> 0
    }

    /** Лечение с учётом знамени. */
    private fun healAmount(healer: Combatant, raw: Int): Int =
        if (healer.team == Team.PLAYER && Relic.BANNER in relics) raw * 3 / 2 else raw

    fun canTarget(attacker: Combatant, target: Combatant): Boolean {
        if (!target.alive || attacker.pos.reach(target.pos) > attacker.type.range) return false
        return if (attacker.type.ability == Ability.HEAL) {
            target.team == attacker.team && target.hp < target.maxHp
        } else {
            // По врагу нельзя бить вслепую: он должен быть виден отряду.
            target.team != attacker.team &&
                (attacker.team != Team.PLAYER || isVisible(target.pos))
        }
    }

    /** Что случится с целью, если ударить прямо сейчас. */
    data class Forecast(
        val healing: Boolean,
        /** Сколько снимет здоровья (или вылечит) после щита. */
        val amount: Int,
        /** Сколько съест щит цели. */
        val absorbed: Int,
        /** Здоровье цели после удара. */
        val remaining: Int,
        val lethal: Boolean,
        /** Сколько достанется каждому соседу цели, 0 — если никого не заденет. */
        val splash: Int,
        /** Что ещё повесит удар: яд, оглушение. */
        val extra: String,
    )

    /**
     * Предпросмотр удара активного бойца по цели. Считает по тем же формулам,
     * что и настоящий удар, поэтому цифры на экране не врут.
     */
    fun forecast(target: Combatant, withSkill: Boolean = false): Forecast? {
        val unit = active ?: return null
        val skill = unit.skill

        val raw: Int
        val healing: Boolean
        val splash: Int
        val extra: String

        if (withSkill && skill != null) {
            raw = skillAmount(unit, skill.kind)
            healing = skill.kind == SkillKind.TONIC
            splash = when (skill.kind) {
                SkillKind.FIRESTORM, SkillKind.RIFT -> raw
                else -> 0
            }
            extra = when (skill.kind) {
                SkillKind.TRIP, SkillKind.RIFT -> "оглушение"
                SkillKind.POISON_BLADE, SkillKind.SPIT -> "яд"
                SkillKind.GUARD -> "щит 10"
                SkillKind.HOWL -> "оглушение"
                SkillKind.TONIC -> "снимет яд и оглушение"
                else -> ""
            }
        } else {
            raw = mainAmount(unit, target)
            healing = unit.type.ability == Ability.HEAL
            splash = if (unit.type.ability == Ability.SPLASH) raw / 2 else 0
            extra = if (
                unit.team == Team.PLAYER && Relic.VIALS in relics && unit.type.range >= 3
            ) {
                "яд от склянок"
            } else {
                ""
            }
        }

        if (healing) {
            val healed = minOf(healAmount(unit, raw), target.maxHp - target.hp)
            return Forecast(true, healed, 0, target.hp + healed, false, 0, extra)
        }

        val incoming = raw.coerceAtLeast(1)
        val absorbed = minOf(target.shield, incoming)
        val toHealth = incoming - absorbed
        val remaining = (target.hp - toHealth).coerceAtLeast(0)
        val saved = target.team == Team.PLAYER && Relic.TALISMAN in relics && !talismanSpent
        return Forecast(
            healing = false,
            amount = toHealth,
            absorbed = absorbed,
            remaining = if (remaining == 0 && saved) 1 else remaining,
            lethal = remaining == 0 && !saved,
            splash = splash,
            extra = extra,
        )
    }

    /** Проводит атаку (или лечение) и завершает ход бойца. */
    fun act(target: Combatant) {
        val attacker = active ?: return
        if (!canTarget(attacker, target)) return
        markFx(attacker, target)

        val amount = mainAmount(attacker, target)
        when (attacker.type.ability) {
            Ability.HEAL -> heal(attacker, target, amount)

            Ability.SPLASH -> {
                strike(attacker, target, amount)
                neighboursOf(target, attacker.team).forEach {
                    strike(attacker, it, amount / 2)
                }
            }

            Ability.FLANK -> strike(attacker, target, amount)

            Ability.PIERCE -> {
                strike(attacker, target, amount)
                val dx = (target.pos.x - attacker.pos.x).coerceIn(-1, 1)
                val dy = (target.pos.y - attacker.pos.y).coerceIn(-1, 1)
                unitAt(Pos(target.pos.x + dx, target.pos.y + dy))
                    ?.takeIf { it.team != attacker.team }
                    ?.let { strike(attacker, it, amount / 2) }
            }

            Ability.NONE -> strike(attacker, target, amount)
        }

        finishAction()
    }

    /** Живые противники команды [side], стоящие вплотную к цели, включая диагонали. */
    private fun neighboursOf(target: Combatant, side: Team) = units.filter {
        it.alive && it.team != side && it.id != target.id && it.pos.reach(target.pos) == 1
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
                it.alive && it.team == unit.team && unit.pos.reach(it.pos) <= skill.range
            }

            SkillTarget.ENEMY -> units.filter {
                it.alive && it.team != unit.team && unit.pos.reach(it.pos) <= skill.range &&
                    (unit.team != Team.PLAYER || isVisible(it.pos))
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
                    it.alive && it.team == unit.team && unit.pos.reach(it.pos) <= 1
                }
                covered.forEach { it.shield += 10 }
                log += "Щит держат: ${covered.joinToString { it.type.name }}"
            }

            SkillKind.TRIP -> {
                strike(unit, target, skillAmount(unit, skill.kind))
                if (target.alive) target.apply(Status.STUN, 1)
            }

            SkillKind.VOLLEY -> strike(unit, target, skillAmount(unit, skill.kind))

            SkillKind.FIRESTORM -> {
                (listOf(target) + neighboursOf(target, unit.team)).forEach {
                    strike(unit, it, skillAmount(unit, skill.kind))
                }
            }

            SkillKind.TONIC -> {
                heal(unit, target, skillAmount(unit, skill.kind))
                target.clearStatuses()
            }

            SkillKind.POISON_BLADE -> {
                strike(unit, target, skillAmount(unit, skill.kind))
                if (target.alive) target.apply(Status.POISON, 3)
            }

            SkillKind.SPIT -> {
                strike(unit, target, skillAmount(unit, skill.kind))
                if (target.alive) target.apply(Status.POISON, 3)
            }

            SkillKind.HOWL -> target.apply(Status.STUN, 1)

            SkillKind.RIFT -> {
                (listOf(target) + neighboursOf(target, unit.team)).forEach {
                    strike(unit, it, skillAmount(unit, skill.kind))
                }
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
        val healed = minOf(healAmount(healer, amount), target.maxHp - target.hp)
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

        if (strikeIfPossible(me)) return
        approach(me, foes)
        if (strikeIfPossible(me)) return
        endTurn()
    }

    private fun strikeIfPossible(me: Combatant): Boolean {
        skillTargets().minByOrNull { it.hp }?.let {
            useSkill(it)
            return true
        }
        units.filter { it.alive && it.team != me.team && canTarget(me, it) }
            .minByOrNull { it.hp }
            ?.let {
                act(it)
                return true
            }
        return false
    }

    /**
     * Шаг к цели по настоящему пути, а не по прямой: иначе боец утыкается
     * в камень и топчется на месте, потому что любой обход сначала уводит дальше.
     */
    private fun approach(me: Combatant, foes: List<Combatant>) {
        val prey = foes.minByOrNull { me.pos.dist(it.pos) * 100 + it.hp } ?: return
        val field = distanceField(prey.pos)
        val here = field[me.pos] ?: Int.MAX_VALUE

        val step = reachable().entries
            .filter { field.containsKey(it.key) }
            .minByOrNull { field.getValue(it.key) * 100 + it.value }
            ?: return

        if (field.getValue(step.key) < here) moveActiveTo(step.key)
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

        return BattleState(WIDTH, HEIGHT, units, growTerrain(units.map { it.pos }.toSet(), rng), relics)
    }

    /** Раскидывает камни, деревья и бурелом по середине поля, не задевая строй. */
    private fun growTerrain(taken: Set<Pos>, rng: Random): Map<Pos, Terrain> = buildMap {
        fun scatter(kind: Terrain, count: Int) {
            repeat(count) {
                val p = Pos(rng.nextInt(WIDTH), rng.nextInt(2, HEIGHT - 2))
                if (p !in taken && p !in this) put(p, kind)
            }
        }
        scatter(Terrain.ROCK, rng.nextInt(3, 7))
        scatter(Terrain.TREE, rng.nextInt(3, 6))
        scatter(Terrain.BRAMBLE, rng.nextInt(2, 5))
    }

    /** Расставляет бойцов от центра ряда к краям. */
    private fun spread(index: Int): Int {
        val i = index % WIDTH
        val offset = (i + 1) / 2 * if (i % 2 == 0) 1 else -1
        return (WIDTH / 2 + offset).coerceIn(0, WIDTH - 1)
    }
}

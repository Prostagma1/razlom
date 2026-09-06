package io.github.prostagma1.razlom.game

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlin.random.Random

enum class Screen { MENU, MAP, BATTLE, REWARD, GAME_OVER, RUN_WON }

/**
 * Состояние забега: отряд, карта узлов, текущий бой и экран.
 * Один экземпляр живёт на всё приложение.
 */
class Game(private val rng: Random = Random.Default) {

    var screen by mutableStateOf(Screen.MENU)
        private set

    val party = mutableStateListOf<Hero>()

    var map by mutableStateOf(MapGenerator.generate(rng))
        private set

    var battle by mutableStateOf<BattleState?>(null)
        private set

    var rewards by mutableStateOf<List<Reward>>(emptyList())
        private set

    /** Последнее событие для строчки-подсказки на карте. */
    var notice by mutableStateOf("")
        private set

    val visited = mutableStateListOf<Int>()
    val available = mutableStateListOf<Int>()

    private var currentNodeId: Int? = null
    private var nextHeroUid = 0

    val currentRow: Int get() = currentNodeId?.let { map.nodes[it]?.row } ?: 0
    val currentKind: NodeKind? get() = currentNodeId?.let { map.nodes[it]?.kind }

    fun newRun() {
        party.clear()
        Roster.starting.forEach { party += Hero(nextHeroUid++, it) }
        map = MapGenerator.generate(rng)
        visited.clear()
        available.clear()
        available += map.rows.first().map { it.id }
        currentNodeId = null
        battle = null
        rewards = emptyList()
        notice = "Отряд выступает в путь"
        screen = Screen.MAP
    }

    fun toMenu() {
        screen = Screen.MENU
    }

    fun enterNode(nodeId: Int) {
        val node = map.nodes[nodeId] ?: return
        if (nodeId !in available) return

        currentNodeId = nodeId
        visited += nodeId
        available.clear()

        when (node.kind) {
            NodeKind.REST -> {
                party.forEach { it.heal(maxOf(1, it.maxHp * 3 / 5)) }
                notice = "Привал: отряд перевязал раны"
                openNextChoices(node)
            }

            NodeKind.RECRUIT -> {
                rewards = Encounters.recruitOffer(rng)
                screen = Screen.REWARD
            }

            else -> {
                battle = BattleFactory.create(party, Encounters.foesFor(node.kind, node.row, rng), rng)
                screen = Screen.BATTLE
            }
        }
    }

    /** Вызывается, когда игрок подтвердил исход боя. */
    fun resolveBattle() {
        val state = battle ?: return
        val node = currentNodeId?.let { map.nodes[it] }

        if (state.outcome == Outcome.DEFEAT) {
            screen = Screen.GAME_OVER
            return
        }

        // Переносим здоровье обратно в отряд; павшие поднимаются еле живыми.
        party.forEach { hero ->
            val fighter = state.units.firstOrNull { it.heroUid == hero.uid }
            hero.hp = when {
                fighter == null -> hero.hp
                fighter.hp > 0 -> fighter.hp
                else -> maxOf(1, hero.maxHp / 4)
            }
        }
        // Бой не сбрасываем: он ещё дорисовывается, пока экраны меняются анимацией.

        if (node?.kind == NodeKind.BOSS) {
            screen = Screen.RUN_WON
            return
        }
        rewards = Encounters.rewards(party.size, rng)
        screen = Screen.REWARD
    }

    fun takeReward(reward: Reward) {
        when (reward) {
            is Reward.Recruit -> if (party.size < Encounters.MAX_PARTY) {
                party += Hero(nextHeroUid++, reward.type)
            }

            Reward.PartyAttack -> party.forEach { it.bonusAtk += 2 }
            Reward.PartyHealth -> party.forEach {
                it.bonusHp += 6
                it.heal(6)
            }

            Reward.FullHeal -> party.forEach { it.heal(it.maxHp) }
        }
        notice = reward.title
        rewards = emptyList()
        openNextChoices(currentNodeId?.let { map.nodes[it] })
    }

    private fun openNextChoices(node: MapNode?) {
        available.clear()
        if (node != null) available += node.next.distinct()
        screen = Screen.MAP
    }
}

package io.github.prostagma1.razlom.game

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlin.random.Random

enum class Screen { MENU, MAP, BATTLE, REWARD, SHOP, GAME_OVER, RUN_WON }

/**
 * Состояние забега: отряд, карта узлов, текущий бой и экран.
 * Один экземпляр живёт на всё приложение.
 */
class Game(
    private val rng: Random = Random.Default,
    private val storage: Storage? = null,
) {
    var screen by mutableStateOf(Screen.MENU)
        internal set

    val party = mutableStateListOf<Hero>()
    val relics = mutableStateListOf<Relic>()

    var gold by mutableIntStateOf(0)
        internal set

    var map by mutableStateOf(MapGenerator.generate(rng))
        internal set

    var battle by mutableStateOf<BattleState?>(null)
        internal set

    var rewards by mutableStateOf<List<Reward>>(emptyList())
        internal set

    var shop by mutableStateOf<List<ShopOffer>>(emptyList())
        internal set

    /** Последнее событие для строчки-подсказки на карте. */
    var notice by mutableStateOf("")
        internal set

    /** Что открылось по итогам забега — показывается на экране финала. */
    var unlocked by mutableStateOf<List<String>>(emptyList())
        internal set

    val visited = mutableStateListOf<Int>()
    val available = mutableStateListOf<Int>()

    val profile = Profile()

    internal var currentNodeId: Int? = null
    internal var nextHeroUid = 0

    /** Состав, которым играли в прошлый раз — для кнопки «ещё раз». */
    var lastSquad by mutableStateOf(Squads.ZASTAVA)
        private set

    /** Есть ли забег, к которому можно вернуться. */
    var hasSavedRun by mutableStateOf(false)
        private set

    val currentRow: Int get() = currentNodeId?.let { map.nodes[it]?.row } ?: 0
    val currentKind: NodeKind? get() = currentNodeId?.let { map.nodes[it]?.kind }
    val relicSet: Set<Relic> get() = relics.toSet()

    init {
        storage?.read(Storage.PROFILE)?.let { SaveCodec.decodeProfile(it, profile) }
        hasSavedRun = storage?.read(Storage.RUN) != null
    }

    // ---- жизненный цикл забега -----------------------------------------

    fun newRun(squad: Squad = Squads.ZASTAVA) {
        lastSquad = squad
        party.clear()
        squad.members.forEach { party += Hero(nextHeroUid++, it) }
        relics.clear()
        gold = 0
        map = MapGenerator.generate(rng)
        visited.clear()
        available.clear()
        available += map.rows.first().map { it.id }
        currentNodeId = null
        battle = null
        rewards = emptyList()
        shop = emptyList()
        unlocked = emptyList()
        notice = "Отряд «${squad.name}» выступает в путь"
        profile.startRun()
        screen = Screen.MAP
        saveProfile()
        save()
    }

    /** Возвращает игрока в прерванный забег. */
    fun continueRun(): Boolean {
        val text = storage?.read(Storage.RUN) ?: return false
        val ok = SaveCodec.decodeRun(text, this)
        if (!ok) {
            storage.delete(Storage.RUN)
            hasSavedRun = false
        }
        return ok
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
                rewards = Encounters.recruitOffer(profile.unlockedUnits, rng)
                screen = Screen.REWARD
            }

            NodeKind.SHOP -> {
                shop = Encounters.shopOffers(party.size, profile.unlockedUnits, relicSet, rng)
                screen = Screen.SHOP
            }

            else -> {
                battle = BattleFactory.create(
                    party,
                    Encounters.foesFor(node.kind, node.row, rng),
                    rng,
                    relicSet,
                )
                screen = Screen.BATTLE
            }
        }
        save()
    }

    /** Вызывается, когда игрок подтвердил исход боя. */
    fun resolveBattle() {
        val state = battle ?: return
        val node = currentNodeId?.let { map.nodes[it] }

        if (state.outcome == Outcome.DEFEAT) {
            unlocked = profile.finishRun(currentRow, won = false)
            screen = Screen.GAME_OVER
            saveProfile()
            clearSave()
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
        if (Relic.BANDAGES in relicSet) party.forEach { it.heal(6) }

        val earned = Encounters.goldFor(node?.kind ?: NodeKind.BATTLE, node?.row ?: 0, relicSet)
        gold += earned
        notice = "Золота получено: $earned"

        if (node?.kind == NodeKind.BOSS) {
            unlocked = profile.finishRun(MapGenerator.DEPTH - 1, won = true)
            screen = Screen.RUN_WON
            saveProfile()
            clearSave()
            return
        }
        rewards = Encounters.rewards(party.size, profile.unlockedUnits, relicSet, rng)
        screen = Screen.REWARD
        save()
    }

    fun takeReward(reward: Reward) {
        when (reward) {
            is Reward.Recruit -> if (party.size < Encounters.MAX_PARTY) {
                party += Hero(nextHeroUid++, reward.type)
            }

            is Reward.Trophy -> if (reward.relic !in relics) relics += reward.relic

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

    // ---- лавка ---------------------------------------------------------

    fun canAfford(offer: ShopOffer) = gold >= offer.price

    fun buy(offer: ShopOffer) {
        if (!canAfford(offer)) return
        when (offer) {
            is ShopOffer.Trinket -> {
                if (offer.relic in relics) return
                relics += offer.relic
            }

            is ShopOffer.Hire -> {
                if (party.size >= Encounters.MAX_PARTY) return
                party += Hero(nextHeroUid++, offer.type)
            }

            ShopOffer.Mend -> party.forEach { it.heal(it.maxHp) }
            ShopOffer.Whetstone -> party.forEach { it.bonusAtk += 2 }
        }
        gold -= offer.price
        shop = shop - offer
        save()
    }

    fun leaveShop() {
        shop = emptyList()
        notice = "Лавка закрылась за спиной"
        openNextChoices(currentNodeId?.let { map.nodes[it] })
    }

    private fun openNextChoices(node: MapNode?) {
        available.clear()
        if (node != null) available += node.next.distinct()
        screen = Screen.MAP
        save()
    }

    // ---- сохранение ----------------------------------------------------

    /** Пишет состояние забега на диск. Вызывается после каждого перехода. */
    internal fun save() {
        val store = storage ?: return
        if (screen == Screen.MENU || screen == Screen.GAME_OVER || screen == Screen.RUN_WON) return
        store.write(Storage.RUN, SaveCodec.encodeRun(this))
        hasSavedRun = true
    }

    internal fun saveProfile() {
        storage?.write(Storage.PROFILE, SaveCodec.encodeProfile(profile))
    }

    private fun clearSave() {
        storage?.delete(Storage.RUN)
        hasSavedRun = false
    }
}

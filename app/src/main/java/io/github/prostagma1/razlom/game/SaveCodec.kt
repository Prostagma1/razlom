package io.github.prostagma1.razlom.game

/**
 * Сохранение в простом построчном формате `ключ=значение`. Формат свой,
 * а не JSON, чтобы обойтись без зависимостей и читать сейв глазами при отладке.
 * Повторяющиеся ключи означают список.
 */
object SaveCodec {
    private const val VERSION = 3

    /** Что умеем читать. Второй формат знал только сплошные препятствия. */
    private val SUPPORTED = setOf(2, 3)

    // ---- профиль -------------------------------------------------------

    fun encodeProfile(profile: Profile): String = buildString {
        appendLine("v=$VERSION")
        appendLine("bestRow=${profile.bestRow}")
        appendLine("runsPlayed=${profile.runsPlayed}")
        appendLine("runsWon=${profile.runsWon}")
    }

    fun decodeProfile(text: String, into: Profile): Boolean = runCatching {
        val map = text.lineSequence().mapNotNull { line ->
            line.split('=', limit = 2).takeIf { it.size == 2 }?.let { it[0] to it[1] }
        }.toMap()
        into.restore(
            bestRow = map["bestRow"]?.toInt() ?: 0,
            runsPlayed = map["runsPlayed"]?.toInt() ?: 0,
            runsWon = map["runsWon"]?.toInt() ?: 0,
        )
        true
    }.getOrDefault(false)

    // ---- забег ---------------------------------------------------------

    fun encodeRun(game: Game): String = buildString {
        appendLine("v=$VERSION")
        appendLine("screen=${game.screen.name}")
        appendLine("gold=${game.gold}")
        appendLine("node=${game.currentNodeId ?: -1}")
        appendLine("uid=${game.nextHeroUid}")
        appendLine("notice=${game.notice}")
        appendLine("relics=${game.relics.joinToString(",") { it.name }}")
        appendLine("visited=${game.visited.joinToString(",")}")
        appendLine("available=${game.available.joinToString(",")}")

        game.party.forEach {
            appendLine("hero=${it.uid}|${it.type.id}|${it.bonusHp}|${it.bonusAtk}|${it.hp}")
        }
        game.map.rows.forEach { row ->
            appendLine("mrow=${row.joinToString(",") { it.id.toString() }}")
        }
        game.map.nodes.values.forEach { node ->
            appendLine(
                "mnode=${node.id}|${node.row}|${node.col}|${node.kind.name}|" +
                    node.next.joinToString(","),
            )
        }
        game.rewards.forEach { appendLine("reward=${encodeReward(it)}") }
        game.shop.forEach { appendLine("offer=${encodeOffer(it)}") }

        game.battle?.let { battle ->
            appendLine("battle=1")
            appendLine("bsize=${battle.width}|${battle.height}")
            appendLine("bround=${battle.round}")
            appendLine("bmoves=${battle.movesLeft}")
            appendLine("btalisman=${if (battle.talismanSpent) 1 else 0}")
            appendLine("bqueue=${battle.queueIds.joinToString(",")}")
            appendLine(
                "bterr=" + battle.terrain.entries.joinToString(";") {
                    "${it.key.x},${it.key.y},${it.value.name}"
                },
            )
            battle.units.forEach { appendLine("bunit=${encodeUnit(it)}") }
        }
    }

    fun decodeRun(text: String, game: Game): Boolean = runCatching {
        val lines = text.lineSequence()
            .mapNotNull { line -> line.split('=', limit = 2).takeIf { it.size == 2 } }
            .map { it[0] to it[1] }
            .toList()

        fun one(key: String): String? = lines.firstOrNull { it.first == key }?.second
        fun many(key: String): List<String> = lines.filter { it.first == key }.map { it.second }

        val version = one("v")?.toIntOrNull() ?: return@runCatching false
        if (version !in SUPPORTED) return@runCatching false

        // Карта.
        val nodes = many("mnode").map { raw ->
            val f = raw.split('|')
            MapNode(
                id = f[0].toInt(),
                row = f[1].toInt(),
                col = f[2].toInt(),
                kind = NodeKind.valueOf(f[3]),
                next = f.getOrNull(4).orEmpty().ids().toMutableList(),
            )
        }.associateBy { it.id }
        val rows = many("mrow").map { line -> line.ids().mapNotNull { nodes[it] } }
        if (rows.isEmpty() || rows.any { it.isEmpty() }) return@runCatching false

        game.party.clear()
        many("hero").forEach { raw ->
            val f = raw.split('|')
            val type = Roster.byId(f[1]) ?: return@runCatching false
            game.party += Hero(f[0].toInt(), type, f[2].toInt(), f[3].toInt())
                .also { it.hp = f[4].toInt() }
        }
        if (game.party.isEmpty()) return@runCatching false

        game.map = RunMap(rows)
        game.relics.clear()
        one("relics").orEmpty().split(',').filter { it.isNotBlank() }
            .mapNotNull { Relic.byName(it) }
            .forEach { game.relics += it }

        game.gold = one("gold")?.toInt() ?: 0
        game.nextHeroUid = one("uid")?.toInt() ?: game.party.size
        game.notice = one("notice").orEmpty()
        game.currentNodeId = one("node")?.toInt()?.takeIf { it >= 0 }

        game.visited.clear()
        game.visited += one("visited").orEmpty().ids()
        game.available.clear()
        game.available += one("available").orEmpty().ids()

        game.rewards = many("reward").mapNotNull { decodeReward(it) }
        game.shop = many("offer").mapNotNull { decodeOffer(it) }

        game.battle = if (one("battle") == "1") {
            val size = one("bsize")!!.split('|')
            val units = many("bunit").map { decodeUnit(it) }
            BattleState(
                width = size[0].toInt(),
                height = size[1].toInt(),
                combatants = units,
                // Старые сохранения знали только «препятствие» — считаем их камнями.
                terrain = one("bterr")?.split(';')?.filter { it.isNotBlank() }?.associate {
                    val f = it.split(',')
                    Pos(f[0].toInt(), f[1].toInt()) to Terrain.valueOf(f[2])
                } ?: one("bobst").orEmpty().split(';').filter { it.isNotBlank() }.associate {
                    val f = it.split(',')
                    Pos(f[0].toInt(), f[1].toInt()) to Terrain.ROCK
                },
                relics = game.relicSet,
                restore = BattleState.Restore(
                    round = one("bround")!!.toInt(),
                    queue = one("bqueue").orEmpty().ids(),
                    movesLeft = one("bmoves")!!.toInt(),
                    talismanSpent = one("btalisman") == "1",
                ),
            )
        } else {
            null
        }

        val screen = Screen.valueOf(one("screen")!!)
        game.screen = if (screen == Screen.BATTLE && game.battle == null) Screen.MAP else screen
        true
    }.getOrDefault(false)

    // ---- мелкие части --------------------------------------------------

    private fun String.ids(): List<Int> =
        split(',').filter { it.isNotBlank() }.map { it.trim().toInt() }

    private fun encodeUnit(u: Combatant): String {
        val statuses = u.statuses.filterValues { it > 0 }.entries
            .joinToString(",") { "${it.key.name}:${it.value}" }
        return listOf(
            u.id, u.type.id, u.team.name, u.maxHp, u.attack - u.battleAtk, u.hp,
            u.pos.x, u.pos.y, u.heroUid ?: -1, u.shield, u.battleAtk, u.cooldown, statuses,
        ).joinToString("|")
    }

    private fun decodeUnit(raw: String): Combatant {
        val f = raw.split('|')
        val unit = Combatant(
            id = f[0].toInt(),
            type = Roster.anyById(f[1]) ?: error("неизвестный боец ${f[1]}"),
            team = Team.valueOf(f[2]),
            maxHp = f[3].toInt(),
            baseAttack = f[4].toInt(),
            hp = f[5].toInt(),
            pos = Pos(f[6].toInt(), f[7].toInt()),
            heroUid = f[8].toInt().takeIf { it >= 0 },
        )
        unit.shield = f[9].toInt()
        unit.battleAtk = f[10].toInt()
        unit.cooldown = f[11].toInt()
        f.getOrNull(12).orEmpty().split(',').filter { it.isNotBlank() }.forEach { pair ->
            val (name, turns) = pair.split(':')
            unit.statuses[Status.valueOf(name)] = turns.toInt()
        }
        return unit
    }

    private fun encodeReward(reward: Reward): String = when (reward) {
        is Reward.Recruit -> "RECRUIT:${reward.type.id}"
        is Reward.Trophy -> "TROPHY:${reward.relic.name}"
        Reward.PartyAttack -> "ATTACK"
        Reward.PartyHealth -> "HEALTH"
        Reward.FullHeal -> "FULLHEAL"
    }

    private fun decodeReward(raw: String): Reward? {
        val (tag, arg) = raw.split(':', limit = 2).let { it[0] to it.getOrNull(1) }
        return when (tag) {
            "RECRUIT" -> Roster.byId(arg.orEmpty())?.let { Reward.Recruit(it) }
            "TROPHY" -> Relic.byName(arg.orEmpty())?.let { Reward.Trophy(it) }
            "ATTACK" -> Reward.PartyAttack
            "HEALTH" -> Reward.PartyHealth
            "FULLHEAL" -> Reward.FullHeal
            else -> null
        }
    }

    private fun encodeOffer(offer: ShopOffer): String = when (offer) {
        is ShopOffer.Trinket -> "TRINKET:${offer.relic.name}"
        is ShopOffer.Hire -> "HIRE:${offer.type.id}"
        ShopOffer.Mend -> "MEND"
        ShopOffer.Whetstone -> "WHET"
    }

    private fun decodeOffer(raw: String): ShopOffer? {
        val (tag, arg) = raw.split(':', limit = 2).let { it[0] to it.getOrNull(1) }
        return when (tag) {
            "TRINKET" -> Relic.byName(arg.orEmpty())?.let { ShopOffer.Trinket(it) }
            "HIRE" -> Roster.byId(arg.orEmpty())?.let { ShopOffer.Hire(it) }
            "MEND" -> ShopOffer.Mend
            "WHET" -> ShopOffer.Whetstone
            else -> null
        }
    }
}

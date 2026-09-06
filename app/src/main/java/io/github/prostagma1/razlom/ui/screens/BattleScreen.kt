package io.github.prostagma1.razlom.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.prostagma1.razlom.game.Ability
import io.github.prostagma1.razlom.game.BattleState
import io.github.prostagma1.razlom.game.Combatant
import io.github.prostagma1.razlom.game.Outcome
import io.github.prostagma1.razlom.game.Pos
import io.github.prostagma1.razlom.game.SkillTarget
import io.github.prostagma1.razlom.game.Status
import io.github.prostagma1.razlom.game.Team
import io.github.prostagma1.razlom.game.Terrain
import io.github.prostagma1.razlom.ui.theme.Arcane
import io.github.prostagma1.razlom.ui.theme.Blood
import io.github.prostagma1.razlom.ui.theme.Bone
import io.github.prostagma1.razlom.ui.theme.Ember
import io.github.prostagma1.razlom.ui.theme.Field
import io.github.prostagma1.razlom.ui.theme.Ink
import io.github.prostagma1.razlom.ui.theme.InkRaised
import io.github.prostagma1.razlom.ui.theme.Frost
import io.github.prostagma1.razlom.ui.theme.Moss
import io.github.prostagma1.razlom.ui.theme.Steel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun BattleScreen(battle: BattleState, onFinished: () -> Unit) {
    val measurer = rememberTextMeasurer()
    val haptics = LocalHapticFeedback.current

    val anims = remember(battle) { mutableMapOf<Int, UnitAnim>() }
    battle.units.forEach { anims.getOrPut(it.id) { UnitAnim(it.pos) } }
    battle.units.forEach { unit ->
        key(unit.id) { UnitAnimator(unit, anims.getValue(unit.id), haptics) }
    }

    // Выпад атакующего в сторону цели.
    LaunchedEffect(battle.fxSeq) {
        if (battle.fxSeq == 0) return@LaunchedEffect
        val anim = battle.fxAttacker?.let { anims[it] } ?: return@LaunchedEffect
        val from = battle.fxFrom ?: return@LaunchedEffect
        val to = battle.fxTarget ?: return@LaunchedEffect
        val delta = Offset((to.x - from.x).toFloat(), (to.y - from.y).toFloat())
        val length = delta.getDistance().coerceAtLeast(0.001f)
        anim.lunge.animateTo(delta / length * 0.3f, tween(90))
        anim.lunge.animateTo(Offset.Zero, spring(dampingRatio = 0.45f))
    }

    val active = battle.active
    val playerTurn = active?.team == Team.PLAYER
    var inspected by remember(battle) { mutableStateOf<Int?>(null) }
    var skillMode by remember(battle) { mutableStateOf(false) }

    // Разведка: в начале боя поле открыто, потом опускается туман.
    var scouting by remember(battle) { mutableStateOf(true) }
    LaunchedEffect(battle) {
        delay(3500)
        scouting = false
    }
    val visible = remember(
        scouting,
        battle.units.map { it.pos to it.alive },
    ) { if (scouting) null else battle.visibleCells() }
    fun seen(p: Pos) = visible == null || p in visible

    // Режим способности не должен переезжать на следующего бойца.
    LaunchedEffect(active?.id, battle.round) { skillMode = false }

    // Враги ходят сами, с паузой, чтобы было видно, что происходит.
    // Это отдельный цикл на весь бой, а не эффект с ключами: ключи пересборки
    // могут не смениться между двумя ходами, и тогда враг не пойдёт никогда.
    LaunchedEffect(battle) {
        while (true) {
            val actor = battle.active
            if (battle.outcome == null && actor != null && actor.team == Team.ENEMY) {
                delay(620)
                // За время паузы ход мог уйти дальше — проверяем, что он всё ещё чей надо.
                if (battle.outcome == null && battle.active?.id == actor.id) battle.aiTakeTurn()
            } else {
                delay(100)
            }
        }
    }

    val skillAims = if (playerTurn && skillMode) battle.skillTargets() else emptyList()
    val skillCells = skillAims.map { it.pos }.toSet()
    val reachable = if (playerTurn && !skillMode) battle.reachable() else emptyMap()
    val targets = if (playerTurn && !skillMode) {
        battle.units.filter { battle.canTarget(active, it) }.map { it.pos }.toSet()
    } else {
        emptySet()
    }

    val pulse by rememberInfiniteTransition(label = "pulse").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1100), RepeatMode.Reverse),
        label = "pulse",
    )

    Column(Modifier.fillMaxSize()) {
        TurnBar(battle)
        TurnOrderStrip(battle)

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(horizontal = 10.dp, vertical = 6.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(
                    Brush.verticalGradient(listOf(Color(0xFF161B24), Color(0xFF0D1015))),
                ),
        ) {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(active?.id, battle.movesLeft, battle.outcome, skillMode) {
                        detectTapGestures { tap ->
                            val cell = minOf(
                                size.width.toFloat() / battle.width,
                                size.height.toFloat() / battle.height,
                            )
                            val ox = (size.width - cell * battle.width) / 2f
                            val oy = (size.height - cell * battle.height) / 2f
                            val gx = ((tap.x - ox) / cell).toInt()
                            val gy = ((tap.y - oy) / cell).toInt()
                            if (tap.x < ox || tap.y < oy ||
                                gx !in 0 until battle.width || gy !in 0 until battle.height
                            ) {
                                return@detectTapGestures
                            }
                            val p = Pos(gx, gy)
                            val unit = battle.unitAt(p)
                            when {
                                battle.outcome != null || !playerTurn -> inspected = unit?.id

                                skillMode -> {
                                    val aim = skillAims.firstOrNull { it.pos == p }
                                    if (aim != null) {
                                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                        battle.useSkill(aim)
                                    }
                                    skillMode = false
                                }

                                unit != null && battle.canTarget(active, unit) -> {
                                    inspected = null
                                    battle.act(unit)
                                }

                                unit != null -> inspected = unit.id

                                p in reachable -> {
                                    inspected = null
                                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    battle.moveActiveTo(p)
                                }

                                else -> inspected = null
                            }
                        }
                    },
            ) {
                val cell = minOf(size.width / battle.width, size.height / battle.height)
                val ox = (size.width - cell * battle.width) / 2f
                val oy = (size.height - cell * battle.height) / 2f
                fun cellTopLeft(p: Pos) = Offset(ox + p.x * cell, oy + p.y * cell)
                fun centerOf(o: Offset) = Offset(ox + (o.x + 0.5f) * cell, oy + (o.y + 0.5f) * cell)

                for (y in 0 until battle.height) {
                    for (x in 0 until battle.width) {
                        val p = Pos(x, y)
                        val tl = cellTopLeft(p)
                        val ground = if ((x + y) % 2 == 0) Field.cell else Field.cellAlt
                        drawRect(ground, tl, Size(cell, cell))

                        when (battle.terrain[p]) {
                            Terrain.ROCK -> {
                                drawRect(Field.rock, tl, Size(cell, cell))
                                drawCircle(
                                    Field.rockTop,
                                    radius = cell * 0.28f,
                                    center = Offset(tl.x + cell * 0.5f, tl.y + cell * 0.45f),
                                )
                            }

                            Terrain.TREE -> {
                                drawCircle(
                                    Field.tree,
                                    radius = cell * 0.42f,
                                    center = Offset(tl.x + cell * 0.5f, tl.y + cell * 0.5f),
                                )
                                drawCircle(
                                    Field.treeTop,
                                    radius = cell * 0.26f,
                                    center = Offset(tl.x + cell * 0.42f, tl.y + cell * 0.42f),
                                )
                            }

                            Terrain.BRAMBLE -> {
                                drawRect(Field.bramble, tl, Size(cell, cell))
                                repeat(3) { i ->
                                    val off = cell * (0.25f + 0.25f * i)
                                    drawLine(
                                        Field.brambleLine,
                                        start = Offset(tl.x + off, tl.y + cell * 0.15f),
                                        end = Offset(tl.x + off - cell * 0.18f, tl.y + cell * 0.85f),
                                        strokeWidth = 2f,
                                    )
                                }
                            }

                            null -> Unit
                        }

                        if (p in skillCells) {
                            drawRect(Field.skill.copy(alpha = 0.5f + 0.35f * pulse), tl, Size(cell, cell))
                            drawRect(Arcane.copy(alpha = 0.5f + 0.5f * pulse), tl, Size(cell, cell), style = Stroke(3f))
                        } else if (p in targets) {
                            drawRect(Field.threat.copy(alpha = 0.45f + 0.4f * pulse), tl, Size(cell, cell))
                            drawRect(Blood.copy(alpha = 0.5f + 0.5f * pulse), tl, Size(cell, cell), style = Stroke(3f))
                        } else if (p in reachable) {
                            drawRect(Field.reachable.copy(alpha = 0.5f + 0.25f * pulse), tl, Size(cell, cell))
                        }
                        drawRect(Field.grid.copy(alpha = 0.6f), tl, Size(cell, cell), style = Stroke(1f))
                    }
                }

                // Точки на доступных клетках — куда можно шагнуть.
                reachable.keys.forEach { p ->
                    drawCircle(
                        Steel.copy(alpha = 0.35f + 0.25f * pulse),
                        radius = cell * 0.07f,
                        center = centerOf(Offset(p.x.toFloat(), p.y.toFloat())),
                    )
                }

                battle.units.forEach { unit ->
                    val anim = anims.getValue(unit.id)
                    if (anim.fade.value <= 0.01f) return@forEach
                    if (unit.team == Team.ENEMY && !seen(unit.pos)) return@forEach

                    val c = centerOf(anim.cell.value + anim.lunge.value)
                    val alpha = anim.fade.value
                    val body = if (unit.team == Team.PLAYER) Field.ally else Field.foe
                    val tinted = lerp(body, Color.White, anim.flash.value)
                    val radius = cell * 0.36f * anim.punch.value * (0.6f + 0.4f * alpha)

                    // Тень под фигурой, чтобы читалась глубина.
                    drawCircle(
                        Color.Black.copy(alpha = 0.35f * alpha),
                        radius = radius,
                        center = c + Offset(0f, cell * 0.06f),
                    )
                    drawCircle(tinted.copy(alpha = alpha), radius = radius, center = c)

                    if (unit.id == active?.id && battle.outcome == null) {
                        drawCircle(
                            Field.active.copy(alpha = 0.35f + 0.45f * pulse),
                            radius = radius + cell * (0.08f + 0.05f * pulse),
                            center = c,
                            style = Stroke(4f),
                        )
                    }
                    if (unit.shield > 0) {
                        drawCircle(
                            Frost.copy(alpha = 0.85f * alpha),
                            radius = radius + cell * 0.1f,
                            center = c,
                            style = Stroke(3f),
                        )
                    }
                    if (unit.id == inspected) {
                        drawCircle(Bone.copy(alpha = 0.8f * alpha), radius = radius + cell * 0.13f, center = c, style = Stroke(2f))
                    }

                    val glyph = measurer.measure(
                        unit.type.glyph,
                        TextStyle(color = Ink.copy(alpha = alpha), fontSize = (cell * 0.30f).toSp(), fontWeight = FontWeight.Bold),
                    )
                    drawText(
                        glyph,
                        topLeft = Offset(c.x - glyph.size.width / 2f, c.y - glyph.size.height / 2f),
                        color = Color.Unspecified,
                    )

                    // Значки статусов над фигурой.
                    val marks = Status.entries.filter { unit.has(it) }
                    marks.forEachIndexed { i, status ->
                        val mark = measurer.measure(
                            status.glyph,
                            TextStyle(
                                color = if (status == Status.POISON) Moss else Arcane,
                                fontSize = (cell * 0.26f).toSp(),
                                fontWeight = FontWeight.Bold,
                            ),
                        )
                        drawText(
                            mark,
                            topLeft = Offset(
                                c.x - mark.size.width / 2f + (i - (marks.size - 1) / 2f) * cell * 0.26f,
                                c.y - cell * 0.52f,
                            ),
                            alpha = alpha,
                        )
                    }

                    // Полоска здоровья с «тающим» хвостом недавнего урона.
                    val barW = cell * 0.72f
                    val barH = cell * 0.08f
                    val barTl = Offset(c.x - barW / 2f, c.y + cell * 0.36f)
                    val ratio = (unit.hp.toFloat() / unit.maxHp).coerceIn(0f, 1f)
                    drawRect(Ink.copy(alpha = 0.85f * alpha), barTl, Size(barW, barH))
                    if (anim.popupValue < 0 && anim.popup.value < 1f) {
                        val before = ((unit.hp - anim.popupValue).toFloat() / unit.maxHp).coerceIn(0f, 1f)
                        drawRect(
                            Bone.copy(alpha = (1f - anim.popup.value) * 0.7f * alpha),
                            barTl + Offset(barW * ratio, 0f),
                            Size(barW * (before - ratio), barH),
                        )
                    }
                    drawRect(tinted.copy(alpha = alpha), barTl, Size(barW * ratio, barH))

                    // Всплывающее число урона или лечения.
                    if (anim.popup.value < 1f && anim.popupValue != 0) {
                        val progress = anim.popup.value
                        val text = if (anim.popupValue > 0) "+${anim.popupValue}" else "${anim.popupValue}"
                        val popup = measurer.measure(
                            text,
                            TextStyle(
                                color = if (anim.popupValue > 0) Moss else Blood,
                                fontSize = (cell * 0.28f).toSp(),
                                fontWeight = FontWeight.Bold,
                            ),
                        )
                        drawText(
                            popup,
                            topLeft = Offset(
                                c.x - popup.size.width / 2f,
                                c.y - cell * (0.45f + 0.5f * progress),
                            ),
                            alpha = (1f - progress).coerceIn(0f, 1f),
                        )
                    }
                }

                // Туман поверх всего: скрытые клетки гаснут вместе с тем, что на них.
                visible?.let { seenCells ->
                    for (y in 0 until battle.height) {
                        for (x in 0 until battle.width) {
                            val p = Pos(x, y)
                            if (p !in seenCells) {
                                drawRect(Field.fog.copy(alpha = 0.9f), cellTopLeft(p), Size(cell, cell))
                            }
                        }
                    }
                }
            }

            androidx.compose.animation.AnimatedVisibility(
                visible = scouting,
                enter = fadeIn(tween(200)),
                exit = fadeOut(tween(600)),
                modifier = Modifier.align(Alignment.TopCenter),
            ) {
                Text(
                    "Разведка: запоминайте поле",
                    style = MaterialTheme.typography.labelMedium,
                    color = Ember,
                    modifier = Modifier
                        .padding(6.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Ink.copy(alpha = 0.8f))
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }
        }

        ActionPanel(
            battle = battle,
            playerTurn = playerTurn,
            skillMode = skillMode,
            inspected = battle.units.firstOrNull { it.id == inspected },
            onClearInspect = { inspected = null },
            onSkill = {
                val unit = battle.active ?: return@ActionPanel
                val skill = unit.skill
                if (skill != null && skill.target == SkillTarget.SELF) {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    battle.useSkill(unit)
                } else {
                    skillMode = !skillMode
                }
            },
        )
    }

    battle.outcome?.let { outcome ->
        val won = outcome == Outcome.VICTORY
        AlertDialog(
            onDismissRequest = {},
            containerColor = InkRaised,
            title = { Text(if (won) "Поле за нами" else "Отряд разбит", color = if (won) Ember else Blood) },
            text = {
                Text(
                    if (won) {
                        "Раунд ${battle.round}. Раненые доберутся до привала."
                    } else {
                        "Забег окончен."
                    },
                )
            },
            confirmButton = { TextButton(onClick = onFinished) { Text("Дальше") } },
        )
    }
}

/** Держит анимации одного бойца в согласии с игровой моделью. */
@Composable
private fun UnitAnimator(unit: Combatant, anim: UnitAnim, haptics: HapticFeedback) {
    LaunchedEffect(unit.pos) {
        val target = unit.pos.toOffset()
        if (anim.cell.value != target) {
            anim.cell.animateTo(target, tween(260, easing = FastOutSlowInEasing))
        }
    }
    LaunchedEffect(unit.hp) {
        val previous = anim.lastHp
        anim.lastHp = unit.hp
        if (previous < 0 || unit.hp == previous) return@LaunchedEffect

        anim.popupValue = unit.hp - previous
        launch {
            anim.popup.snapTo(0f)
            anim.popup.animateTo(1f, tween(850))
        }
        if (unit.hp < previous) {
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            launch {
                anim.flash.snapTo(1f)
                anim.flash.animateTo(0f, tween(340))
            }
            launch {
                anim.punch.snapTo(1.28f)
                anim.punch.animateTo(1f, spring(dampingRatio = 0.32f, stiffness = Spring.StiffnessMedium))
            }
        }
    }
    LaunchedEffect(unit.alive) {
        if (!unit.alive) anim.fade.animateTo(0f, tween(450))
    }
}

@Composable
private fun TurnBar(battle: BattleState) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("Раунд ${battle.round}", style = MaterialTheme.typography.titleSmall, color = Steel)
        val alive = battle.units.count { it.team == Team.ENEMY && it.alive }
        Text(
            "Врагов: $alive",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.error,
        )
    }
}

/** Очередь ходов: кто сейчас и кто следом. */
@Composable
private fun TurnOrderStrip(battle: BattleState) {
    val order = battle.turnOrder
    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        items(order, key = { it.id }) { unit ->
            val isActive = unit.id == order.firstOrNull()?.id
            val tint by animateColorAsState(
                targetValue = when {
                    isActive -> Ember
                    unit.team == Team.PLAYER -> Moss.copy(alpha = 0.55f)
                    else -> Blood.copy(alpha = 0.55f)
                },
                animationSpec = tween(260),
                label = "chip",
            )
            Box(
                modifier = Modifier
                    .animateItem()
                    .scale(if (isActive) 1f else 0.82f)
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(tint),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    unit.type.glyph,
                    color = Ink,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                )
            }
        }
    }
}

@Composable
private fun ActionPanel(
    battle: BattleState,
    playerTurn: Boolean,
    skillMode: Boolean,
    inspected: Combatant?,
    onClearInspect: () -> Unit,
    onSkill: () -> Unit,
) {
    val active = battle.active
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        AnimatedContent(
            targetState = active?.id to playerTurn,
            transitionSpec = {
                (fadeIn(tween(220)) + slideInVertically { it / 4 }) togetherWith fadeOut(tween(140))
            },
            label = "active",
        ) { (_, isPlayer) ->
            val unit = battle.active
            Column {
                if (unit == null) {
                    Text("...", color = Steel)
                } else {
                    Text(
                        if (isPlayer) "Ходит: ${unit.type.name}" else "Ход врага: ${unit.type.name}",
                        style = MaterialTheme.typography.titleMedium,
                        color = if (isPlayer) Ember else Blood,
                    )
                    Text(
                        buildString {
                            append("HP ${unit.hp}/${unit.maxHp} · ⚔${unit.attack}")
                            if (unit.shield > 0) append(" · щит ${unit.shield}")
                            append(" · дальность ${unit.type.range} · обзор ${unit.type.vision}")
                            append(" · шагов ${battle.movesLeft}")
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = Steel,
                    )
                    if (unit.type.ability != Ability.NONE && isPlayer) {
                        Text(
                            abilityHint(unit.type.ability),
                            style = MaterialTheme.typography.labelSmall,
                            color = Bone,
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        Box(Modifier.height(34.dp)) {
            androidx.compose.animation.AnimatedVisibility(
                visible = inspected != null,
                enter = fadeIn(tween(180)) + slideInVertically { it / 2 },
                exit = fadeOut(tween(120)),
            ) {
                inspected?.let { unit ->
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(InkRaised)
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "${unit.type.name}: ${unit.hp}/${unit.maxHp} · ⚔${unit.attack} · " +
                                "дальность ${unit.type.range} · ход ${unit.type.move}",
                            style = MaterialTheme.typography.labelMedium,
                            color = Bone,
                        )
                        Spacer(Modifier.padding(horizontal = 4.dp))
                        TextButton(onClick = onClearInspect) { Text("×") }
                    }
                }
            }
        }

        Spacer(Modifier.height(4.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val skill = active?.skill
            if (skill != null) {
                Button(
                    onClick = onSkill,
                    enabled = playerTurn && battle.outcome == null && active.skillReady &&
                        (skill.target == SkillTarget.SELF || battle.skillTargets().isNotEmpty()),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (skillMode) Arcane else MaterialTheme.colorScheme.primary,
                        contentColor = Ink,
                    ),
                ) {
                    Text(
                        when {
                            !active.skillReady -> "${skill.name} · ${active.cooldown}"
                            skillMode -> "Отмена"
                            else -> skill.name
                        },
                    )
                }
            }
            Button(
                onClick = { battle.endTurn() },
                enabled = playerTurn && battle.outcome == null,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = Bone,
                ),
            ) { Text("Конец хода") }
        }

        if (playerTurn && active?.skill != null && active.skillReady) {
            Text(
                active.skill!!.description,
                style = MaterialTheme.typography.labelSmall,
                color = Arcane,
                modifier = Modifier.padding(top = 4.dp),
            )
        }

        Spacer(Modifier.height(6.dp))
        Box(Modifier.height(30.dp)) {
            Column {
                battle.log.takeLast(2).forEach { line ->
                    key(line) {
                        Text(
                            line,
                            style = MaterialTheme.typography.labelSmall,
                            color = Steel,
                        )
                    }
                }
            }
        }
    }
}

private fun abilityHint(ability: Ability) = when (ability) {
    Ability.HEAL -> "Лечит союзника вместо удара"
    Ability.SPLASH -> "Задевает соседей цели"
    Ability.FLANK -> "+50% урона, если рядом с целью свой"
    Ability.PIERCE -> "Пробивает второго на линии"
    Ability.NONE -> ""
}

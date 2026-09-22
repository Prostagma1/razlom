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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.PaddingValues
import io.github.prostagma1.razlom.ui.theme.Gold
import io.github.prostagma1.razlom.ui.theme.InkLine
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.border
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.clickable
import androidx.compose.animation.AnimatedVisibility
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
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

@Composable
fun BattleScreen(battle: BattleState, onFinished: () -> Unit) {
    val measurer = rememberTextMeasurer()
    val haptics = LocalHapticFeedback.current

    val anims = remember(battle) { mutableMapOf<Int, UnitAnim>() }
    battle.units.forEach { anims.getOrPut(it.id) { UnitAnim(it.pos) } }
    battle.units.forEach { unit ->
        key(unit.id) { UnitAnimator(unit, anims.getValue(unit.id), haptics, battle) }
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
    var selected by remember(battle) { mutableStateOf<Int?>(null) }
    var showLog by remember(battle) { mutableStateOf(false) }

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
    LaunchedEffect(active?.id, battle.round) {
        skillMode = false
        selected = null
    }

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

    val healer = active?.type?.ability == Ability.HEAL
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
                        // Клетка под пальцем, или null — если ткнули мимо доски.
                        fun cellAt(tap: Offset): Pos? {
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
                                return null
                            }
                            return Pos(gx, gy)
                        }

                        detectTapGestures(
                            // Долгий тап ничего не тратит: показывает расчёт и карточку.
                            onLongPress = { tap ->
                                val unit = cellAt(tap)?.let { battle.unitAt(it) }
                                if (unit == null) {
                                    inspected = null
                                    selected = null
                                    return@detectTapGestures
                                }
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                inspected = unit.id
                                val aimable = playerTurn && battle.outcome == null && (
                                    if (skillMode) {
                                        skillAims.any { it.id == unit.id }
                                    } else {
                                        active != null && battle.canTarget(active, unit)
                                    }
                                    )
                                selected = if (aimable) unit.id else null
                            },
                            onTap = { tap ->
                                val p = cellAt(tap) ?: return@detectTapGestures
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
                                        selected = null
                                    }

                                    unit != null && battle.canTarget(active, unit) -> {
                                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                        inspected = null
                                        selected = null
                                        battle.act(unit)
                                    }

                                    unit != null -> {
                                        inspected = unit.id
                                        selected = null
                                    }

                                    p in reachable -> {
                                        inspected = null
                                        selected = null
                                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        battle.moveActiveTo(p)
                                    }

                                    else -> {
                                        inspected = null
                                        selected = null
                                    }
                                }
                            },
                        )
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
                            // Цель лечения — свой, красить его как врага нельзя.
                            val mark = if (healer) Moss else Blood
                            val fill = if (healer) Moss.copy(alpha = 0.18f + 0.2f * pulse) else Field.threat.copy(alpha = 0.45f + 0.4f * pulse)
                            drawRect(fill, tl, Size(cell, cell))
                            drawRect(mark.copy(alpha = 0.5f + 0.5f * pulse), tl, Size(cell, cell), style = Stroke(3f))
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
                    if (unit.id == selected) {
                        drawCircle(
                            Ember.copy(alpha = 0.7f + 0.3f * pulse),
                            radius = radius + cell * 0.16f,
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

                    // Полоска здоровья с числом и «тающим» хвостом недавнего урона.
                    // Здоровье выбрасывается костями, так что одной доли мало — нужно число.
                    val barW = cell * 0.8f
                    val barH = cell * 0.2f
                    val barTl = Offset(c.x - barW / 2f, c.y + cell * 0.26f)
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
                    val hpText = measurer.measure(
                        "${unit.hp}",
                        TextStyle(
                            color = Bone,
                            fontSize = (barH * 0.86f).toSp(),
                            fontWeight = FontWeight.Bold,
                        ),
                    )
                    drawText(
                        hpText,
                        topLeft = Offset(
                            c.x - hpText.size.width / 2f,
                            barTl.y + (barH - hpText.size.height) / 2f,
                        ),
                        alpha = alpha,
                    )

                    // Всплывающее число урона или лечения.
                    if (anim.popup.value < 1f && anim.popupValue != 0) {
                        val progress = anim.popup.value
                        val sign = if (anim.popupValue > 0) "+${anim.popupValue}" else "${anim.popupValue}"
                        val text = if (anim.popupCrit) "$sign!" else sign
                        val popup = measurer.measure(
                            text,
                            TextStyle(
                                color = when {
                                    anim.popupCrit -> Ember
                                    anim.popupValue > 0 -> Moss
                                    else -> Blood
                                },
                                fontSize = (cell * if (anim.popupCrit) 0.42f else 0.28f).toSp(),
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

            DetailOverlay(
                battle = battle,
                playerTurn = playerTurn,
                skillMode = skillMode,
                selected = battle.units.firstOrNull { it.id == selected },
                inspected = battle.units.firstOrNull { it.id == inspected },
                showLog = showLog,
                onClearInspect = {
                    inspected = null
                    selected = null
                },
                onAttack = { target ->
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    inspected = null
                    if (skillMode) {
                        battle.useSkill(target)
                        skillMode = false
                    } else {
                        battle.act(target)
                    }
                    selected = null
                },
            )
        }

        ActionPanel(
            battle = battle,
            playerTurn = playerTurn,
            skillMode = skillMode,
            showLog = showLog,
            onToggleLog = { showLog = !showLog },
            onUndo = {
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                battle.undoMove()
            },
            onSkill = {
                val unit = battle.active ?: return@ActionPanel
                val skill = unit.skill
                selected = null
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
                        buildString {
                            append("Раунд ${battle.round}. Раненые доберутся до привала.")
                            if (battle.playerCrits > 0) append("\nКритов за бой: ${battle.playerCrits}.")
                        }
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
private fun UnitAnimator(
    unit: Combatant,
    anim: UnitAnim,
    haptics: HapticFeedback,
    battle: BattleState,
) {
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
        // Крит — только если по этому бойцу только что прошёл удар-крит.
        // Раньше флажок брался из последнего броска в бою, и яд на следующем
        // ходу всплывал золотым «−3!», будто крит продолжается.
        anim.popupCrit = unit.critsTaken != anim.shownCrits
        anim.shownCrits = unit.critsTaken
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

/**
 * Нижняя панель. Её высота не меняется ни между ходами, ни при открытых
 * подсказках: всё, что появляется и исчезает, живёт поверх поля. Иначе поле
 * сжимается, клетки съезжают, и тап попадает не туда.
 */
@Composable
private fun ActionPanel(
    battle: BattleState,
    playerTurn: Boolean,
    skillMode: Boolean,
    showLog: Boolean,
    onToggleLog: () -> Unit,
    onUndo: () -> Unit,
    onSkill: () -> Unit,
) {
    val active = battle.active
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
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
                Text(
                    when {
                        unit == null -> "…"
                        isPlayer -> "Ходит: ${unit.type.name}"
                        else -> "Ход врага: ${unit.type.name}"
                    },
                    style = MaterialTheme.typography.titleMedium,
                    color = if (isPlayer) Ember else Blood,
                    maxLines = 1,
                )
                // Одна строка: подробности — в карточке по долгому нажатию.
                Text(
                    if (unit == null) {
                        ""
                    } else {
                        buildString {
                            append("HP ${unit.hp}/${unit.maxHp} · ⚔${unit.damage}")
                            if (unit.shield > 0) append(" · щит ${unit.shield}")
                            if (isPlayer) append(" · шагов ${battle.movesLeft}")
                        }
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = Steel,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        // Держим бросок ещё два хода, чтобы успеть прочитать, а потом убираем:
        // иначе золотой «КРИТ» висел на экране, пока кто-нибудь снова не бросит.
        RollStrip(battle.lastRoll?.takeIf { battle.turnCount - it.turn <= 2 })

        // Строка-подсказка всегда одной высоты: меняется текст, а не раскладка.
        Text(
            when {
                battle.outcome != null -> ""
                !playerTurn -> "Враг ходит…"
                skillMode -> active?.skill?.description.orEmpty()
                else -> "Тап — шаг или удар · удержите бойца — расчёт и карточка"
            },
            style = MaterialTheme.typography.labelSmall,
            color = if (skillMode && playerTurn) Arcane else Steel.copy(alpha = 0.8f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 2.dp),
        )

        Spacer(Modifier.height(8.dp))
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
                        maxLines = 1,
                    )
                }
            }
            if (playerTurn && battle.canUndoMove) {
                Button(
                    onClick = onUndo,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = Bone,
                    ),
                    contentPadding = PaddingValues(horizontal = 16.dp),
                ) {
                    // В этом шрифте стрелка отмены мелкая — без размера она теряется в кнопке.
                    Text("↶", fontSize = 24.sp, fontWeight = FontWeight.Bold)
                }
            }
            Button(
                onClick = { battle.endTurn() },
                enabled = playerTurn && battle.outcome == null,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = Bone,
                ),
            ) { Text("Конец хода", maxLines = 1) }
        }

        // Последняя строка журнала, по нажатию — весь журнал поверх поля.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 6.dp)
                .clip(RoundedCornerShape(6.dp))
                .clickable(onClick = onToggleLog)
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                battle.log.lastOrNull().orEmpty(),
                style = MaterialTheme.typography.labelSmall,
                color = Steel,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text(
                if (showLog) "журнал ▴" else "журнал ▾",
                style = MaterialTheme.typography.labelSmall,
                color = Ember,
            )
        }
    }
}

/**
 * Карточка, расчёт удара и журнал — поверх поля. Прячутся в ту половину,
 * где нет бойца, о котором речь: держишь своего внизу — карточка сверху.
 */
@Composable
private fun BoxScope.DetailOverlay(
    battle: BattleState,
    playerTurn: Boolean,
    skillMode: Boolean,
    selected: Combatant?,
    inspected: Combatant?,
    showLog: Boolean,
    onClearInspect: () -> Unit,
    onAttack: (Combatant) -> Unit,
) {
    val focus = selected ?: inspected
    val atTop = focus == null || focus.pos.y >= battle.height / 2
    val forecast = selected?.let { battle.forecast(it, withSkill = skillMode) }

    androidx.compose.animation.AnimatedVisibility(
        visible = showLog || inspected != null || forecast != null,
        enter = fadeIn(tween(160)) + slideInVertically(tween(200)) { if (atTop) -it / 6 else it / 6 },
        exit = fadeOut(tween(120)),
        modifier = Modifier
            .align(if (atTop) Alignment.TopCenter else Alignment.BottomCenter)
            .padding(8.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (showLog) LogPanel(battle)
            inspected?.let { UnitCard(it.facts(), onClose = onClearInspect) }
            if (selected != null && forecast != null) {
                ForecastRow(
                    target = selected,
                    forecast = forecast,
                    actionName = when {
                        skillMode -> battle.active?.skill?.name ?: "Применить"
                        forecast.healing -> "Лечить"
                        else -> "Ударить"
                    },
                    enabled = playerTurn && battle.outcome == null,
                    onAttack = { onAttack(selected) },
                )
            }
        }
    }
}

@Composable
private fun ForecastRow(
    target: Combatant,
    forecast: BattleState.Forecast,
    actionName: String,
    enabled: Boolean,
    onAttack: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(InkRaised)
            .border(1.dp, if (forecast.lethal) Ember else InkLine, RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                "${target.type.name} · ${target.hp}/${target.maxHp}",
                style = MaterialTheme.typography.labelLarge,
                color = Bone,
            )
            Text(
                forecastText(forecast, target.hp),
                style = MaterialTheme.typography.labelMedium,
                color = when {
                    forecast.healing -> Moss
                    forecast.lethal -> Ember
                    else -> Steel
                },
                fontWeight = if (forecast.lethal) FontWeight.Bold else FontWeight.Normal,
            )
            if (forecast.critChance > 0.0 && forecast.dice != "—") {
                Text(
                    "💥 крит ${percent(forecast.critChance)} → " +
                        (if (forecast.healing) "+" else "−") + forecast.critAmount,
                    style = MaterialTheme.typography.labelSmall,
                    color = Ember,
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        Button(onClick = onAttack, enabled = enabled) { Text(actionName, maxLines = 1) }
    }
}

/** «6%», а для совсем малых шансов — «<1%», чтобы не писать «0%» про возможное. */
private fun percent(p: Double): String {
    val v = (p * 100).roundToInt()
    return if (v == 0 && p > 0.0) "<1%" else "$v%"
}

/** Выпавшие кости последнего удара: грани квадратиками, бонус и итог. */
@Composable
private fun RollStrip(report: BattleState.RollReport?) {
    AnimatedContent(
        targetState = report,
        contentKey = { it?.seq },
        transitionSpec = {
            (fadeIn(tween(180)) + scaleIn(tween(220), initialScale = 0.85f)) togetherWith
                fadeOut(tween(120))
        },
        label = "roll",
    ) { r ->
        if (r == null) {
            Spacer(Modifier.height(30.dp))
            return@AnimatedContent
        }
        // Крит подсвечивается золотом: его должно быть видно краем глаза.
        val edge = when {
            r.crit -> Gold
            r.friendly -> Ember
            else -> Blood
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(30.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                "${r.who} ${r.roll.dice}:",
                style = MaterialTheme.typography.labelMedium,
                color = Steel,
                maxLines = 1,
            )
            r.roll.faces.forEachIndexed { i, face ->
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(RoundedCornerShape(5.dp))
                        .background(if (r.crit) Gold.copy(alpha = 0.18f) else InkRaised)
                        .border(if (r.crit) 2.dp else 1.dp, edge, RoundedCornerShape(5.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "$face",
                        style = MaterialTheme.typography.labelLarge,
                        color = if (r.crit) Gold else Bone,
                        fontWeight = FontWeight.Bold,
                    )
                }
                // Переброшенная счастливыми костями грань.
                if (i in r.roll.rerolled) {
                    Text("↻", style = MaterialTheme.typography.labelSmall, color = Moss)
                }
            }
            val bonus = r.roll.dice.bonus
            if (bonus != 0) {
                Text(
                    if (bonus > 0) "+$bonus" else "$bonus",
                    style = MaterialTheme.typography.labelLarge,
                    color = Steel,
                )
            }
            Text(
                buildString {
                    append("= ${r.roll.total}")
                    if (r.crit) append(" ×${r.critMultiplier}")
                    if (r.amount != r.roll.total * r.critMultiplier) append(" → ${r.amount}")
                    else if (r.crit) append(" = ${r.amount}")
                },
                style = MaterialTheme.typography.labelLarge,
                color = when {
                    r.crit -> Gold
                    r.healing -> Moss
                    else -> edge
                },
                fontWeight = FontWeight.Bold,
                maxLines = 1,
            )
            if (r.crit) {
                Text(
                    "КРИТ",
                    style = MaterialTheme.typography.labelMedium,
                    color = Ink,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(Gold)
                        .padding(horizontal = 5.dp, vertical = 1.dp),
                )
            }
        }
    }
}

/** Весь журнал боя: прокручивается, свежие записи внизу. */
@Composable
private fun LogPanel(battle: BattleState) {
    val state = rememberLazyListState()
    LaunchedEffect(battle.log.size) {
        if (battle.log.isNotEmpty()) state.animateScrollToItem(battle.log.lastIndex)
    }

    LazyColumn(
        state = state,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 170.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(InkRaised.copy(alpha = 0.96f))
            .padding(horizontal = 10.dp, vertical = 6.dp),
    ) {
        if (battle.log.isEmpty()) {
            item {
                Text(
                    "Пока тихо — ходов ещё не было",
                    style = MaterialTheme.typography.labelMedium,
                    color = Steel.copy(alpha = 0.7f),
                )
            }
        }
        items(battle.log.toList()) { line ->
            val isRound = line.startsWith("—")
            Text(
                line,
                style = MaterialTheme.typography.labelMedium,
                color = if (isRound) Ember else Steel,
                fontWeight = if (isRound) FontWeight.Bold else FontWeight.Normal,
                modifier = Modifier.padding(vertical = 1.dp),
            )
        }
    }
}

private fun forecastText(f: BattleState.Forecast, targetHp: Int): String = buildString {
    fun span(a: Int, b: Int) = if (a == b) "$a" else "$a…$b"

    if (f.dice == "—") {
        append(f.extra.ifEmpty { "без броска" })
        return@buildString
    }
    append("🎲 ${f.dice} · ")
    if (f.healing) {
        append("вылечит на ${span(f.plainMin, f.plainMax)}")
        if (f.extra.isNotEmpty()) append(" · ${f.extra}")
        return@buildString
    }
    if (f.absorbed > 0) append("щит съест до ${f.absorbed} · ")
    // Разброс обычного удара; крит показан отдельной строкой.
    append("−${span(f.plainMin, f.plainMax)} HP")
    when {
        f.lethal -> append(" · СМЕРТЕЛЬНО")
        f.lethalChance > 0.0 -> append(" · убьёт с шансом ${percent(f.lethalChance)}")
        else -> append(
            " · останется ${span((targetHp - f.plainMax).coerceAtLeast(0), targetHp - f.plainMin)}",
        )
    }
    if (f.splashMax > 0) append(" · соседям −${span(f.splashMin, f.splashMax)}")
    if (f.extra.isNotEmpty()) append(" · ${f.extra}")
}

private fun abilityHint(ability: Ability) = when (ability) {
    Ability.HEAL -> "Лечит союзника вместо удара"
    Ability.SPLASH -> "Задевает соседей цели"
    Ability.FLANK -> "+50% урона, если рядом с целью свой"
    Ability.PIERCE -> "Пробивает второго на линии"
    Ability.NONE -> ""
}

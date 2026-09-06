package io.github.prostagma1.razlom.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.prostagma1.razlom.game.Game
import io.github.prostagma1.razlom.game.MapNode
import io.github.prostagma1.razlom.game.NodeKind
import io.github.prostagma1.razlom.ui.theme.Bone
import io.github.prostagma1.razlom.ui.theme.Ember
import io.github.prostagma1.razlom.ui.theme.InkLine
import io.github.prostagma1.razlom.ui.theme.InkRaised
import io.github.prostagma1.razlom.ui.theme.Steel

private fun glyphFor(kind: NodeKind) = when (kind) {
    NodeKind.BATTLE -> "×"
    NodeKind.ELITE -> "‡"
    NodeKind.REST -> "≈"
    NodeKind.RECRUIT -> "+"
    NodeKind.SHOP -> "◈"
    NodeKind.BOSS -> "Ω"
}

@Composable
fun MapScreen(game: Game, onEnter: (Int) -> Unit, onMenu: () -> Unit = {}) {
    val measurer = rememberTextMeasurer()
    val haptics = LocalHapticFeedback.current
    val map = game.map
    val labelStyle = TextStyle(color = Bone, fontSize = 20.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
    val captionStyle = TextStyle(color = Steel, fontSize = 10.sp)

    // Карта проявляется рядами снизу вверх, когда забег только начался.
    val reveal = remember(map) { Animatable(0f) }
    LaunchedEffect(map) { reveal.animateTo(1f, tween(900)) }

    val pulse by rememberInfiniteTransition(label = "map").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1200), RepeatMode.Reverse),
        label = "pulse",
    )

    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                game.notice.ifEmpty { "Выберите следующий переход" },
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f, fill = false),
            )
            Spacer(Modifier.width(8.dp))
            GoldTag(game.gold)
            TextButton(onClick = onMenu) {
                Text("Меню", style = MaterialTheme.typography.labelMedium)
            }
        }

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(horizontal = 24.dp, vertical = 8.dp)
                .pointerInput(map, game.available.toList()) {
                    detectTapGestures { tap ->
                        val rowH = size.height / map.depth.toFloat()
                        val colW = size.width / 3f
                        val radius = minOf(rowH, colW) * 0.28f
                        map.nodes.values.firstOrNull { node ->
                            val c = center(node, map.depth, rowH, colW)
                            (tap - c).getDistance() <= radius * 1.7f && node.id in game.available
                        }?.let {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            onEnter(it.id)
                        }
                    }
                },
        ) {
            val rowH = size.height / map.depth.toFloat()
            val colW = size.width / 3f
            val radius = minOf(rowH, colW) * 0.28f

            /** Насколько ряд уже «проявился»: 0 — ещё нет, 1 — полностью. */
            fun rowReveal(row: Int) =
                (reveal.value * map.depth - row).coerceIn(0f, 1f)

            map.nodes.values.forEach { node ->
                val from = center(node, map.depth, rowH, colW)
                val show = rowReveal(node.row + 1)
                if (show <= 0f) return@forEach
                node.next.forEach { id ->
                    val target = map.nodes[id] ?: return@forEach
                    val to = center(target, map.depth, rowH, colW)
                    val walked = node.id in game.visited && target.id in game.visited
                    drawLine(
                        color = when {
                            walked -> Ember.copy(alpha = 0.65f)
                            target.id in game.available -> Ember.copy(alpha = 0.25f + 0.35f * pulse)
                            else -> InkLine
                        },
                        start = from,
                        end = from + (to - from) * show,
                        strokeWidth = if (walked) 5f else 3f,
                    )
                }
            }

            map.nodes.values.forEach { node ->
                val show = rowReveal(node.row)
                if (show <= 0f) return@forEach

                val c = center(node, map.depth, rowH, colW)
                val open = node.id in game.available
                val done = node.id in game.visited
                val r = radius * show * if (open) 1f + 0.06f * pulse else 1f

                if (open) {
                    // Тлеющий ореол вокруг доступного перехода.
                    drawCircle(Ember.copy(alpha = 0.12f + 0.12f * pulse), radius = r * 1.9f, center = c)
                }
                drawCircle(
                    color = when {
                        done -> InkLine
                        open -> InkRaised
                        else -> InkRaised.copy(alpha = 0.6f)
                    },
                    radius = r,
                    center = c,
                )
                drawCircle(
                    color = when {
                        open -> Ember
                        done -> Steel.copy(alpha = 0.5f)
                        else -> InkLine
                    },
                    radius = r,
                    center = c,
                    style = Stroke(width = if (open) 5f else 2f),
                )

                val glyph = measurer.measure(glyphFor(node.kind), labelStyle)
                drawText(
                    glyph,
                    topLeft = Offset(c.x - glyph.size.width / 2f, c.y - glyph.size.height / 2f),
                    color = if (open) Ember else Steel.copy(alpha = if (done) 0.5f else 0.8f),
                    alpha = show,
                )

                if (open) {
                    val caption = measurer.measure(node.kind.label, captionStyle)
                    drawText(
                        caption,
                        topLeft = Offset(c.x - caption.size.width / 2f, c.y + r + 6f),
                        color = Color.Unspecified,
                        alpha = show,
                    )
                }
            }

            // Где отряд стоит сейчас.
            game.visited.lastOrNull()?.let { id ->
                map.nodes[id]?.let { node ->
                    val c = center(node, map.depth, rowH, colW)
                    drawCircle(
                        Bone.copy(alpha = 0.5f + 0.4f * pulse),
                        radius = radius * (1.25f + 0.1f * pulse),
                        center = c,
                        style = Stroke(2f),
                    )
                }
            }
        }

        PartyBar(game.party)
        if (game.relics.isNotEmpty()) {
            Text(
                "Реликвии: " + game.relics.joinToString(", ") { it.title },
                style = MaterialTheme.typography.labelSmall,
                color = Ember,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }
        Text(
            "× схватка   ‡ логово   ≈ привал   + наёмники   ◈ лавка   Ω Пожиратель",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
    }
}

/** Ряд 0 — внизу экрана, босс — наверху. */
private fun center(node: MapNode, depth: Int, rowH: Float, colW: Float) = Offset(
    x = colW * (node.col + 0.5f),
    y = rowH * (depth - 1 - node.row + 0.5f),
)

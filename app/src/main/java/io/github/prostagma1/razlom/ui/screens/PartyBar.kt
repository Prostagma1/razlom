package io.github.prostagma1.razlom.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.prostagma1.razlom.game.Hero
import io.github.prostagma1.razlom.ui.theme.Blood
import io.github.prostagma1.razlom.ui.theme.Ember
import io.github.prostagma1.razlom.ui.theme.Moss

/** Полоска отряда: кто жив, сколько здоровья и урона. */
@Composable
fun PartyBar(party: List<Hero>, modifier: Modifier = Modifier) {
    val alarm by rememberInfiniteTransition(label = "party").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(800), RepeatMode.Reverse),
        label = "alarm",
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        party.forEach { hero ->
            val target = hero.hp.toFloat() / hero.maxHp
            val ratio by animateFloatAsState(target, tween(450), label = "hp")
            val wounded = target <= 0.35f
            val tint by animateColorAsState(
                if (wounded) Blood else Moss,
                tween(400),
                label = "tint",
            )

            Column(
                modifier = Modifier
                    .width(98.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .border(
                        width = 1.dp,
                        color = if (wounded) {
                            Blood.copy(alpha = 0.35f + 0.45f * alarm)
                        } else {
                            Color.Transparent
                        },
                        shape = RoundedCornerShape(10.dp),
                    )
                    .padding(8.dp),
            ) {
                Text(
                    hero.type.name,
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = if (wounded) Ember else MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    "${hero.hp}/${hero.maxHp} · ⚔${hero.attack}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                LinearProgressIndicator(
                    progress = { ratio },
                    color = tint,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                )
            }
        }
    }
}

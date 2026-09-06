package io.github.prostagma1.razlom.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.unit.dp
import io.github.prostagma1.razlom.game.Hero
import io.github.prostagma1.razlom.game.Reward
import io.github.prostagma1.razlom.ui.theme.Ember
import kotlinx.coroutines.delay

@Composable
fun RewardScreen(
    title: String,
    rewards: List<Reward>,
    party: List<Hero>,
    onPick: (Reward) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(title, style = MaterialTheme.typography.headlineSmall, color = Ember)
        Spacer(Modifier.height(4.dp))
        Text(
            "Выберите одну награду",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(20.dp))

        rewards.forEachIndexed { index, reward ->
            RewardCard(reward, index) { onPick(reward) }
        }

        Spacer(Modifier.height(20.dp))
        PartyBar(party)
    }
}

/** Карточки выезжают по очереди — так видно, что выбор именно из трёх. */
@Composable
private fun RewardCard(reward: Reward, index: Int, onPick: () -> Unit) {
    var shown by remember(reward) { mutableStateOf(false) }
    LaunchedEffect(reward) {
        delay(index * 110L)
        shown = true
    }

    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.97f else 1f, tween(120), label = "press")

    AnimatedVisibility(
        visible = shown,
        enter = fadeIn(tween(260)) + slideInHorizontally(tween(320)) { it / 3 },
    ) {
        Card(
            onClick = onPick,
            interactionSource = interaction,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp)
                .scale(scale),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        ) {
            Column(Modifier.padding(16.dp)) {
                Text(reward.title, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(4.dp))
                Text(
                    reward.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

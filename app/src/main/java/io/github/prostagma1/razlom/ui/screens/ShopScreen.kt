package io.github.prostagma1.razlom.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.prostagma1.razlom.game.Game
import io.github.prostagma1.razlom.game.ShopOffer
import io.github.prostagma1.razlom.ui.theme.Blood
import io.github.prostagma1.razlom.ui.theme.Bone
import io.github.prostagma1.razlom.ui.theme.Ember
import io.github.prostagma1.razlom.ui.theme.Steel
import kotlinx.coroutines.delay

@Composable
fun ShopScreen(game: Game, onBuy: (ShopOffer) -> Unit, onLeave: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
    ) {
        Spacer(Modifier.height(8.dp))
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Лавка", style = MaterialTheme.typography.headlineSmall, color = Ember)
            GoldTag(game.gold)
        }
        Text(
            "Торговец разложил товар прямо на камнях",
            style = MaterialTheme.typography.bodySmall,
            color = Steel,
        )

        Spacer(Modifier.height(16.dp))

        Column(Modifier.weight(1f)) {
            game.shop.forEachIndexed { index, offer ->
                OfferCard(
                    offer = offer,
                    index = index,
                    affordable = game.canAfford(offer),
                    onBuy = { onBuy(offer) },
                )
            }
            if (game.shop.isEmpty()) {
                Text(
                    "Всё раскуплено.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Steel,
                )
            }
        }

        RelicBar(game)
        Spacer(Modifier.height(8.dp))
        Button(onClick = onLeave, modifier = Modifier.fillMaxWidth()) { Text("Идти дальше") }
    }
}

@Composable
fun GoldTag(gold: Int) {
    Row(
        Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("◈", color = Ember, fontWeight = FontWeight.Bold)
        Text(
            " $gold",
            style = MaterialTheme.typography.titleSmall,
            color = Bone,
            fontWeight = FontWeight.Bold,
        )
    }
}

/** Реликвии, которые уже собраны в этом забеге. */
@Composable
fun RelicBar(game: Game, modifier: Modifier = Modifier) {
    if (game.relics.isEmpty()) return
    Column(modifier.fillMaxWidth()) {
        Text("Реликвии", style = MaterialTheme.typography.labelMedium, color = Steel)
        game.relics.forEach { relic ->
            Text(
                "◆ ${relic.title} — ${relic.description}",
                style = MaterialTheme.typography.labelSmall,
                color = Bone,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}

@Composable
private fun OfferCard(offer: ShopOffer, index: Int, affordable: Boolean, onBuy: () -> Unit) {
    var shown by remember(offer) { mutableStateOf(false) }
    LaunchedEffect(offer) {
        delay(index * 90L)
        shown = true
    }

    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.97f else 1f, tween(120), label = "press")

    AnimatedVisibility(
        visible = shown,
        enter = fadeIn(tween(240)) + slideInHorizontally(tween(300)) { it / 3 },
        exit = fadeOut(tween(160)) + shrinkVertically(tween(220)),
    ) {
        Card(
            onClick = onBuy,
            enabled = affordable,
            interactionSource = interaction,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 10.dp)
                .scale(scale),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface,
                disabledContainerColor = MaterialTheme.colorScheme.surface,
            ),
        ) {
            Row(
                Modifier.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        offer.title,
                        style = MaterialTheme.typography.titleSmall,
                        color = if (affordable) Bone else Steel.copy(alpha = 0.6f),
                    )
                    Text(
                        offer.description,
                        style = MaterialTheme.typography.labelSmall,
                        color = Steel.copy(alpha = if (affordable) 1f else 0.6f),
                    )
                }
                Text(
                    "◈ ${offer.price}",
                    style = MaterialTheme.typography.titleSmall,
                    color = if (affordable) Ember else Blood,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

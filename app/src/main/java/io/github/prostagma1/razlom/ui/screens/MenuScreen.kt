package io.github.prostagma1.razlom.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.background
import androidx.compose.material3.Button
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
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.prostagma1.razlom.game.Roster
import io.github.prostagma1.razlom.ui.theme.Ember

@Composable
fun MenuScreen(onStart: () -> Unit) {
    val glow by rememberInfiniteTransition(label = "menu").animateFloat(
        initialValue = 0.25f,
        targetValue = 0.75f,
        animationSpec = infiniteRepeatable(tween(2200), RepeatMode.Reverse),
        label = "glow",
    )
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        // Тлеющее пятно за названием.
        Box(
            Modifier
                .fillMaxWidth()
                .height(220.dp)
                .blur(80.dp)
                .background(
                    Brush.radialGradient(
                        listOf(Ember.copy(alpha = glow * 0.35f), MaterialTheme.colorScheme.background),
                    ),
                ),
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(28.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            AnimatedVisibility(shown, enter = fadeIn(tween(700))) {
                Text(
                    "РАЗЛОМ",
                    style = MaterialTheme.typography.displaySmall,
                    color = Ember.copy(alpha = 0.75f + glow * 0.25f),
                )
            }
            Spacer(Modifier.height(12.dp))
            AnimatedVisibility(shown, enter = fadeIn(tween(900)) + slideInVertically { it / 6 }) {
                Text(
                    "Восемь переходов до Пожирателя. Ведите отряд, выбирайте путь, " +
                        "не растеряйте людей по дороге.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
            Spacer(Modifier.height(32.dp))
            AnimatedVisibility(shown, enter = fadeIn(tween(1100)) + slideInVertically { it / 3 }) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Button(onClick = onStart, modifier = Modifier.fillMaxWidth()) {
                        Text("Новый забег")
                    }
                    Spacer(Modifier.height(28.dp))
                    Text(
                        "Стартовый отряд: " + Roster.starting.joinToString { it.name },
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
fun EndScreen(title: String, subtitle: String, onRestart: () -> Unit, onMenu: () -> Unit) {
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(28.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        AnimatedVisibility(shown, enter = fadeIn(tween(600)) + slideInVertically { it / 8 }) {
            Text(title, style = MaterialTheme.typography.headlineMedium, color = Ember)
        }
        Spacer(Modifier.height(12.dp))
        AnimatedVisibility(shown, enter = fadeIn(tween(900))) {
            Text(
                subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
        Spacer(Modifier.height(32.dp))
        AnimatedVisibility(shown, enter = fadeIn(tween(1200)) + slideInVertically { it / 3 }) {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Button(onClick = onRestart, modifier = Modifier.fillMaxWidth()) { Text("Ещё раз") }
                Spacer(Modifier.height(12.dp))
                Button(onClick = onMenu, modifier = Modifier.fillMaxWidth()) { Text("В меню") }
            }
        }
    }
}

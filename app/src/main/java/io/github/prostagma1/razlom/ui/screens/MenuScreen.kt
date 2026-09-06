package io.github.prostagma1.razlom.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.prostagma1.razlom.game.Game
import io.github.prostagma1.razlom.game.Squad
import io.github.prostagma1.razlom.ui.theme.Bone
import io.github.prostagma1.razlom.ui.theme.Ember
import io.github.prostagma1.razlom.ui.theme.Moss
import io.github.prostagma1.razlom.ui.theme.Steel

@Composable
fun MenuScreen(game: Game, onContinue: () -> Unit, onStart: (Squad) -> Unit) {
    val glow by rememberInfiniteTransition(label = "menu").animateFloat(
        initialValue = 0.25f,
        targetValue = 0.75f,
        animationSpec = infiniteRepeatable(tween(2200), RepeatMode.Reverse),
        label = "glow",
    )
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }

    val squads = game.profile.squads
    var chosen by remember(squads.size) { mutableStateOf(squads.first()) }

    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(240.dp)
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
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(24.dp))
            AnimatedVisibility(shown, enter = fadeIn(tween(700))) {
                Text(
                    "РАЗЛОМ",
                    style = MaterialTheme.typography.displaySmall,
                    color = Ember.copy(alpha = 0.75f + glow * 0.25f),
                )
            }
            Spacer(Modifier.height(10.dp))
            AnimatedVisibility(shown, enter = fadeIn(tween(900)) + slideInVertically { it / 6 }) {
                Text(
                    "Восемь переходов до Пожирателя. Ведите отряд, выбирайте путь, " +
                        "не растеряйте людей по дороге.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Steel,
                    textAlign = TextAlign.Center,
                )
            }

            Spacer(Modifier.height(24.dp))

            AnimatedVisibility(shown, enter = fadeIn(tween(1100)) + slideInVertically { it / 4 }) {
                Column(Modifier.fillMaxWidth()) {
                    if (game.hasSavedRun) {
                        Button(onClick = onContinue, modifier = Modifier.fillMaxWidth()) {
                            Text("Продолжить забег")
                        }
                        Spacer(Modifier.height(10.dp))
                    }

                    Text(
                        "Стартовый отряд",
                        style = MaterialTheme.typography.labelLarge,
                        color = Steel,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                    squads.forEach { squad ->
                        SquadCard(squad, squad.id == chosen.id) { chosen = squad }
                    }
                    LockedSquads(game)

                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = { onStart(chosen) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = if (game.hasSavedRun) {
                            ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                contentColor = Bone,
                            )
                        } else {
                            ButtonDefaults.buttonColors()
                        },
                    ) {
                        Text(if (game.hasSavedRun) "Начать заново" else "Новый забег")
                    }
                    if (game.hasSavedRun) {
                        Text(
                            "Новый забег сотрёт сохранение текущего",
                            style = MaterialTheme.typography.labelSmall,
                            color = Steel,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 6.dp),
                        )
                    }

                    Spacer(Modifier.height(20.dp))
                    ProgressPanel(game)
                    Spacer(Modifier.height(24.dp))
                }
            }
        }
    }
}

@Composable
private fun SquadCard(squad: Squad, selected: Boolean, onPick: () -> Unit) {
    val border by animateColorAsState(
        if (selected) Ember else Color.Transparent,
        tween(220),
        label = "border",
    )
    Column(
        Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, border, RoundedCornerShape(10.dp))
            .clickable(onClick = onPick)
            .padding(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                squad.name,
                style = MaterialTheme.typography.titleSmall,
                color = if (selected) Ember else Bone,
            )
            Spacer(Modifier.fillMaxWidth(0.02f))
            Text(
                "  " + squad.members.joinToString(" · ") { it.name },
                style = MaterialTheme.typography.labelSmall,
                color = Steel,
            )
        }
        Text(
            squad.hint,
            style = MaterialTheme.typography.labelSmall,
            color = Steel,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

@Composable
private fun LockedSquads(game: Game) {
    val locked = io.github.prostagma1.razlom.game.Squads.all
        .filter { game.profile.bestRow < it.requiredRow }
    locked.forEach { squad ->
        Text(
            "🔒 ${squad.name} — дойти до ${squad.requiredRow}-го перехода",
            style = MaterialTheme.typography.labelSmall,
            color = Steel.copy(alpha = 0.7f),
            modifier = Modifier.padding(bottom = 4.dp),
        )
    }
}

@Composable
private fun ProgressPanel(game: Game) {
    val profile = game.profile
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(12.dp),
    ) {
        Text("Летопись", style = MaterialTheme.typography.titleSmall, color = Ember)
        Spacer(Modifier.height(6.dp))
        Text(
            "Забегов: ${profile.runsPlayed} · побед: ${profile.runsWon} · " +
                "лучший переход: ${profile.bestRow}",
            style = MaterialTheme.typography.labelMedium,
            color = Steel,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            "Открыты: " + io.github.prostagma1.razlom.game.Roster.recruitable
                .filter { it.id in profile.unlockedUnits }
                .joinToString(", ") { it.name },
            style = MaterialTheme.typography.labelSmall,
            color = Moss,
        )
    }
}

@Composable
fun EndScreen(
    title: String,
    subtitle: String,
    unlocked: List<String>,
    onRestart: () -> Unit,
    onMenu: () -> Unit,
) {
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
                color = Steel,
                textAlign = TextAlign.Center,
            )
        }

        if (unlocked.isNotEmpty()) {
            Spacer(Modifier.height(20.dp))
            AnimatedVisibility(shown, enter = fadeIn(tween(1100))) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text("Открыто", style = MaterialTheme.typography.titleSmall, color = Moss)
                    Spacer(Modifier.height(6.dp))
                    unlocked.forEach {
                        Text(
                            it,
                            style = MaterialTheme.typography.bodyMedium,
                            color = Bone,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(28.dp))
        AnimatedVisibility(shown, enter = fadeIn(tween(1200)) + slideInVertically { it / 3 }) {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Button(onClick = onRestart, modifier = Modifier.fillMaxWidth()) { Text("Ещё раз") }
                Spacer(Modifier.height(12.dp))
                Button(onClick = onMenu, modifier = Modifier.fillMaxWidth()) { Text("В меню") }
            }
        }
    }
}

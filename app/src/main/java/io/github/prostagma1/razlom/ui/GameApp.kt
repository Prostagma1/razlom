package io.github.prostagma1.razlom.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import io.github.prostagma1.razlom.game.Game
import io.github.prostagma1.razlom.game.NodeKind
import io.github.prostagma1.razlom.game.Screen
import io.github.prostagma1.razlom.ui.screens.BattleScreen
import io.github.prostagma1.razlom.ui.screens.EndScreen
import io.github.prostagma1.razlom.ui.screens.MapScreen
import io.github.prostagma1.razlom.ui.screens.MenuScreen
import io.github.prostagma1.razlom.ui.screens.RewardScreen
import io.github.prostagma1.razlom.ui.screens.ShopScreen
import io.github.prostagma1.razlom.ui.theme.Ember
import io.github.prostagma1.razlom.ui.theme.InkRaised

@Composable
fun GameApp(game: Game) {
    var askExit by remember { mutableStateOf(false) }

    // Системная «назад» больше не закрывает игру посреди боя.
    BackHandler(enabled = game.screen != Screen.MENU) {
        when (game.screen) {
            Screen.GAME_OVER, Screen.RUN_WON -> game.toMenu()
            else -> askExit = true
        }
    }

    if (askExit) {
        AlertDialog(
            onDismissRequest = { askExit = false },
            containerColor = InkRaised,
            title = { Text("Выйти в меню?", color = Ember) },
            text = { Text("Забег сохранится — вернётесь к нему кнопкой «Продолжить забег».") },
            confirmButton = {
                TextButton(onClick = {
                    askExit = false
                    game.exitToMenu()
                }) { Text("Выйти") }
            },
            dismissButton = {
                TextButton(onClick = { askExit = false }) { Text("Остаться") }
            },
        )
    }

    Scaffold(modifier = Modifier.fillMaxSize()) { padding ->
        AnimatedContent(
            targetState = game.screen,
            modifier = Modifier.padding(padding),
            transitionSpec = {
                (fadeIn(tween(320)) + scaleIn(tween(320), initialScale = 0.96f)) togetherWith
                    fadeOut(tween(180))
            },
            label = "screen",
        ) { screen ->
            Box(Modifier.fillMaxSize()) {
                when (screen) {
                    Screen.MENU -> MenuScreen(
                        game = game,
                        onContinue = { game.continueRun() },
                        onStart = { game.newRun(it) },
                    )

                    Screen.MAP -> MapScreen(
                        game = game,
                        onEnter = game::enterNode,
                        onMenu = { askExit = true },
                    )

                    Screen.BATTLE -> game.battle?.let {
                        BattleScreen(it, onFinished = game::resolveBattle)
                    }

                    Screen.REWARD -> RewardScreen(
                        title = when (game.currentKind) {
                            NodeKind.RECRUIT -> "Наёмники у костра"
                            NodeKind.ELITE -> "Богатая добыча"
                            else -> "Трофеи"
                        },
                        rewards = game.rewards,
                        party = game.party,
                        onPick = game::takeReward,
                    )

                    Screen.SHOP -> ShopScreen(
                        game = game,
                        onBuy = game::buy,
                        onLeave = game::leaveShop,
                    )

                    Screen.GAME_OVER -> EndScreen(
                        title = "Отряд не вернулся",
                        subtitle = "Разлом сомкнулся за вами. Следующие пойдут дальше.",
                        unlocked = game.unlocked,
                        onRestart = { game.newRun(game.lastSquad) },
                        onMenu = game::toMenu,
                    )

                    Screen.RUN_WON -> EndScreen(
                        title = "Пожиратель повержен",
                        subtitle = "Отряд прошёл все восемь переходов. Попробуйте другой состав.",
                        unlocked = game.unlocked,
                        onRestart = { game.newRun(game.lastSquad) },
                        onMenu = game::toMenu,
                    )
                }
            }
        }
    }
}

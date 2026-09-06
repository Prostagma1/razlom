package io.github.prostagma1.razlom.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import io.github.prostagma1.razlom.game.Game
import io.github.prostagma1.razlom.game.NodeKind
import io.github.prostagma1.razlom.game.Screen
import io.github.prostagma1.razlom.ui.screens.BattleScreen
import io.github.prostagma1.razlom.ui.screens.EndScreen
import io.github.prostagma1.razlom.ui.screens.MapScreen
import io.github.prostagma1.razlom.ui.screens.MenuScreen
import io.github.prostagma1.razlom.ui.screens.RewardScreen

@Composable
fun GameApp() {
    val game = remember { Game() }

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
                    Screen.MENU -> MenuScreen(onStart = game::newRun)

                    Screen.MAP -> MapScreen(game, onEnter = game::enterNode)

                    Screen.BATTLE -> game.battle?.let {
                        BattleScreen(it, onFinished = game::resolveBattle)
                    }

                    Screen.REWARD -> RewardScreen(
                        title = if (game.currentKind == NodeKind.RECRUIT) "Наёмники у костра" else "Трофеи",
                        rewards = game.rewards,
                        party = game.party,
                        onPick = game::takeReward,
                    )

                    Screen.GAME_OVER -> EndScreen(
                        title = "Отряд не вернулся",
                        subtitle = "Разлом сомкнулся за вами. Следующие пойдут дальше.",
                        onRestart = game::newRun,
                        onMenu = game::toMenu,
                    )

                    Screen.RUN_WON -> EndScreen(
                        title = "Пожиратель повержен",
                        subtitle = "Отряд прошёл все восемь переходов. Попробуйте другой состав.",
                        onRestart = game::newRun,
                        onMenu = game::toMenu,
                    )
                }
            }
        }
    }
}

package io.github.prostagma1.razlom

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import io.github.prostagma1.razlom.game.Game
import io.github.prostagma1.razlom.ui.GameApp
import io.github.prostagma1.razlom.ui.theme.RazlomTheme

class MainActivity : ComponentActivity() {
    private lateinit var game: Game

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        game = Game(storage = FileStorage(applicationContext))
        enableEdgeToEdge()
        setContent {
            RazlomTheme {
                GameApp(game)
            }
        }
    }

    override fun onStop() {
        super.onStop()
        // Уходя в фон, дописываем текущее состояние: система может убить процесс.
        game.save()
    }
}

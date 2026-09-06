package io.github.prostagma1.razlom

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import io.github.prostagma1.razlom.ui.GameApp
import io.github.prostagma1.razlom.ui.theme.RazlomTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            RazlomTheme {
                GameApp()
            }
        }
    }
}

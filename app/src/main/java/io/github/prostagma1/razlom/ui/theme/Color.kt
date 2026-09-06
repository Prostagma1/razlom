package io.github.prostagma1.razlom.ui.theme

import androidx.compose.ui.graphics.Color

val Ember = Color(0xFFE07A3F)
val EmberDim = Color(0xFF8C4A24)
val Moss = Color(0xFF6FAE7A)
val Steel = Color(0xFF8FA3B8)
val Ink = Color(0xFF11131A)
val InkRaised = Color(0xFF1A1E28)
val InkLine = Color(0xFF2C3240)
val Bone = Color(0xFFE8E3D8)
val Blood = Color(0xFFC0453F)
val Arcane = Color(0xFF9B7BD4)
val Frost = Color(0xFF6FA8C7)

/** Цвета игрового поля. */
object Field {
    val cell = Color(0xFF1E232E)
    val cellAlt = Color(0xFF232936)
    val grid = Color(0xFF303748)
    val obstacle = Color(0xFF3A3126)
    val reachable = Color(0xFF2F4A57)
    val threat = Color(0xFF5A2A2A)
    val skill = Color(0xFF43356B)
    val ally = Moss
    val foe = Blood
    val active = Ember
}

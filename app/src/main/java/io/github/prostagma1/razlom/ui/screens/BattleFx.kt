package io.github.prostagma1.razlom.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import io.github.prostagma1.razlom.game.Pos

/**
 * Анимационное состояние одного бойца. Живёт рядом с игровой моделью:
 * логика двигает бойца мгновенно, а рисуем мы по этим сглаженным значениям.
 */
class UnitAnim(pos: Pos) {
    /** Позиция в клетках, догоняющая настоящую. */
    val cell = Animatable(Offset(pos.x.toFloat(), pos.y.toFloat()), Offset.VectorConverter)

    /** Короткий выпад в сторону цели при ударе. */
    val lunge = Animatable(Offset.Zero, Offset.VectorConverter)

    /** Вспышка при получении урона: 1 — белая, 0 — обычный цвет. */
    val flash = Animatable(0f)

    /** Пружинка размера при попадании. */
    val punch = Animatable(1f)

    /** Растворение павшего. */
    val fade = Animatable(1f)

    /** Всплывающее число урона или лечения. */
    val popup = Animatable(1f)
    var popupValue by mutableIntStateOf(0)

    /** Здоровье на прошлом кадре — чтобы поймать сам факт изменения. */
    var lastHp = -1
}

fun Pos.toOffset() = Offset(x.toFloat(), y.toFloat())

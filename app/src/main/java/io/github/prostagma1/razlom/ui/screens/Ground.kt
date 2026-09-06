package io.github.prostagma1.razlom.ui.screens

import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlin.math.floor
import kotlin.random.Random

/**
 * Земля под сеткой: процедурный шум, испечённый один раз за бой.
 * Клетки перестают быть однотонными, а рисовать нечего не надо.
 */
@Composable
fun rememberGround(seed: Int, width: Int = 112, height: Int = 144): ImageBitmap =
    remember(seed) { bakeGround(seed, width, height) }

private fun bakeGround(seed: Int, width: Int, height: Int): ImageBitmap {
    val rng = Random(seed)
    val offsetX = rng.nextFloat() * 100f
    val offsetY = rng.nextFloat() * 100f
    val pixels = IntArray(width * height)

    for (y in 0 until height) {
        for (x in 0 until width) {
            // Три октавы: крупные пятна, средняя грязь, мелкое зерно.
            val nx = x / width.toFloat()
            val ny = y / height.toFloat()
            var value = 0f
            var amplitude = 0.55f
            var frequency = 4f
            repeat(3) {
                value += noise(nx * frequency + offsetX, ny * frequency + offsetY, seed) * amplitude
                amplitude *= 0.5f
                frequency *= 2.4f
            }
            value = (value * 0.5f + 0.5f).coerceIn(0f, 1f)

            // Тёмное пятно там, где шум низкий; светлая пыль — где высокий.
            val alpha = ((value - 0.5f) * 2f).let { if (it < 0f) -it * 70f else it * 42f }
            val tint = if (value < 0.5f) 0x00000000 else 0x00C8B48C
            pixels[y * width + x] = (alpha.toInt().coerceIn(0, 90) shl 24) or tint
        }
    }
    return Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888).asImageBitmap()
}

/** Гладкий шум значений: решётка случайных чисел с косинусной интерполяцией. */
private fun noise(x: Float, y: Float, seed: Int): Float {
    val x0 = floor(x).toInt()
    val y0 = floor(y).toInt()
    val fx = smooth(x - x0)
    val fy = smooth(y - y0)

    val a = hash(x0, y0, seed)
    val b = hash(x0 + 1, y0, seed)
    val c = hash(x0, y0 + 1, seed)
    val d = hash(x0 + 1, y0 + 1, seed)

    val top = a + (b - a) * fx
    val bottom = c + (d - c) * fx
    return (top + (bottom - top) * fy) * 2f - 1f
}

private fun smooth(t: Float) = t * t * (3f - 2f * t)

private fun hash(x: Int, y: Int, seed: Int): Float {
    var h = x * 374761393 + y * 668265263 + seed * 1274126177
    h = (h xor (h shr 13)) * 1274126177
    return ((h xor (h shr 16)) and 0x7FFFFFFF) / 2147483647f
}

package io.github.prostagma1.razlom.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * Частица живёт в координатах поля (клетки, дробные), поэтому одинаково
 * выглядит на любом экране.
 */
class Particle(
    var x: Float,
    var y: Float,
    var vx: Float,
    var vy: Float,
    var life: Float,
    val maxLife: Float,
    val color: Color,
    val size: Float,
    val gravity: Float,
)

/**
 * Искры, брызги и пыль. Держим обычный список, а перерисовку дёргаем
 * счётчиком: список из сотни частиц не должен пересобирать композицию.
 */
class Sparks {
    private val items = ArrayList<Particle>()
    private val rng = Random(0)
    private var emberTimer = 0f

    /** Растёт каждый кадр — отрисовка подписана на него. */
    var tick by mutableIntStateOf(0)
        private set

    val count: Int get() = items.size

    fun burst(
        x: Float,
        y: Float,
        count: Int,
        color: Color,
        speed: Float,
        size: Float = 0.06f,
        life: Float = 0.5f,
        gravity: Float = 2.2f,
    ) {
        repeat(count) {
            val angle = rng.nextFloat() * 2f * Math.PI.toFloat()
            val power = speed * (0.35f + rng.nextFloat())
            items += Particle(
                x = x,
                y = y,
                vx = cos(angle) * power,
                vy = sin(angle) * power - speed * 0.35f,
                life = life * (0.6f + rng.nextFloat() * 0.7f),
                maxLife = life,
                color = color,
                size = size * (0.6f + rng.nextFloat() * 0.8f),
                gravity = gravity,
            )
        }
    }

    /** Пыль из-под ног: стелется по земле и почти не подпрыгивает. */
    fun dust(x: Float, y: Float, color: Color) {
        repeat(7) {
            val angle = rng.nextFloat() * 2f * Math.PI.toFloat()
            items += Particle(
                x = x,
                y = y + 0.2f,
                vx = cos(angle) * 0.5f,
                vy = sin(angle) * 0.18f - 0.1f,
                life = 0.45f + rng.nextFloat() * 0.3f,
                maxLife = 0.75f,
                color = color,
                size = 0.05f + rng.nextFloat() * 0.05f,
                gravity = -0.2f,
            )
        }
    }

    fun update(dt: Float, width: Int, height: Int, emberColor: Color) {
        // Редкие угольки, лениво всплывающие над полем.
        emberTimer -= dt
        if (emberTimer <= 0f) {
            emberTimer = 0.35f + rng.nextFloat() * 0.5f
            items += Particle(
                x = rng.nextFloat() * width,
                y = height * (0.5f + rng.nextFloat() * 0.5f),
                vx = (rng.nextFloat() - 0.5f) * 0.15f,
                vy = -0.25f - rng.nextFloat() * 0.2f,
                life = 2.2f + rng.nextFloat(),
                maxLife = 3.2f,
                color = emberColor,
                size = 0.03f + rng.nextFloat() * 0.025f,
                gravity = 0f,
            )
        }

        val iterator = items.iterator()
        while (iterator.hasNext()) {
            val p = iterator.next()
            p.life -= dt
            if (p.life <= 0f) {
                iterator.remove()
                continue
            }
            p.x += p.vx * dt
            p.y += p.vy * dt
            p.vy += p.gravity * dt
            p.vx *= 0.98f
        }
        tick++
    }

    inline fun forEach(block: (Particle) -> Unit) = snapshot().forEach(block)

    fun snapshot(): List<Particle> = items
}

/** Эффекты боя целиком: частицы и толчок экрана. */
class BattleFx {
    val sparks = Sparks()
    val shake = Animatable(0f)

    /** Короткий толчок экрана: [power] от 0 до 1. */
    suspend fun kick(power: Float) {
        shake.snapTo(maxOf(shake.value, power))
        shake.animateTo(0f, tween(340))
    }
}

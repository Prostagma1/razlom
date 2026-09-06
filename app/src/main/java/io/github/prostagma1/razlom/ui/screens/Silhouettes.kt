package io.github.prostagma1.razlom.ui.screens

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke

/**
 * Силуэты бойцов вместо кружков с буквами. Рисуются примитивами в координатах
 * «доля клетки от центра», поэтому одинаково читаются на любом размере поля.
 *
 * Два правила, которые видно только на тёмном поле: оружие рисуется светлым
 * металлом (тёмное на тёмном исчезает), а вся фигура сначала печатается
 * плоским тёмным контуром на размер больше — иначе она сливается с землёй.
 */
fun DrawScope.drawFighter(
    id: String,
    center: Offset,
    size: Float,
    body: Color,
    alpha: Float,
    /** Если задан — все детали рисуются одним цветом: так делается обводка. */
    flat: Color? = null,
) {
    val skin = flat ?: body.copy(alpha = alpha)
    val steel = flat ?: Color(0xFFB9C4D2).copy(alpha = 0.95f * alpha)
    val dark = flat ?: Color.Black.copy(alpha = 0.5f * alpha)
    val light = flat ?: Color.White.copy(alpha = 0.28f * alpha)

    fun at(x: Float, y: Float) = Offset(center.x + x * size, center.y + y * size)

    fun blob(x: Float, y: Float, rx: Float, ry: Float, color: Color = skin) {
        drawOval(color, at(x - rx, y - ry), Size(rx * 2 * size, ry * 2 * size))
    }

    fun slab(x0: Float, y0: Float, x1: Float, y1: Float, color: Color = skin, corner: Float = 0.06f) {
        drawRoundRect(
            color = color,
            topLeft = at(x0, y0),
            size = Size((x1 - x0) * size, (y1 - y0) * size),
            cornerRadius = CornerRadius(corner * size),
        )
    }

    fun dot(x: Float, y: Float, r: Float, color: Color = skin) =
        drawCircle(color, r * size, at(x, y))

    fun bar(x0: Float, y0: Float, x1: Float, y1: Float, w: Float, color: Color = skin) =
        drawLine(color, at(x0, y0), at(x1, y1), w * size, StrokeCap.Round)

    fun wedge(vararg points: Pair<Float, Float>, color: Color = skin) {
        val path = Path()
        points.forEachIndexed { i, (x, y) ->
            val o = at(x, y)
            if (i == 0) path.moveTo(o.x, o.y) else path.lineTo(o.x, o.y)
        }
        path.close()
        drawPath(path, color)
    }

    fun arc(x: Float, y: Float, r: Float, start: Float, sweep: Float, w: Float, color: Color = skin) {
        drawArc(
            color = color,
            startAngle = start,
            sweepAngle = sweep,
            useCenter = false,
            topLeft = at(x - r, y - r),
            size = Size(r * 2 * size, r * 2 * size),
            style = Stroke(w * size, cap = StrokeCap.Round),
        )
    }

    when (id) {
        // ---- отряд игрока ----------------------------------------------

        "latnik" -> {
            bar(0.20f, -0.16f, 0.30f, 0.22f, 0.06f, steel) // меч
            slab(-0.14f, -0.08f, 0.16f, 0.30f) // корпус
            dot(0.01f, -0.24f, 0.135f) // голова
            wedge(0.01f to -0.42f, -0.08f to -0.28f, 0.10f to -0.28f) // гребень
            slab(-0.34f, -0.14f, -0.12f, 0.24f, steel, corner = 0.08f) // щит
            dot(-0.23f, 0.05f, 0.05f, dark) // умбон щита
        }

        "kopye" -> {
            bar(-0.24f, 0.40f, 0.28f, -0.32f, 0.045f, steel) // древко
            wedge(0.34f to -0.44f, 0.23f to -0.28f, 0.35f to -0.24f, color = steel) // наконечник
            slab(-0.14f, -0.06f, 0.12f, 0.30f)
            dot(-0.01f, -0.22f, 0.125f)
            bar(-0.02f, 0.02f, 0.14f, -0.06f, 0.05f) // рука на древке
        }

        "luchnik" -> {
            arc(-0.10f, 0.02f, 0.30f, 100f, 200f, 0.045f, steel) // дуга лука
            bar(-0.20f, -0.24f, -0.20f, 0.28f, 0.018f, light) // тетива
            slab(-0.06f, -0.06f, 0.16f, 0.28f)
            dot(0.05f, -0.21f, 0.115f)
            bar(0.05f, 0.00f, -0.19f, 0.02f, 0.04f) // рука тянет тетиву
        }

        "mag" -> {
            bar(0.26f, -0.38f, 0.30f, 0.30f, 0.035f, steel) // посох
            dot(0.26f, -0.42f, 0.075f, light) // навершие
            wedge(0.0f to -0.40f, -0.20f to 0.30f, 0.18f to 0.30f) // балахон
            slab(-0.17f, -0.22f, 0.15f, -0.04f, corner = 0.05f) // плечи, чтобы не был конусом
            bar(0.06f, -0.10f, 0.26f, -0.06f, 0.045f) // рука на посохе
            dot(-0.01f, -0.24f, 0.085f, light) // лицо в тени капюшона
        }

        "znahar" -> {
            slab(0.13f, -0.15f, 0.36f, -0.07f, steel, corner = 0.02f) // крест в руке
            slab(0.21f, -0.27f, 0.29f, 0.05f, steel, corner = 0.02f)
            slab(-0.16f, -0.08f, 0.14f, 0.30f, corner = 0.07f) // корпус
            dot(-0.01f, -0.23f, 0.13f) // голова
            slab(-0.16f, 0.04f, 0.14f, 0.10f, light, corner = 0.02f) // перевязь
        }

        "razboy" -> {
            bar(-0.10f, 0.04f, -0.34f, -0.02f, 0.05f, steel) // клинки в стороны
            bar(0.10f, 0.04f, 0.34f, -0.02f, 0.05f, steel)
            slab(-0.11f, -0.10f, 0.11f, 0.26f, corner = 0.07f) // узкий корпус
            dot(0.0f, -0.22f, 0.12f) // голова
            wedge(-0.14f to -0.22f, 0.02f to -0.42f, 0.14f to -0.22f) // остроконечный капюшон
            dot(0.0f, -0.20f, 0.045f, light) // блик глаз
        }

        // ---- враги -----------------------------------------------------

        "ghoul" -> {
            bar(-0.14f, 0.00f, -0.32f, 0.32f, 0.06f) // длинные руки
            bar(0.16f, 0.00f, 0.32f, 0.32f, 0.06f)
            blob(0.0f, 0.12f, 0.19f, 0.22f) // сгорбленное тело
            dot(0.02f, -0.18f, 0.135f)
            dot(-0.03f, -0.20f, 0.032f, dark)
            dot(0.08f, -0.20f, 0.032f, dark)
        }

        "bonearcher" -> {
            arc(0.10f, 0.02f, 0.30f, 280f, 200f, 0.045f, steel) // лук
            bar(0.20f, -0.24f, 0.20f, 0.28f, 0.018f, light)
            slab(-0.11f, -0.04f, 0.09f, 0.28f)
            dot(-0.01f, -0.21f, 0.14f) // череп
            dot(-0.06f, -0.23f, 0.038f, dark)
            dot(0.05f, -0.23f, 0.038f, dark)
            bar(-0.05f, -0.11f, 0.03f, -0.11f, 0.028f, dark) // зубы
        }

        "marauder" -> {
            bar(0.26f, -0.28f, 0.26f, 0.30f, 0.045f, steel) // топорище
            wedge(0.26f to -0.36f, 0.46f to -0.20f, 0.26f to -0.04f, color = steel) // лезвие
            slab(-0.24f, -0.08f, 0.20f, 0.30f, corner = 0.08f) // широкий корпус
            dot(-0.02f, -0.23f, 0.135f)
            slab(-0.24f, 0.02f, 0.20f, 0.08f, dark, corner = 0.02f) // пояс
        }

        "spitter" -> {
            blob(0.0f, 0.10f, 0.25f, 0.24f)
            dot(0.0f, -0.20f, 0.12f)
            dot(0.22f, -0.18f, 0.06f, light) // брызги яда
            dot(0.32f, -0.30f, 0.035f, light)
            dot(-0.11f, 0.10f, 0.055f, dark) // пузыри на теле
            dot(0.09f, 0.17f, 0.045f, dark)
        }

        "howler" -> {
            arc(0.26f, -0.18f, 0.16f, -55f, 110f, 0.035f, steel) // волны крика
            arc(0.26f, -0.18f, 0.26f, -45f, 90f, 0.03f, steel)
            slab(-0.16f, -0.02f, 0.14f, 0.30f)
            dot(-0.01f, -0.20f, 0.155f)
            blob(-0.01f, -0.13f, 0.055f, 0.095f, dark) // разинутая пасть
        }

        "devourer" -> {
            wedge(-0.28f to -0.10f, -0.18f to -0.44f, -0.06f to -0.12f) // шипы
            wedge(-0.08f to -0.14f, 0.03f to -0.50f, 0.14f to -0.14f)
            wedge(0.12f to -0.10f, 0.26f to -0.42f, 0.32f to -0.08f)
            blob(0.0f, 0.08f, 0.34f, 0.30f) // туша
            dot(-0.12f, 0.02f, 0.06f, dark)
            dot(0.12f, 0.02f, 0.06f, dark)
            dot(0.0f, 0.16f, 0.09f, dark) // пасть
            dot(-0.12f, 0.02f, 0.025f, light)
            dot(0.12f, 0.02f, 0.025f, light)
        }

        else -> {
            slab(-0.16f, -0.06f, 0.16f, 0.30f)
            dot(0.0f, -0.20f, 0.13f)
        }
    }
}

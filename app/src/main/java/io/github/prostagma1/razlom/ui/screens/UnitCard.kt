package io.github.prostagma1.razlom.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.prostagma1.razlom.game.Ability
import io.github.prostagma1.razlom.game.Combatant
import io.github.prostagma1.razlom.game.Hero
import io.github.prostagma1.razlom.game.Status
import io.github.prostagma1.razlom.game.Team
import io.github.prostagma1.razlom.game.UnitType
import io.github.prostagma1.razlom.ui.theme.Arcane
import io.github.prostagma1.razlom.ui.theme.Blood
import io.github.prostagma1.razlom.ui.theme.Bone
import io.github.prostagma1.razlom.ui.theme.Ember
import io.github.prostagma1.razlom.ui.theme.Frost
import io.github.prostagma1.razlom.ui.theme.Ink
import io.github.prostagma1.razlom.ui.theme.Moss
import io.github.prostagma1.razlom.ui.theme.Steel

/** Что показывать в карточке — одинаково для бойца в бою и для героя на карте. */
data class UnitFacts(
    val type: UnitType,
    val hp: Int,
    val maxHp: Int,
    val attack: Int,
    val friendly: Boolean,
    val shield: Int = 0,
    val cooldown: Int = 0,
    val statuses: List<Pair<Status, Int>> = emptyList(),
)

fun Combatant.facts() = UnitFacts(
    type = type,
    hp = hp,
    maxHp = maxHp,
    attack = attack,
    friendly = team == Team.PLAYER,
    shield = shield,
    cooldown = cooldown,
    statuses = statuses.filterValues { it > 0 }.toList(),
)

fun Hero.facts() = UnitFacts(
    type = type,
    hp = hp,
    maxHp = maxHp,
    attack = attack,
    friendly = true,
)

/** Подробная карточка бойца: всё, что о нём стоит знать перед ходом. */
@Composable
fun UnitCard(facts: UnitFacts, onClose: (() -> Unit)? = null, modifier: Modifier = Modifier) {
    val tint = if (facts.friendly) Moss else Blood
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Portrait(tint, facts.type.id)
            Spacer(Modifier.size(10.dp))
            Column(Modifier.weight(1f)) {
                Text(facts.type.name, style = MaterialTheme.typography.titleSmall, color = Bone)
                Text(
                    "${facts.hp}/${facts.maxHp} HP" +
                        if (facts.shield > 0) " · щит ${facts.shield}" else "",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (facts.shield > 0) Frost else Steel,
                )
            }
            if (onClose != null) {
                TextButton(onClick = onClose) { Text("×", color = Steel) }
            }
        }

        LinearProgressIndicator(
            progress = { (facts.hp.toFloat() / facts.maxHp).coerceIn(0f, 1f) },
            color = tint,
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
        )

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Stat("Урон", "${facts.attack}")
            Stat("Дальность", "${facts.type.range}")
            Stat("Ход", "${facts.type.move}")
            Stat("Обзор", "${facts.type.vision}")
            Stat("Скорость", "${facts.type.speed}")
        }

        if (facts.type.ability != Ability.NONE) {
            Spacer(Modifier.height(8.dp))
            Text(
                "Особенность: " + abilityText(facts.type.ability),
                style = MaterialTheme.typography.labelMedium,
                color = Bone,
            )
        }

        facts.type.active?.let { skill ->
            Spacer(Modifier.height(4.dp))
            Text(
                buildString {
                    append(skill.name)
                    append(if (facts.cooldown > 0) " (через ${facts.cooldown} хода)" else " (готова)")
                    append(": ")
                    append(skill.description)
                },
                style = MaterialTheme.typography.labelMedium,
                color = if (facts.cooldown > 0) Steel else Arcane,
            )
        }

        if (facts.statuses.isNotEmpty()) {
            Spacer(Modifier.height(6.dp))
            Text(
                facts.statuses.joinToString("  ") { (status, turns) ->
                    "${status.glyph} ${status.label}: $turns"
                },
                style = MaterialTheme.typography.labelMedium,
                color = Ember,
            )
        }
    }
}

@Composable
private fun Portrait(color: androidx.compose.ui.graphics.Color, typeId: String) {
    Box(
        Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            drawFighter(
                id = typeId,
                center = Offset(size.width / 2f, size.height / 2f),
                size = size.minDimension * 0.95f,
                body = color,
                alpha = 1f,
            )
        }
    }
}

@Composable
private fun Stat(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleSmall, color = Bone)
        Text(label, style = MaterialTheme.typography.labelSmall, color = Steel, fontSize = 9.sp)
    }
}

private fun abilityText(ability: Ability) = when (ability) {
    Ability.HEAL -> "лечит союзника вместо удара"
    Ability.SPLASH -> "задевает всех рядом с целью"
    Ability.FLANK -> "+50% урона, если рядом с целью свой"
    Ability.PIERCE -> "пробивает второго на линии"
    Ability.NONE -> ""
}

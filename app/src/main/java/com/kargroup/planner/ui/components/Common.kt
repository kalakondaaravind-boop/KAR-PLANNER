package com.kargroup.planner.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowDropDown
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Computer
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.Flight
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.Phone
import androidx.compose.material.icons.outlined.ShoppingCart
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kargroup.planner.ui.theme.KarColors
import com.kargroup.planner.ui.theme.PaletteChoices
import com.kargroup.planner.ui.theme.parseHexColor

val ContextIcons: List<Pair<String, ImageVector>> = listOf(
    "bolt" to Icons.Outlined.Bolt,
    "phone" to Icons.Outlined.Phone,
    "groups" to Icons.Outlined.Groups,
    "desk" to Icons.Outlined.Computer,
    "cart" to Icons.Outlined.ShoppingCart,
    "home" to Icons.Outlined.Home,
    "mail" to Icons.Outlined.Email,
    "star" to Icons.Outlined.Star,
    "idea" to Icons.Outlined.Lightbulb,
    "travel" to Icons.Outlined.Flight,
)

fun contextIcon(name: String?): ImageVector =
    ContextIcons.firstOrNull { it.first == name }?.second ?: Icons.Outlined.Bolt

/** Small rounded tag used for task metadata (priority, due date, project...). */
@Composable
fun MetaPill(text: String, color: Color, icon: ImageVector? = null, dotColor: Color? = null) {
    Surface(shape = RoundedCornerShape(50), color = color.copy(alpha = 0.10f)) {
        Row(
            Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (dotColor != null) {
                Box(Modifier.size(7.dp).clip(CircleShape).background(dotColor))
                Spacer(Modifier.width(5.dp))
            }
            if (icon != null) {
                Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(12.dp))
                Spacer(Modifier.width(4.dp))
            }
            Text(text, color = color, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
fun ColorDot(color: Color, size: Int = 10) {
    Box(Modifier.size(size.dp).clip(CircleShape).background(color))
}

@Composable
fun FieldLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = KarColors.Slate,
        fontWeight = FontWeight.Bold,
        modifier = modifier.padding(top = 14.dp, bottom = 6.dp),
    )
}

data class SelectorOption(val id: String?, val label: String, val color: Color?)

/** A dropdown field that looks like an outlined text field. */
@Composable
fun SelectorField(
    label: String,
    options: List<SelectorOption>,
    selectedId: String?,
    onSelect: (String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    val selected = options.firstOrNull { it.id == selectedId } ?: options.firstOrNull()
    Box(modifier) {
        Surface(
            shape = RoundedCornerShape(10.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            color = Color.White,
            modifier = Modifier.fillMaxWidth().clickable { expanded = true },
        ) {
            Row(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(label, style = MaterialTheme.typography.labelSmall, color = KarColors.Slate)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        selected?.color?.let { ColorDot(it); Spacer(Modifier.width(8.dp)) }
                        Text(selected?.label ?: "—", style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                Icon(Icons.Outlined.ArrowDropDown, contentDescription = null, tint = KarColors.Slate)
            }
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { opt ->
                DropdownMenuItem(
                    text = { Text(opt.label) },
                    leadingIcon = { if (opt.color != null) ColorDot(opt.color, 12) else Spacer(Modifier.size(12.dp)) },
                    trailingIcon = {
                        if (opt.id == selectedId) Icon(Icons.Outlined.Check, contentDescription = null, tint = KarColors.Navy)
                    },
                    onClick = {
                        onSelect(opt.id)
                        expanded = false
                    },
                )
            }
        }
    }
}

/** Row of colour swatches. */
@Composable
fun ColorPicker(selected: String, onSelect: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        PaletteChoices.chunked(6).forEach { rowColors ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                rowColors.forEach { hex ->
                    val isSel = hex.equals(selected, ignoreCase = true)
                    Box(
                        Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(parseHexColor(hex))
                            .border(if (isSel) 3.dp else 0.dp, if (isSel) KarColors.Gold else Color.Transparent, CircleShape)
                            .clickable { onSelect(hex) },
                        contentAlignment = Alignment.Center,
                    ) {
                        if (isSel) Icon(Icons.Outlined.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun VSpace(h: Int) = Spacer(Modifier.height(h.dp))

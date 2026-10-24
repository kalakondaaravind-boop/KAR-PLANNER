package com.kargroup.planner.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import com.kargroup.planner.ui.components.OutlinedTextField
import androidx.compose.material3.Surface
import com.kargroup.planner.ui.components.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.kargroup.planner.data.TaskEntity
import com.kargroup.planner.ui.components.GoldButton
import com.kargroup.planner.ui.components.KarBrush
import com.kargroup.planner.ui.theme.KarColors
import kotlinx.coroutines.delay
import kotlin.random.Random

/** An activity waiting for its completion remarks. */
data class CompletionRequest(val task: TaskEntity, val openSubtasks: Int)

/** What the celebration screen shows after an activity is completed. */
data class Celebration(val emoji: String, val headline: String, val line: String, val taskTitle: String, val remarks: String)

private val Cheers = listOf(
    Triple("🏆", "Well done!", "One more milestone ticked off. All the best for the next one!"),
    Triple("🎉", "Great job!", "That's progress you can be proud of. Keep the momentum going!"),
    Triple("🚀", "Nailed it!", "Fantastic work. All the best for what's next!"),
    Triple("⭐", "Excellent work!", "Quality delivered. On to the next win!"),
    Triple("💪", "Superb effort!", "Consistency builds results. Keep it up!"),
    Triple("🌟", "Brilliant!", "Another one done and dusted. All the best!"),
    Triple("✅", "Mission accomplished!", "Smooth delivery. Your next activity awaits!"),
)

fun newCelebration(task: TaskEntity, remarks: String): Celebration {
    val (e, h, l) = Cheers[Random.nextInt(Cheers.size)]
    return Celebration(e, h, l, task.text, remarks.trim())
}

/** Compulsory remarks before an activity can be marked completed. */
@Composable
fun CompletionRemarksDialog(
    request: CompletionRequest,
    onDismiss: () -> Unit,
    onConfirm: (remarks: String, includeSubtasks: Boolean) -> Unit,
) {
    var remarks by remember { mutableStateOf("") }
    var includeSubtasks by remember { mutableStateOf(true) }
    var tried by remember { mutableStateOf(false) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        delay(250)
        runCatching { focus.requestFocus() }
    }
    val missing = remarks.isBlank()

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = Color.White,
            shadowElevation = 24.dp,
            modifier = Modifier.padding(16.dp).widthIn(max = 560.dp).fillMaxWidth(),
        ) {
            Column {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .background(Brush.linearGradient(listOf(Color(0xFF059669), Color(0xFF10B981), Color(0xFF34D399))))
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Outlined.TaskAlt, null, tint = Color.White, modifier = Modifier.size(30.dp))
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text("Complete Activity", style = MaterialTheme.typography.titleLarge, color = Color.White)
                        Text(
                            request.task.text,
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.9f),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                Column(Modifier.padding(20.dp)) {
                    Text(
                        "Add completion remarks to close this activity: the outcome, what was delivered, or any follow-up.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = KarColors.Slate,
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = remarks,
                        onValueChange = { remarks = it.take(1000) },
                        label = { Text("Completion remarks *") },
                        placeholder = { Text("e.g. Report submitted to audit committee; 2 open points closed") },
                        isError = tried && missing,
                        supportingText = { if (tried && missing) Text("Remarks are required to complete the activity") },
                        minLines = 3,
                        maxLines = 6,
                        modifier = Modifier.fillMaxWidth().focusRequester(focus),
                    )
                    if (request.openSubtasks > 0) {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .clickable { includeSubtasks = !includeSubtasks }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(
                                checked = includeSubtasks,
                                onCheckedChange = { includeSubtasks = it },
                                colors = CheckboxDefaults.colors(checkedColor = KarColors.Green),
                            )
                            Text(
                                "Also mark ${request.openSubtasks} open subtask${if (request.openSubtasks == 1) "" else "s"} as completed",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = onDismiss) { Text("Cancel", color = KarColors.Slate) }
                        Spacer(Modifier.width(8.dp))
                        GoldButton(
                            text = "Mark Completed",
                            onClick = {
                                tried = true
                                if (!missing) onConfirm(remarks.trim(), includeSubtasks)
                            },
                            height = 44.dp,
                        )
                    }
                }
            }
        }
    }
}

private data class Confetto(val x: Float, val delay: Float, val speed: Float, val size: Float, val color: Color, val spin: Float)

private val ConfettiColors = listOf(
    Color(0xFF0EA5E9), Color(0xFF38BDF8), Color(0xFFF5A623), Color(0xFF10B981),
    Color(0xFFEC4899), Color(0xFF8B5CF6), Color(0xFFF59E0B),
)

/** Animated "smart art" shown after completion: confetti, a bouncing badge and a motivating message. */
@Composable
fun CelebrationOverlay(celebration: Celebration, onDone: () -> Unit) {
    val pieces = remember(celebration) {
        List(70) {
            Confetto(
                x = Random.nextFloat(),
                delay = Random.nextFloat() * 0.6f,
                speed = 0.55f + Random.nextFloat() * 0.6f,
                size = 6f + Random.nextFloat() * 8f,
                color = ConfettiColors[Random.nextInt(ConfettiColors.size)],
                spin = Random.nextFloat() * 360f,
            )
        }
    }
    val fall = remember(celebration) { Animatable(0f) }
    val pop = remember(celebration) { Animatable(0.2f) }
    LaunchedEffect(celebration) {
        pop.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow))
    }
    LaunchedEffect(celebration) {
        fall.animateTo(1.6f, tween(durationMillis = 3200, easing = LinearEasing))
    }
    LaunchedEffect(celebration) {
        delay(4500)
        onDone()
    }
    val pulse = rememberInfiniteTransition(label = "pulse")
    val glow by pulse.animateFloat(
        initialValue = 0.92f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "glow",
    )
    val wobble by pulse.animateFloat(
        initialValue = -6f,
        targetValue = 6f,
        animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse),
        label = "wobble",
    )

    Dialog(onDismissRequest = onDone, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(
            Modifier
                .fillMaxSize()
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDone),
            contentAlignment = Alignment.Center,
        ) {
            // Confetti rain
            Canvas(Modifier.fillMaxSize()) {
                val t = fall.value
                pieces.forEach { p ->
                    val progress = ((t - p.delay) * p.speed).coerceIn(0f, 1.2f)
                    if (progress > 0f) {
                        val y = -40f + progress * (size.height + 80f)
                        val x = p.x * size.width + kotlin.math.sin((progress * 12f + p.spin).toDouble()).toFloat() * 24f
                        rotate(p.spin + progress * 540f, pivot = Offset(x, y)) {
                            drawRect(p.color, topLeft = Offset(x, y), size = Size(p.size, p.size * 0.55f))
                        }
                    }
                }
            }
            Surface(
                shape = RoundedCornerShape(28.dp),
                color = Color.White,
                shadowElevation = 30.dp,
                modifier = Modifier.padding(24.dp).widthIn(max = 460.dp).fillMaxWidth().scale(pop.value),
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(150.dp)
                            .background(KarBrush.SkyHeader),
                        contentAlignment = Alignment.Center,
                    ) {
                        // Sunburst rings
                        Box(
                            Modifier.size(120.dp).scale(glow).clip(CircleShape).background(Color.White.copy(alpha = 0.18f)),
                        )
                        Box(
                            Modifier.size(92.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.28f)),
                        )
                        Box(
                            Modifier.size(74.dp).clip(CircleShape).background(KarColors.Card),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(celebration.emoji, fontSize = 40.sp, modifier = Modifier.rotate(wobble))
                        }
                    }
                    Column(Modifier.padding(horizontal = 24.dp, vertical = 20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            celebration.headline,
                            fontSize = 30.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = KarColors.SkyDeep,
                            textAlign = TextAlign.Center,
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            celebration.line,
                            style = MaterialTheme.typography.bodyLarge,
                            color = KarColors.Ink,
                            textAlign = TextAlign.Center,
                        )
                        Spacer(Modifier.height(14.dp))
                        Surface(color = KarColors.SkyWash, shape = RoundedCornerShape(12.dp)) {
                            Column(Modifier.fillMaxWidth().padding(12.dp)) {
                                Text(
                                    "✔ " + celebration.taskTitle,
                                    style = MaterialTheme.typography.titleSmall,
                                    color = KarColors.Navy,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    "“${celebration.remarks}”",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = KarColors.Slate,
                                    maxLines = 3,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                        Spacer(Modifier.height(16.dp))
                        GoldButton("Continue", onDone, Modifier.fillMaxWidth(), height = 46.dp)
                    }
                }
            }
        }
    }
}

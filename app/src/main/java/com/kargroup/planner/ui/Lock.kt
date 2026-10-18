package com.kargroup.planner.ui

import android.content.Context
import android.content.ContextWrapper
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Backspace
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.kargroup.planner.data.PlannerSettings
import com.kargroup.planner.data.SettingsStore
import com.kargroup.planner.ui.components.KarBrush
import com.kargroup.planner.ui.components.KarLogo
import com.kargroup.planner.ui.components.OutlinedTextField
import com.kargroup.planner.ui.components.Text
import com.kargroup.planner.ui.theme.KarColors

private tailrec fun Context.findFragmentActivity(): FragmentActivity? = when (this) {
    is FragmentActivity -> this
    is ContextWrapper -> baseContext.findFragmentActivity()
    else -> null
}

fun biometricAvailable(context: Context): Boolean =
    BiometricManager.from(context).canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK) == BiometricManager.BIOMETRIC_SUCCESS

private fun askBiometric(context: Context, title: String, onSuccess: () -> Unit) {
    val activity = context.findFragmentActivity() ?: return
    val prompt = BiometricPrompt(
        activity,
        ContextCompat.getMainExecutor(activity),
        object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                onSuccess()
            }
        },
    )
    val info = BiometricPrompt.PromptInfo.Builder()
        .setTitle(title)
        .setSubtitle("KAR Planner")
        .setNegativeButtonText("Use PIN")
        .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_WEAK)
        .build()
    runCatching { prompt.authenticate(info) }
}

/** Full-screen lock with PIN pad and (optional) fingerprint. */
@Composable
fun LockScreen(settings: PlannerSettings, title: String, onUnlocked: () -> Unit) {
    val context = LocalContext.current
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf(false) }
    val bioAvailable = remember { biometricAvailable(context) }
    val bio = settings.useBiometric && bioAvailable
    LaunchedEffect(Unit) { if (bio) askBiometric(context, "Unlock $title", onUnlocked) }

    fun press(d: String) {
        if (pin.length >= 6) return
        pin += d
        error = false
        val hash = settings.pinHash
        if (hash != null && pin.length >= 4 && SettingsStore.hashPin(pin) == hash) {
            onUnlocked()
        } else if (pin.length == 6) {
            error = true
            pin = ""
        }
    }

    Box(Modifier.fillMaxSize().background(KarBrush.Backdrop), contentAlignment = Alignment.Center) {
        Column(
            Modifier.widthIn(max = 380.dp).fillMaxWidth().verticalScroll(rememberScrollState()).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            KarLogo(height = 44.dp)
            Spacer(Modifier.height(24.dp))
            Box(
                Modifier.size(64.dp).clip(CircleShape).background(KarBrush.SkyHeader),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Outlined.Lock, null, tint = Color.White, modifier = Modifier.size(30.dp)) }
            Spacer(Modifier.height(12.dp))
            Text("$title is locked", style = MaterialTheme.typography.titleLarge, color = KarColors.Navy)
            Text(
                if (error) "Wrong PIN, try again" else "Enter your PIN",
                style = MaterialTheme.typography.bodyMedium,
                color = if (error) KarColors.Red else KarColors.Slate,
            )
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                repeat(6) { i ->
                    Box(
                        Modifier
                            .size(14.dp)
                            .clip(CircleShape)
                            .background(if (i < pin.length) KarColors.SkyDeep else Color.Transparent)
                            .border(1.5.dp, KarColors.SkyDeep, CircleShape)
                    )
                }
            }
            Spacer(Modifier.height(20.dp))
            listOf(listOf("1", "2", "3"), listOf("4", "5", "6"), listOf("7", "8", "9"), listOf("bio", "0", "del")).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(18.dp), modifier = Modifier.padding(vertical = 7.dp)) {
                    row.forEach { k ->
                        Box(
                            Modifier
                                .size(68.dp)
                                .clip(CircleShape)
                                .background(if (k == "bio" || k == "del") Color.Transparent else Color.White)
                                .then(
                                    if (k == "bio" && !bio) Modifier
                                    else Modifier.clickable {
                                        when (k) {
                                            "del" -> pin = pin.dropLast(1)
                                            "bio" -> askBiometric(context, "Unlock $title", onUnlocked)
                                            else -> press(k)
                                        }
                                    }
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            when (k) {
                                "del" -> Icon(Icons.Outlined.Backspace, "Delete", tint = KarColors.Slate)
                                "bio" -> if (bio) Icon(Icons.Outlined.Fingerprint, "Fingerprint", tint = KarColors.SkyDeep, modifier = Modifier.size(32.dp))
                                else -> Text(k, fontSize = 26.sp, fontWeight = FontWeight.SemiBold, color = KarColors.Navy)
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Create (or change) the 4-6 digit PIN. */
@Composable
fun SetPinDialog(onDismiss: () -> Unit, onSet: (String) -> Unit) {
    var p1 by remember { mutableStateOf("") }
    var p2 by remember { mutableStateOf("") }
    val valid = p1.length in 4..6 && p1.all { it.isDigit() }
    val match = p1 == p2
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color.White,
        icon = { Icon(Icons.Outlined.Lock, null, tint = KarColors.SkyDeep) },
        title = { Text("Set PIN") },
        text = {
            Column {
                OutlinedTextField(
                    value = p1,
                    onValueChange = { v -> p1 = v.filter { it.isDigit() }.take(6) },
                    label = { Text("New PIN (4-6 digits)") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = p2,
                    onValueChange = { v -> p2 = v.filter { it.isDigit() }.take(6) },
                    label = { Text("Repeat PIN") },
                    singleLine = true,
                    isError = p2.isNotEmpty() && !match,
                    supportingText = { if (p2.isNotEmpty() && !match) Text("PINs don't match") },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    "Keep your PIN safe. If you forget it, the app's data can only be recovered from a backup.",
                    style = MaterialTheme.typography.bodySmall,
                    color = KarColors.Slate,
                )
            }
        },
        confirmButton = { TextButton(onClick = { onSet(p1) }, enabled = valid && match) { Text("Save PIN") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Suppress("unused")
private val lockShape = RoundedCornerShape(16.dp)

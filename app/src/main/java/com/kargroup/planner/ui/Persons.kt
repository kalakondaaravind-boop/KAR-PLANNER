package com.kargroup.planner.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowDropDown
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material.icons.outlined.PersonOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import com.kargroup.planner.ui.components.OutlinedTextField
import androidx.compose.material3.Surface
import com.kargroup.planner.ui.components.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kargroup.planner.data.PersonEntity
import com.kargroup.planner.ui.theme.KarColors

/**
 * Drop-down for choosing who an activity is assigned to. Only people from the people list
 * can be picked; [onAddNew] (optional) offers "+ Add new person" at the bottom of the list.
 */
@Composable
fun PersonPicker(
    value: String,
    onValueChange: (String) -> Unit,
    people: List<PersonEntity>,
    modifier: Modifier = Modifier,
    label: String = "Assigned to",
    onAddNew: (() -> Unit)? = null,
) {
    var open by remember { mutableStateOf(false) }
    val current = value.trim()
    val match = people.firstOrNull { it.name.equals(current, ignoreCase = true) }
    Box(modifier) {
        Surface(
            onClick = { open = true },
            shape = RoundedCornerShape(10.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            color = Color.White,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (current.isEmpty()) Icons.Outlined.PersonOff else Icons.Outlined.Person,
                    null,
                    tint = if (current.isEmpty()) KarColors.SlateLight else KarColors.SkyDeep,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(label, style = MaterialTheme.typography.labelSmall, color = KarColors.Slate)
                    Text(
                        if (current.isEmpty()) "Unassigned" else (match?.name ?: current),
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (current.isEmpty()) KarColors.Slate else KarColors.Ink,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    match?.role?.let { r ->
                        Text(r, style = MaterialTheme.typography.labelSmall, color = KarColors.SlateLight, maxLines = 1)
                    }
                }
                Icon(Icons.Outlined.ArrowDropDown, "Choose person", tint = KarColors.Slate)
            }
        }
        DropdownMenu(
            expanded = open,
            onDismissRequest = { open = false },
            modifier = Modifier.heightIn(max = 360.dp),
        ) {
            DropdownMenuItem(
                text = { Text("Unassigned", color = KarColors.Slate) },
                leadingIcon = { Icon(Icons.Outlined.PersonOff, null, tint = KarColors.SlateLight) },
                trailingIcon = { if (current.isEmpty()) Icon(Icons.Outlined.Check, null, tint = KarColors.SkyDeep) },
                onClick = { onValueChange(""); open = false },
            )
            people.forEach { p ->
                val sel = p.name.equals(current, ignoreCase = true)
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(p.name, fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal)
                            p.role?.let { r -> Text(r, style = MaterialTheme.typography.labelSmall, color = KarColors.Slate) }
                        }
                    },
                    leadingIcon = { Icon(Icons.Outlined.Person, null, tint = KarColors.SkyDeep) },
                    trailingIcon = { if (sel) Icon(Icons.Outlined.Check, null, tint = KarColors.SkyDeep) },
                    onClick = { onValueChange(p.name); open = false },
                )
            }
            if (people.isEmpty()) {
                DropdownMenuItem(
                    text = { Text("No people added yet", color = KarColors.SlateLight) },
                    enabled = false,
                    onClick = {},
                )
            }
            if (onAddNew != null) {
                HorizontalDivider(color = KarColors.Border)
                DropdownMenuItem(
                    text = { Text("Add new person…", color = KarColors.SkyDeep, fontWeight = FontWeight.Bold) },
                    leadingIcon = { Icon(Icons.Outlined.PersonAdd, null, tint = KarColors.SkyDeep) },
                    onClick = { open = false; onAddNew() },
                )
            }
        }
    }
}

/** Add / edit a person. Names must be unique (ignoring capital letters). */
@Composable
fun PersonDialog(
    existing: PersonEntity?,
    takenNames: Collection<String>,
    onDismiss: () -> Unit,
    onSave: (PersonEntity) -> Unit,
) {
    var name by remember { mutableStateOf(existing?.name ?: "") }
    var role by remember { mutableStateOf(existing?.role ?: "") }
    var email by remember { mutableStateOf(existing?.email ?: "") }
    var phone by remember { mutableStateOf(existing?.phone ?: "") }
    val clean = name.trim().replace(Regex("\\s+"), " ")
    val duplicate = clean.isNotEmpty() && takenNames.any {
        it.equals(clean, ignoreCase = true) && !it.equals(existing?.name ?: "", ignoreCase = true)
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color.White,
        icon = { Icon(Icons.Outlined.PersonAdd, null, tint = KarColors.SkyDeep) },
        title = { Text(if (existing == null) "Add Person" else "Edit Person", color = KarColors.Navy) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(60) },
                    label = { Text("Full name *") },
                    isError = duplicate,
                    supportingText = { if (duplicate) Text("This person is already in the list") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = role,
                    onValueChange = { role = it.take(60) },
                    label = { Text("Role / designation") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it.take(80) },
                    label = { Text("Email") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it.take(20) },
                    label = { Text("Phone") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (existing != null && clean.isNotEmpty() && clean != existing.name) {
                    Text(
                        "Renaming updates every activity assigned to ${existing.name}.",
                        style = MaterialTheme.typography.bodySmall,
                        color = KarColors.Amber,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val base = existing ?: PersonEntity(name = clean)
                    onSave(
                        base.copy(
                            name = clean,
                            role = role.trim().ifBlank { null },
                            email = email.trim().ifBlank { null },
                            phone = phone.trim().ifBlank { null },
                        )
                    )
                },
                enabled = clean.isNotEmpty() && !duplicate,
                colors = ButtonDefaults.buttonColors(containerColor = KarColors.SkyDeep, contentColor = Color.White),
            ) { Text(if (existing == null) "Add Person" else "Save", fontWeight = FontWeight.Bold) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

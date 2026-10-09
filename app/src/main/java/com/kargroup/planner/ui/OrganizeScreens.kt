package com.kargroup.planner.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kargroup.planner.data.ContextTagEntity
import com.kargroup.planner.data.ProjectEntity
import com.kargroup.planner.data.TaskEntity
import com.kargroup.planner.ui.components.ColorPicker
import com.kargroup.planner.ui.components.ContextIcons
import com.kargroup.planner.ui.components.FieldLabel
import com.kargroup.planner.ui.components.contextIcon
import com.kargroup.planner.ui.theme.KarColors
import com.kargroup.planner.ui.components.DepthCard
import com.kargroup.planner.ui.components.KarBrush
import com.kargroup.planner.ui.components.GoldButton
import com.kargroup.planner.ui.components.IconTile
import com.kargroup.planner.ui.theme.parseHexColor

@Composable
fun OrganizeHeader(title: String, subtitle: String, buttonLabel: String, onAdd: () -> Unit) {
    DepthCard(
        modifier = Modifier.fillMaxWidth(),
        background = KarBrush.SkyHeader,
        border = null,
        elevation = 10.dp,
    ) {
        Row(
            Modifier.padding(horizontal = 18.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleLarge, color = Color.White)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.72f))
            }
            Spacer(Modifier.width(10.dp))
            GoldButton(buttonLabel, onAdd, icon = Icons.Outlined.Add, height = 42.dp)
        }
    }
}

@Composable
fun ProjectsScreen(
    projects: List<ProjectEntity>,
    tasks: List<TaskEntity>,
    wide: Boolean,
    header: @Composable () -> Unit,
    onOpen: (ProjectEntity) -> Unit,
    onAdd: () -> Unit,
    onEdit: (ProjectEntity) -> Unit,
    onDelete: (ProjectEntity) -> Unit,
) {
    val hPad = if (wide) 24.dp else 12.dp
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 300.dp),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = hPad, end = hPad, top = 12.dp, bottom = 110.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) { header() }
        item(span = { GridItemSpan(maxLineSpan) }) {
            OrganizeHeader("Projects", "${projects.size} project(s) · group related activities", "New Project", onAdd)
        }
        if (projects.isEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                EmptyCard(Icons.Outlined.Folder, "No projects yet", "Create a project to group your activities and meetings.")
            }
        }
        items(projects, key = { it.id }) { p ->
            val mine = tasks.filter { it.projectId == p.id }
            val done = mine.count { it.done }
            val color = parseHexColor(p.color)
            DepthCard(modifier = Modifier.fillMaxWidth(), onClick = { onOpen(p) }) {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(14.dp).background(color, CircleShape))
                        Spacer(Modifier.width(10.dp))
                        Text(
                            p.name,
                            style = MaterialTheme.typography.titleMedium,
                            color = KarColors.Navy,
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        IconButton(onClick = { onEdit(p) }) { Icon(Icons.Outlined.Edit, "Edit", tint = KarColors.Slate) }
                        IconButton(onClick = { onDelete(p) }) { Icon(Icons.Outlined.Delete, "Delete", tint = KarColors.Red) }
                    }
                    if (!p.description.isNullOrBlank()) {
                        Text(p.description, style = MaterialTheme.typography.bodySmall, color = KarColors.Slate, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                    Spacer(Modifier.height(10.dp))
                    val fraction = if (mine.isEmpty()) 0f else done.toFloat() / mine.size
                    LinearProgressIndicator(
                        progress = { fraction },
                        modifier = Modifier.fillMaxWidth().height(6.dp),
                        color = color,
                        trackColor = KarColors.Border,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "${mine.size - done} open · $done done",
                        style = MaterialTheme.typography.labelSmall,
                        color = KarColors.Slate,
                    )
                }
            }
        }
    }
}

@Composable
fun ContextsScreen(
    contexts: List<ContextTagEntity>,
    tasks: List<TaskEntity>,
    wide: Boolean,
    header: @Composable () -> Unit,
    onOpen: (ContextTagEntity) -> Unit,
    onAdd: () -> Unit,
    onEdit: (ContextTagEntity) -> Unit,
    onDelete: (ContextTagEntity) -> Unit,
) {
    val hPad = if (wide) 24.dp else 12.dp
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 260.dp),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = hPad, end = hPad, top = 12.dp, bottom = 110.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) { header() }
        item(span = { GridItemSpan(maxLineSpan) }) {
            OrganizeHeader("GTD Contexts", "Where or how an activity gets done", "New Context", onAdd)
        }
        if (contexts.isEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                EmptyCard(contextIcon("bolt"), "No contexts yet", "Contexts like @calls or @desk let you batch similar work.")
            }
        }
        items(contexts, key = { it.id }) { c ->
            val open = tasks.count { it.contextId == c.id && !it.done }
            val color = parseHexColor(c.color)
            DepthCard(modifier = Modifier.fillMaxWidth(), onClick = { onOpen(c) }) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconTile(contextIcon(c.icon), color, 42.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(c.name, style = MaterialTheme.typography.titleMedium, color = KarColors.Navy, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("$open open", style = MaterialTheme.typography.labelSmall, color = KarColors.Slate)
                    }
                    IconButton(onClick = { onEdit(c) }) { Icon(Icons.Outlined.Edit, "Edit", tint = KarColors.Slate) }
                    IconButton(onClick = { onDelete(c) }) { Icon(Icons.Outlined.Delete, "Delete", tint = KarColors.Red) }
                }
            }
        }
    }
}

@Composable
fun EmptyCard(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, body: String) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(Color.White, RoundedCornerShape(16.dp))
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, null, tint = KarColors.SlateLight, modifier = Modifier.size(44.dp))
        Spacer(Modifier.height(8.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, color = KarColors.Navy)
        Text(body, style = MaterialTheme.typography.bodyMedium, color = KarColors.Slate)
    }
}

@Composable
fun ProjectDialog(existing: ProjectEntity?, onDismiss: () -> Unit, onSave: (ProjectEntity) -> Unit) {
    var name by remember { mutableStateOf(existing?.name ?: "") }
    var description by remember { mutableStateOf(existing?.description ?: "") }
    var color by remember { mutableStateOf(existing?.color ?: "#1E3A6E") }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color.White,
        title = { Text(if (existing == null) "New Project" else "Edit Project", color = KarColors.Navy) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(name, { name = it }, label = { Text("Project name *") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(description, { description = it }, label = { Text("Description") }, minLines = 2, modifier = Modifier.fillMaxWidth())
                FieldLabel("Colour")
                ColorPicker(color) { color = it }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val base = existing ?: ProjectEntity(name = "")
                    onSave(base.copy(name = name.trim(), description = description.trim().ifBlank { null }, color = color))
                },
                enabled = name.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = KarColors.SkyDeep, contentColor = androidx.compose.ui.graphics.Color.White),
            ) { Text(if (existing == null) "Create Project" else "Save", fontWeight = FontWeight.Bold) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ContextDialog(existing: ContextTagEntity?, onDismiss: () -> Unit, onSave: (ContextTagEntity) -> Unit) {
    var name by remember { mutableStateOf(existing?.name ?: "@") }
    var color by remember { mutableStateOf(existing?.color ?: "#1E3A6E") }
    var icon by remember { mutableStateOf(existing?.icon ?: "bolt") }
    val cleanName = name.trim().let { if (it.startsWith("@")) it else "@$it" }.replace(" ", "-")
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color.White,
        title = { Text(if (existing == null) "New Context" else "Edit Context", color = KarColors.Navy) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(name, { name = it }, label = { Text("Context name (e.g. @calls)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                FieldLabel("Icon")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ContextIcons.forEach { (key, vector) ->
                        val sel = key == icon
                        Box(
                            Modifier
                                .size(40.dp)
                                .background(if (sel) KarColors.SkyDeep else KarColors.Tint, RoundedCornerShape(10.dp))
                                .border(1.dp, if (sel) KarColors.SkyDeep else KarColors.Border, RoundedCornerShape(10.dp))
                                .clickable { icon = key },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(vector, key, tint = if (sel) KarColors.Gold else KarColors.Navy)
                        }
                    }
                }
                FieldLabel("Colour")
                ColorPicker(color) { color = it }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val base = existing ?: ContextTagEntity(name = "")
                    onSave(base.copy(name = cleanName, color = color, icon = icon))
                },
                enabled = cleanName.length > 1,
                colors = ButtonDefaults.buttonColors(containerColor = KarColors.SkyDeep, contentColor = androidx.compose.ui.graphics.Color.White),
            ) { Text(if (existing == null) "Create Context" else "Save", fontWeight = FontWeight.Bold) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

package com.kargroup.planner.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.HorizontalScrollbar
import androidx.compose.foundation.LocalScrollbarStyle
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.defaultScrollbarStyle
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.UploadFile
import androidx.compose.material.icons.outlined.ViewColumn
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kargroup.planner.desktop.ColFormula
import com.kargroup.planner.desktop.ColType
import com.kargroup.planner.desktop.CommercialColumn
import com.kargroup.planner.desktop.CommercialRow
import com.kargroup.planner.desktop.CommercialStore
import com.kargroup.planner.desktop.CommercialImport
import com.kargroup.planner.desktop.Commercials
import com.kargroup.planner.desktop.UploadTable
import com.kargroup.planner.desktop.FileDialogs
import com.kargroup.planner.ui.components.DepthCard
import com.kargroup.planner.ui.components.OutlinedTextField
import com.kargroup.planner.ui.components.Text
import com.kargroup.planner.ui.theme.KarColors
import com.kargroup.planner.util.Ist

/** Colour for a status / drop-down value (conditional formatting). */
fun commercialStatusColor(v: String?): Color {
    val s = v?.lowercase()?.trim() ?: return KarColors.Slate
    return when {
        s.startsWith("won") || s == "live" || s.contains("signed") || s == "active" -> Color(0xFF059669)
        s.contains("lost") || s.contains("drop") || s.contains("cancel") || s.contains("reject") -> Color(0xFFDC2626)
        s.contains("hold") || s.contains("pause") -> Color(0xFFD97706)
        s.contains("shared") || s.contains("sent") || s.contains("proposal") || s.contains("negotiat") -> Color(0xFF7C3AED)
        s.contains("received") || s.contains("new") || s.contains("enquir") -> Color(0xFF0284C7)
        else -> KarColors.Slate
    }
}

private fun probColor(p: Double): Color = when {
    p >= 75 -> Color(0xFF059669); p >= 40 -> Color(0xFFD97706); else -> Color(0xFFDC2626)
}

private fun compareKeys(a: Comparable<*>?, b: Comparable<*>?): Int = when {
    a is Double && b is Double -> a.compareTo(b)
    a is String && b is String -> a.compareTo(b)
    a is Double -> -1
    b is Double -> 1
    else -> a.toString().compareTo(b.toString())
}

private const val ROW_HEAD = 64 // width (dp) of the row number / menu column

/** Initial state (also used by the smoke test). */
object CommercialScreenState { var status: String? = null }

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CommercialsScreen(store: CommercialStore, onMessage: (String) -> Unit) {
    val columns by store.columns.collectAsState()
    val rows by store.rows.collectAsState()
    val byId = columns.associateBy { it.id }
    val visible = columns.filter { !it.hidden }
    val roleCol = { role: String -> columns.firstOrNull { it.role == role } }
    val statusCol = roleCol("STATUS")

    var query by rememberSaveable { mutableStateOf("") }
    var statusFilter by rememberSaveable { mutableStateOf(CommercialScreenState.status) }
    var sort by remember { mutableStateOf<Pair<String, Boolean>?>(null) }
    var editing by remember { mutableStateOf<Pair<String, String>?>(null) }
    var rowDialog by remember { mutableStateOf<CommercialRow?>(null) }
    var columnDialog by remember { mutableStateOf<CommercialColumn?>(null) }
    var manageColumns by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf<CommercialRow?>(null) }
    var upload by remember { mutableStateOf<UploadTable?>(null) }

    val filtered = rows.sortedBy { it.order }
        .filter { r -> statusFilter == null || (statusCol != null && (r.values[statusCol.id] ?: "").equals(statusFilter, true)) || (statusFilter == "" && statusCol != null && r.values[statusCol.id].isNullOrBlank()) }
        .filter { r ->
            query.isBlank() || columns.any { c -> Commercials.display(r, c, byId).contains(query.trim(), true) }
        }
        .let { list ->
            val s = sort ?: return@let list
            val col = byId[s.first] ?: return@let list
            val keyed = list.map { it to Commercials.sortKey(it, col, byId) }
            val (blank, set) = keyed.partition { it.second == null }
            val sorted = set.sortedWith { x, y -> compareKeys(x.second, y.second) }.map { it.first }
            (if (s.second) sorted else sorted.reversed()) + blank.map { it.first }
        }

    fun commit(row: CommercialRow, col: CommercialColumn, text: String) {
        val v = Commercials.normalise(col, text)
        if (v != (row.values[col.id] ?: "")) store.setCell(row.id, col.id, v)
    }

    fun move(rowId: String, colId: String, dx: Int, dy: Int) {
        val editable = visible.filter { it.type != ColType.FORMULA }
        val ri = filtered.indexOfFirst { it.id == rowId }
        var ci = editable.indexOfFirst { it.id == colId }
        var nr = ri + dy
        ci += dx
        if (ci >= editable.size) { ci = 0; nr += 1 }
        if (ci < 0) { ci = editable.size - 1; nr -= 1 }
        val r = filtered.getOrNull(nr)
        val c = editable.getOrNull(ci)
        editing = if (r != null && c != null && c.type != ColType.CHOICE && c.type != ColType.YESNO) r.id to c.id else null
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 14.dp)) {
        // ------------------------------------------------------------ title + tools
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Commercials Tracker", style = MaterialTheme.typography.titleLarge, color = KarColors.Navy)
                Text("All client commercials in one table. Click a cell to type; Tab / Enter move like Excel.",
                    style = MaterialTheme.typography.bodySmall, color = KarColors.Slate)
            }
            ToolButton("Bulk upload", Icons.Outlined.UploadFile) {
                FileDialogs.openAny("Bulk upload commercials (Excel or CSV)", listOf("xlsx", "xlsm", "csv"))?.let { f ->
                    runCatching { CommercialImport.read(f) }
                        .onSuccess { if (it.rows.isEmpty()) onMessage("No data rows found in ${f.name}") else upload = it }
                        .onFailure { onMessage("Could not read ${f.name}: ${it.message}") }
                }
            }
            Spacer(Modifier.width(6.dp))
            ToolButton("Columns", Icons.Outlined.ViewColumn) { manageColumns = true }
            Spacer(Modifier.width(6.dp))
            ToolButton("Add column", Icons.Outlined.Add) { columnDialog = CommercialColumn(id = "", name = "") }
            Spacer(Modifier.width(6.dp))
            ToolButton("Export (Excel)", Icons.Outlined.Download) {
                FileDialogs.save("Export commercials for Excel", "KAR-Commercials-${Ist.todayStr()}.csv")?.let { f ->
                    runCatching { f.writeText(Commercials.csv(visible, filtered), Charsets.UTF_8) }
                        .onSuccess { onMessage("Exported ${filtered.size} rows") }
                        .onFailure { onMessage("Export failed: ${it.message}") }
                }
            }
            Spacer(Modifier.width(6.dp))
            Surface(onClick = {
                val r = store.addRow(statusCol?.let { c -> mapOf(c.id to (statusFilter?.takeIf { it.isNotBlank() } ?: c.options.firstOrNull().orEmpty())) }?.filterValues { it.isNotBlank() } ?: emptyMap())
                query = ""; sort = null
                (visible.firstOrNull { it.type == ColType.TEXT } ?: visible.firstOrNull())?.let { editing = r.id to it.id }
            }, color = KarColors.SkyDeep, shape = RoundedCornerShape(8.dp)) {
                Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Add, null, tint = Color.White, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Add row", color = Color.White, style = MaterialTheme.typography.labelLarge)
                }
            }
        }
        Spacer(Modifier.height(12.dp))

        // ------------------------------------------------------------ summary cards
        val fteCol = roleCol("FTE"); val revCol = roleCol("REVENUE"); val probCol = roleCol("PROB"); val clientCol = roleCol("CLIENT")
        val base = filtered
        fun sum(c: CommercialColumn?, list: List<CommercialRow>) = c?.let { col -> list.sumOf { Commercials.value(it, col, byId) ?: 0.0 } }
        val wonRows = if (statusCol == null) emptyList() else base.filter { (it.values[statusCol.id] ?: "").lowercase().startsWith("won") }
        val openRows = if (statusCol == null) base else base.filter {
            val s = (it.values[statusCol.id] ?: "").lowercase(); !s.startsWith("won") && !s.contains("lost")
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            SummaryCard("Clients", (clientCol?.let { c -> base.mapNotNull { it.values[c.id]?.uppercase() }.distinct().size } ?: base.size).toString(),
                "${base.size} rows", KarColors.SkyDeep, Modifier.weight(1f))
            if (fteCol != null) SummaryCard("Total FTE", Commercials.fmt(sum(fteCol, base) ?: 0.0, ColType.NUMBER),
                "Won: " + Commercials.fmt(sum(fteCol, wonRows) ?: 0.0, ColType.NUMBER), Color(0xFF7C3AED), Modifier.weight(1f))
            if (revCol != null) SummaryCard("Monthly revenue", Commercials.inrShort(sum(revCol, base) ?: 0.0),
                "Annual " + Commercials.inrShort((sum(revCol, base) ?: 0.0) * 12), Color(0xFF0284C7), Modifier.weight(1f))
            if (revCol != null) SummaryCard("Won (monthly)", Commercials.inrShort(sum(revCol, wonRows) ?: 0.0),
                "${wonRows.size} won", Color(0xFF059669), Modifier.weight(1f))
            if (revCol != null && probCol != null) {
                val weighted = openRows.sumOf { r -> (Commercials.value(r, revCol, byId) ?: 0.0) * ((Commercials.value(r, probCol, byId) ?: 0.0) / 100) }
                SummaryCard("Weighted pipeline", Commercials.inrShort(weighted), "Open rows × probability", Color(0xFFD97706), Modifier.weight(1f))
            }
        }
        Spacer(Modifier.height(10.dp))

        // ------------------------------------------------------------ filters
        Row(verticalAlignment = Alignment.CenterVertically) {
            FlowRow(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                StatusChip("All", rows.size, null, statusFilter == null) { statusFilter = null; CommercialScreenState.status = null }
                if (statusCol != null) {
                    val values = (statusCol.options + rows.mapNotNull { it.values[statusCol.id] }).distinctBy { it.lowercase() }
                    values.forEach { v ->
                        val n = rows.count { (it.values[statusCol.id] ?: "").equals(v, true) }
                        StatusChip(v, n, commercialStatusColor(v), statusFilter.equals(v, true)) { statusFilter = v; CommercialScreenState.status = v }
                    }
                    val blank = rows.count { it.values[statusCol.id].isNullOrBlank() }
                    if (blank > 0) StatusChip("No status", blank, KarColors.SlateLight, statusFilter == "") { statusFilter = "" }
                }
            }
            Surface(color = KarColors.Card, shape = RoundedCornerShape(8.dp), border = BorderStroke(1.dp, KarColors.Border), modifier = Modifier.width(260.dp)) {
                Row(Modifier.padding(horizontal = 10.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Search, null, tint = KarColors.Slate, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Box(Modifier.weight(1f)) {
                        if (query.isEmpty()) Text("Search client, location…", style = MaterialTheme.typography.bodySmall, color = KarColors.SlateLight)
                        BasicTextField(query, { query = it }, singleLine = true, textStyle = MaterialTheme.typography.bodySmall.copy(color = KarColors.Ink),
                            cursorBrush = SolidColor(KarColors.SkyDeep), modifier = Modifier.fillMaxWidth())
                    }
                }
            }
        }
        Spacer(Modifier.height(10.dp))

        // ------------------------------------------------------------ table
        DepthCard(Modifier.fillMaxWidth().weight(1f), elevation = 2.dp, shape = RoundedCornerShape(10.dp)) {
            val hScroll = rememberScrollState()
            val listState = rememberLazyListState()
            // the client column (and row number) stay fixed while the other columns scroll sideways
            val frozen = visible.firstOrNull { it.role == "CLIENT" } ?: visible.firstOrNull()
            val rest = visible.filter { it.id != frozen?.id }
            val frozenWidth = (ROW_HEAD + (frozen?.width ?: 0)).dp
            CompositionLocalProvider(LocalScrollbarStyle provides tableScrollbarStyle()) {
                BoxWithConstraints(Modifier.fillMaxSize()) {
                    val restWidth = maxOf(rest.sumOf { it.width }.dp, maxWidth - frozenWidth - SCROLLBAR)
                    Column(Modifier.fillMaxSize()) {
                        // header
                        TableLine(KarColors.Navy, restWidth, hScroll, Modifier.height(34.dp), frozenPart = {
                            Text("#", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.7f),
                                modifier = Modifier.width(ROW_HEAD.dp).padding(start = 12.dp))
                            frozen?.let { c -> HeaderCell(c, sort, onSort = { sort = nextSort(sort, c.id) }, onEdit = { columnDialog = c }) }
                        }) {
                            rest.forEach { c -> HeaderCell(c, sort, onSort = { sort = nextSort(sort, c.id) }, onEdit = { columnDialog = c }) }
                        }
                        Row(Modifier.fillMaxWidth().weight(1f)) {
                            if (filtered.isEmpty()) {
                                Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) {
                                    Text(if (rows.isEmpty()) "No commercials yet — click “Add row” to start." else "No rows match the filter.",
                                        style = MaterialTheme.typography.bodyMedium, color = KarColors.Slate)
                                }
                            } else {
                                LazyColumn(Modifier.weight(1f).fillMaxHeight(), state = listState) {
                                    itemsIndexed(filtered, key = { _, r -> r.id }) { i, r ->
                                        val cell: @Composable (CommercialColumn) -> Unit = { c -> GridCell(
                                            row = r, col = c, byId = byId,
                                            editing = editing == (r.id to c.id),
                                            onStartEdit = { editing = r.id to c.id },
                                            onCommit = { text, dx, dy ->
                                                commit(r, c, text)
                                                if (dx == 0 && dy == 0) { if (editing == (r.id to c.id)) editing = null } else move(r.id, c.id, dx, dy)
                                            },
                                            onCancel = { editing = null },
                                            onPick = { v -> store.setCell(r.id, c.id, v) },
                                        ) }
                                        TableLine(if (i % 2 == 1) KarColors.Tint else KarColors.Card, restWidth, hScroll, Modifier.height(34.dp), scrollbarGap = false, frozenPart = {
                                            RowMenu(i + 1, onEdit = { rowDialog = r }, onDuplicate = { store.duplicate(r.id); onMessage("Row duplicated") },
                                                onDelete = { confirmDelete = r })
                                            frozen?.let { cell(it) }
                                        }) {
                                            rest.forEach { cell(it) }
                                        }
                                        HorizontalDivider(color = KarColors.Border.copy(alpha = 0.6f))
                                    }
                                }
                            }
                            VerticalScrollbar(rememberScrollbarAdapter(listState), Modifier.fillMaxHeight().width(SCROLLBAR))
                        }
                        // totals
                        val total: @Composable (CommercialColumn) -> Unit = { c ->
                            val t = Commercials.formatType(c)
                            val text = when {
                                c.role == "CLIENT" -> "${filtered.size} rows"
                                t == ColType.PERCENT -> filtered.mapNotNull { Commercials.value(it, c, byId) }.takeIf { it.isNotEmpty() }
                                    ?.let { "Avg " + Commercials.fmt(it.average(), ColType.PERCENT) } ?: ""
                                t == ColType.NUMBER || t == ColType.CURRENCY -> filtered.mapNotNull { Commercials.value(it, c, byId) }.takeIf { it.isNotEmpty() }
                                    ?.let { Commercials.fmt(it.sum(), t) } ?: ""
                                else -> ""
                            }
                            Box(Modifier.width(c.width.dp).fillMaxHeight().gridLine().padding(horizontal = CELL_PAD),
                                contentAlignment = if (isRightAligned(c)) Alignment.CenterEnd else Alignment.CenterStart) {
                                Text(text, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = KarColors.Navy, maxLines = 1,
                                    overflow = TextOverflow.Ellipsis)
                            }
                        }
                        TableLine(KarColors.SkyWash, restWidth, hScroll, Modifier.height(34.dp), frozenPart = {
                            Text("Total", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = KarColors.Navy,
                                modifier = Modifier.width(ROW_HEAD.dp).padding(start = 12.dp))
                            frozen?.let { total(it) }
                        }) {
                            rest.forEach { total(it) }
                        }
                        // scroll bar to see all the columns
                        Row(Modifier.fillMaxWidth().background(KarColors.SkyWash), verticalAlignment = Alignment.CenterVertically) {
                            Text(if (hScroll.maxValue > 0) "◀ ▶  scroll for more columns" else "", style = MaterialTheme.typography.labelSmall, color = KarColors.SlateLight,
                                maxLines = 1, modifier = Modifier.width(frozenWidth).padding(start = 12.dp))
                            HorizontalScrollbar(rememberScrollbarAdapter(hScroll), Modifier.weight(1f).height(SCROLLBAR + 4.dp).padding(vertical = 2.dp))
                            Spacer(Modifier.width(SCROLLBAR))
                        }
                    }
                }
            }
        }
    }

    rowDialog?.let { r ->
        CommercialRowDialog(r, columns, onDismiss = { rowDialog = null }, onSave = { store.saveRow(it); rowDialog = null; onMessage("Row saved") },
            onDelete = { rowDialog = null; confirmDelete = r })
    }
    columnDialog?.let { c ->
        CommercialColumnDialog(c, columns, onDismiss = { columnDialog = null },
            onSave = { col -> store.saveColumn(if (col.id.isBlank()) col.copy(id = java.util.UUID.randomUUID().toString()) else col); columnDialog = null; onMessage("Column saved") },
            onDelete = if (c.id.isBlank()) null else ({ store.deleteColumn(c.id); columnDialog = null; onMessage("Column deleted") }))
    }
    upload?.let { t ->
        BulkUploadDialog(t, columns, onDismiss = { upload = null }, onTemplate = {
            FileDialogs.save("Save upload template", "KAR-Commercials-template.csv")?.let { f ->
                runCatching { f.writeText(CommercialImport.template(columns), Charsets.UTF_8) }
                    .onSuccess { onMessage("Template saved — fill it in Excel and upload") }
                    .onFailure { onMessage("Could not save: ${it.message}") }
            }
        }) { newCols, data, replace, keys ->
            val (added, updated) = store.importRows(newCols, data, replace, keys)
            upload = null; statusFilter = null; query = ""; sort = null
            onMessage("Uploaded: $added added" + (if (updated > 0) ", $updated updated" else "") + (if (newCols.isNotEmpty()) ", ${newCols.size} new column(s)" else ""))
        }
    }
    if (manageColumns) {
        ManageColumnsDialog(columns, store, onEdit = { columnDialog = it }, onAdd = { columnDialog = CommercialColumn(id = "", name = "") },
            onDismiss = { manageColumns = false })
    }
    confirmDelete?.let { r ->
        val name = roleCol("CLIENT")?.let { r.values[it.id] } ?: "this row"
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            containerColor = KarColors.Card,
            title = { Text("Delete $name?") },
            text = { Text("The row will be removed from the commercials tracker.", color = KarColors.Slate) },
            confirmButton = { TextButton(onClick = { store.deleteRows(setOf(r.id)); confirmDelete = null; onMessage("Row deleted") }) { Text("Delete", color = KarColors.Red) } },
            dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text("Cancel") } },
        )
    }
}

// =============================================================================
// Grid pieces
// =============================================================================

@Composable
private fun HeaderCell(c: CommercialColumn, sort: Pair<String, Boolean>?, onSort: () -> Unit, onEdit: () -> Unit) {
    val right = isRightAligned(c)
    val menu: @Composable () -> Unit = { Icon(Icons.Outlined.MoreVert, "Edit column", tint = Color.White.copy(alpha = 0.55f),
        modifier = Modifier.size(20.dp).clickable(onClick = onEdit).padding(3.dp)) }
    val arrow: @Composable () -> Unit = { if (sort?.first == c.id) Icon(if (sort.second) Icons.Outlined.ArrowUpward else Icons.Outlined.ArrowDownward, null,
        tint = KarColors.Gold, modifier = Modifier.padding(horizontal = 2.dp).size(12.dp)) }
    Row(Modifier.width(c.width.dp).fillMaxHeight().gridLine(Color.White.copy(alpha = 0.15f)).clickable(onClick = onSort)
        .padding(start = if (right) 2.dp else CELL_PAD, end = if (right) CELL_PAD else 2.dp), verticalAlignment = Alignment.CenterVertically) {
        if (right) {
            menu(); Spacer(Modifier.weight(1f)); arrow()
            Text(c.name, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 1,
                overflow = TextOverflow.Ellipsis, textAlign = TextAlign.End, modifier = Modifier.weight(5f, fill = false))
        } else {
            Text(c.name, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 1,
                overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(5f, fill = false))
            arrow(); Spacer(Modifier.weight(1f)); menu()
        }
    }
}

/** Numbers, amounts and percentages line up on the right, everything else on the left. */
private fun isRightAligned(c: CommercialColumn) = ColType.numeric(Commercials.formatType(c))

private val CELL_PAD = 10.dp
private val SCROLLBAR = 12.dp

/** Thin line on the right edge of a cell (grid look). */
private fun Modifier.gridLine(color: Color? = null): Modifier = drawBehind {
    val c = color ?: KarColors.Border.copy(alpha = 0.7f)
    drawLine(c, Offset(size.width - 0.5f, 0f), Offset(size.width - 0.5f, size.height), strokeWidth = 1f)
}

private fun nextSort(sort: Pair<String, Boolean>?, id: String): Pair<String, Boolean>? =
    when { sort?.first != id -> id to true; sort?.second == true -> id to false; else -> null }

/** One table line: fixed part on the left, the remaining columns scroll sideways together (shared [h] state). */
@Composable
private fun TableLine(
    bg: Color,
    restWidth: androidx.compose.ui.unit.Dp,
    h: androidx.compose.foundation.ScrollState,
    modifier: Modifier = Modifier,
    scrollbarGap: Boolean = true,
    frozenPart: @Composable () -> Unit,
    scrollPart: @Composable () -> Unit,
) {
    Row(modifier.fillMaxWidth().background(bg), verticalAlignment = Alignment.CenterVertically) {
        Row(Modifier.fillMaxHeight().drawBehind {
            drawLine(KarColors.Slate.copy(alpha = 0.35f), Offset(size.width - 1f, 0f), Offset(size.width - 1f, size.height), strokeWidth = 2f)
        }, verticalAlignment = Alignment.CenterVertically) { frozenPart() }
        Box(Modifier.weight(1f).fillMaxHeight().horizontalScroll(h)) {
            Row(Modifier.width(restWidth).fillMaxHeight(), verticalAlignment = Alignment.CenterVertically) { scrollPart() }
        }
        if (scrollbarGap) Spacer(Modifier.width(SCROLLBAR))
    }
}

@Composable
private fun tableScrollbarStyle() = defaultScrollbarStyle().copy(
    thickness = 9.dp,
    shape = RoundedCornerShape(5.dp),
    unhoverColor = KarColors.Slate.copy(alpha = 0.45f),
    hoverColor = KarColors.SkyDeep.copy(alpha = 0.85f),
    minimalHeight = 24.dp,
)

@Composable
private fun RowMenu(n: Int, onEdit: () -> Unit, onDuplicate: () -> Unit, onDelete: () -> Unit) {
    var open by remember { mutableStateOf(false) }
    Row(Modifier.width(ROW_HEAD.dp).padding(start = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("$n", style = MaterialTheme.typography.labelSmall, color = KarColors.SlateLight, modifier = Modifier.weight(1f))
        Box {
            Icon(Icons.Outlined.MoreVert, "Row options", tint = KarColors.Slate, modifier = Modifier.size(24.dp).clickable { open = true }.padding(4.dp))
            DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                DropdownMenuItem(text = { Text("Edit row…") }, leadingIcon = { Icon(Icons.Outlined.Edit, null) }, onClick = { open = false; onEdit() })
                DropdownMenuItem(text = { Text("Duplicate") }, leadingIcon = { Icon(Icons.Outlined.ContentCopy, null) }, onClick = { open = false; onDuplicate() })
                DropdownMenuItem(text = { Text("Delete", color = KarColors.Red) }, leadingIcon = { Icon(Icons.Outlined.Delete, null, tint = KarColors.Red) }, onClick = { open = false; onDelete() })
            }
        }
        Spacer(Modifier.width(4.dp))
    }
}

/** One table cell: shows the value, edits in place (text) or with a drop-down (lists, yes/no). */
@Composable
private fun GridCell(
    row: CommercialRow,
    col: CommercialColumn,
    byId: Map<String, CommercialColumn>,
    editing: Boolean,
    onStartEdit: () -> Unit,
    onCommit: (String, Int, Int) -> Unit,
    onCancel: () -> Unit,
    onPick: (String) -> Unit,
) {
    val shown = Commercials.display(row, col, byId)
    val type = Commercials.formatType(col)
    val numeric = ColType.numeric(type)
    val mod = Modifier.width(col.width.dp).fillMaxHeight().gridLine()
    when {
        col.type == ColType.FORMULA -> Box(mod.background(KarColors.SkyWash.copy(alpha = 0.5f)).padding(horizontal = CELL_PAD), contentAlignment = if (numeric) Alignment.CenterEnd else Alignment.CenterStart) {
            Text(shown, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = KarColors.Navy, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        col.type == ColType.CHOICE || col.type == ColType.YESNO -> {
            var open by remember { mutableStateOf(false) }
            val options = if (col.type == ColType.YESNO) listOf("Yes", "No") else col.options
            Box(mod.clickable { open = true }.padding(horizontal = CELL_PAD), contentAlignment = Alignment.CenterStart) {
                if (shown.isNotBlank()) {
                    val c = if (col.type == ColType.YESNO) (if (shown.equals("Yes", true)) Color(0xFF059669) else KarColors.Slate) else commercialStatusColor(shown)
                    Text(shown, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold, color = c, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.background(c.copy(alpha = 0.12f), RoundedCornerShape(4.dp)).padding(horizontal = 6.dp, vertical = 3.dp))
                } else Text("▾", color = KarColors.SlateLight, style = MaterialTheme.typography.labelSmall)
                DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                    options.forEach { o ->
                        val c = if (col.type == ColType.YESNO) (if (o == "Yes") Color(0xFF059669) else KarColors.Slate) else commercialStatusColor(o)
                        DropdownMenuItem(text = { Text(o, color = c, fontWeight = FontWeight.SemiBold) }, onClick = { open = false; onPick(o) })
                    }
                    if (shown.isNotBlank()) DropdownMenuItem(text = { Text("Clear", color = KarColors.Slate) }, onClick = { open = false; onPick("") })
                }
            }
        }
        editing -> {
            val start = when {
                col.type == ColType.DATE -> shown
                numeric -> Commercials.number(row.values[col.id])?.let { if (it % 1.0 == 0.0) it.toLong().toString() else it.toString() } ?: row.values[col.id].orEmpty()
                else -> row.values[col.id].orEmpty()
            }
            var tf by remember { mutableStateOf(TextFieldValue(start, TextRange(0, start.length))) }
            val fr = remember { FocusRequester() }
            var focused by remember { mutableStateOf(false) }
            var done by remember { mutableStateOf(false) }
            fun finish(dx: Int, dy: Int) { if (!done) { done = true; onCommit(tf.text, dx, dy) } }
            LaunchedEffect(Unit) { fr.requestFocus() }
            val latestText = rememberUpdatedState(tf.text)
            val latestCommit = rememberUpdatedState(onCommit)
            DisposableEffect(Unit) { onDispose { if (!done) { done = true; latestCommit.value(latestText.value, 0, 0) } } }
            Box(mod.background(KarColors.Card).padding(2.dp).background(KarColors.SkyDeep.copy(alpha = 0.08f)), contentAlignment = Alignment.CenterStart) {
                BasicTextField(
                    value = tf,
                    onValueChange = { tf = if (col.type == ColType.TEXT) it.copy(text = it.text.uppercase()) else it },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.labelMedium.copy(color = KarColors.Ink, textAlign = if (numeric) TextAlign.End else TextAlign.Start),
                    cursorBrush = SolidColor(KarColors.SkyDeep),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = CELL_PAD - 2.dp).focusRequester(fr)
                        .onFocusChanged { if (it.isFocused) focused = true else if (focused) finish(0, 0) }
                        .onPreviewKeyEvent { e ->
                            if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                            when (e.key) {
                                Key.Enter, Key.NumPadEnter -> { finish(0, if (e.isShiftPressed) -1 else 1); true }
                                Key.Tab -> { finish(if (e.isShiftPressed) -1 else 1, 0); true }
                                Key.Escape -> { done = true; onCancel(); true }
                                else -> false
                            }
                        },
                )
            }
        }
        else -> {
            val color = when {
                col.role == "PROB" -> Commercials.number(row.values[col.id])?.let { probColor(it) } ?: KarColors.Ink
                col.type == ColType.DATE && shown.equals("TBD", true) -> KarColors.Slate
                col.role == "CLIENT" -> KarColors.Navy
                else -> KarColors.Ink
            }
            Box(mod.clickable(onClick = onStartEdit).padding(horizontal = CELL_PAD), contentAlignment = if (numeric) Alignment.CenterEnd else Alignment.CenterStart) {
                Text(shown, style = MaterialTheme.typography.labelMedium, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    fontWeight = if (col.role == "CLIENT" || col.role == "PROB") FontWeight.SemiBold else FontWeight.Normal)
            }
        }
    }
}

@Composable
private fun ToolButton(label: String, icon: ImageVector, onClick: () -> Unit) {
    Surface(onClick = onClick, color = KarColors.Card, shape = RoundedCornerShape(8.dp), border = BorderStroke(1.dp, KarColors.Border)) {
        Row(Modifier.padding(horizontal = 10.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = KarColors.SkyDeep, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text(label, style = MaterialTheme.typography.labelMedium, color = KarColors.Ink)
        }
    }
}

@Composable
private fun StatusChip(label: String, count: Int, color: Color?, selected: Boolean, onClick: () -> Unit) {
    val accent = color ?: KarColors.Navy
    Surface(onClick = onClick, color = if (selected) accent else KarColors.Card, shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, if (selected) accent else KarColors.Border)) {
        Row(Modifier.padding(horizontal = 10.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
            if (color != null && !selected) Box(Modifier.size(8.dp).background(color, RoundedCornerShape(4.dp)))
            if (color != null && !selected) Spacer(Modifier.width(6.dp))
            Text(label, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold, color = if (selected) Color.White else KarColors.Ink)
            Spacer(Modifier.width(6.dp))
            Text("$count", style = MaterialTheme.typography.labelSmall, color = if (selected) Color.White.copy(alpha = 0.85f) else KarColors.Slate)
        }
    }
}

@Composable
private fun SummaryCard(label: String, value: String, sub: String, accent: Color, modifier: Modifier) {
    DepthCard(modifier, elevation = 2.dp, shape = RoundedCornerShape(10.dp)) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(4.dp).height(36.dp).background(accent, RoundedCornerShape(2.dp)))
            Spacer(Modifier.width(10.dp))
            Column {
                Text(label, style = MaterialTheme.typography.labelSmall, color = KarColors.Slate)
                Text(value, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = KarColors.Navy, maxLines = 1)
                Text(sub, style = MaterialTheme.typography.labelSmall, color = KarColors.SlateLight, maxLines = 1)
            }
        }
    }
}

// =============================================================================
// Dialogs
// =============================================================================

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CommercialRowDialog(row: CommercialRow, columns: List<CommercialColumn>, onDismiss: () -> Unit, onSave: (CommercialRow) -> Unit, onDelete: () -> Unit) {
    var values by remember { mutableStateOf(row.values) }
    val byId = columns.associateBy { it.id }
    val preview = row.copy(values = values)
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = KarColors.Card,
        title = { Text("Edit row") },
        text = {
            Column(Modifier.widthIn(min = 560.dp).verticalScroll(rememberScrollState())) {
                columns.forEach { c ->
                    Text(c.name, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = KarColors.Navy,
                        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp))
                    when (c.type) {
                        ColType.FORMULA -> Text(Commercials.display(preview, c, byId).ifBlank { "–" } + "   (calculated)", style = MaterialTheme.typography.bodySmall, color = KarColors.Slate)
                        ColType.CHOICE, ColType.YESNO -> FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            val opts = if (c.type == ColType.YESNO) listOf("Yes", "No") else c.options
                            opts.forEach { o ->
                                PillChip(o, values[c.id].equals(o, true), commercialStatusColor(o)) {
                                    values = if (values[c.id].equals(o, true)) values - c.id else values + (c.id to o)
                                }
                            }
                        }
                        ColType.DATE -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            val iso = Commercials.parseDate(values[c.id])?.toString()
                            DateBox(c.name, iso, { d -> values = if (d == null) values - c.id else values + (c.id to d) }, Modifier.width(220.dp))
                            PillChip("TBD", values[c.id].equals("TBD", true)) { values = values + (c.id to "TBD") }
                        }
                        else -> Column {
                        OutlinedTextField(
                            value = values[c.id].orEmpty(),
                            onValueChange = { v -> values = values + (c.id to v) },
                            singleLine = true, modifier = Modifier.fillMaxWidth(),
                        )
                        if (ColType.numeric(c.type) && values[c.id].orEmpty().isNotBlank()) Text(Commercials.display(preview, c, byId),
                            style = MaterialTheme.typography.labelSmall, color = KarColors.Slate, modifier = Modifier.padding(top = 2.dp))
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(row.copy(values = columns.associate { c -> c.id to Commercials.normalise(c, values[c.id].orEmpty()) }.filterValues { it.isNotBlank() } + values.filterKeys { k -> columns.none { it.id == k } })) }) { Text("Save") }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onDelete) { Text("Delete", color = KarColors.Red) }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
    )
}

private val WIDTHS = listOf(80 to "Narrow", 120 to "Medium", 170 to "Wide", 240 to "Extra wide")
private val OPS = listOf("MUL" to "×  multiply", "ADD" to "+  add", "SUB" to "−  subtract", "DIV" to "÷  divide")

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CommercialColumnDialog(col: CommercialColumn, all: List<CommercialColumn>, onDismiss: () -> Unit, onSave: (CommercialColumn) -> Unit, onDelete: (() -> Unit)?) {
    val isNew = col.id.isBlank()
    var name by remember { mutableStateOf(col.name) }
    var type by remember { mutableStateOf(col.type) }
    var options by remember { mutableStateOf(col.options.joinToString(", ")) }
    var width by remember { mutableStateOf(col.width) }
    var hidden by remember { mutableStateOf(col.hidden) }
    val numericCols = all.filter { it.id != col.id && (ColType.numeric(it.type) || it.type == ColType.FORMULA) }
    var fa by remember { mutableStateOf(col.formula?.a ?: numericCols.firstOrNull()?.id.orEmpty()) }
    var op by remember { mutableStateOf(col.formula?.op ?: "MUL") }
    var useConst by remember { mutableStateOf(col.formula?.b == null && col.formula?.constant != null) }
    var fb by remember { mutableStateOf(col.formula?.b ?: numericCols.getOrNull(1)?.id ?: numericCols.firstOrNull()?.id.orEmpty()) }
    var constant by remember { mutableStateOf(col.formula?.constant?.let { if (it % 1.0 == 0.0) it.toLong().toString() else it.toString() } ?: "12") }
    var format by remember { mutableStateOf(col.formula?.format ?: ColType.CURRENCY) }
    var confirmDelete by remember { mutableStateOf(false) }
    val formulaOk = type != ColType.FORMULA || (fa.isNotBlank() && (if (useConst) Commercials.number(constant) != null else fb.isNotBlank()))
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = KarColors.Card,
        title = { Text(if (isNew) "Add column" else "Edit column") },
        text = {
            Column(Modifier.widthIn(min = 520.dp).verticalScroll(rememberScrollState())) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Column name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Label("Type of data")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    ColType.ALL.forEach { t ->
                        if (t != ColType.FORMULA || numericCols.isNotEmpty()) PillChip(ColType.label(t), type == t) { type = t }
                    }
                }
                if (type == ColType.CHOICE) {
                    Label("List values (separate with commas)")
                    OutlinedTextField(value = options, onValueChange = { options = it }, singleLine = true, modifier = Modifier.fillMaxWidth())
                }
                if (type == ColType.FORMULA) {
                    Label("Formula")
                    val colOpts = numericCols.map { it.id to it.name.uppercase() }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        SmallMenu(fa, colOpts) { fa = it }
                        SmallMenu(op, OPS) { op = it }
                        if (useConst) OutlinedTextField(value = constant, onValueChange = { constant = it }, singleLine = true, modifier = Modifier.width(110.dp))
                        else SmallMenu(fb, colOpts) { fb = it }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                        Text("Use a fixed number instead of a column", style = MaterialTheme.typography.bodySmall, color = KarColors.Ink, modifier = Modifier.weight(1f))
                        Switch(checked = useConst, onCheckedChange = { useConst = it }, colors = SwitchDefaults.colors(checkedTrackColor = KarColors.SkyDeep))
                    }
                    Label("Show result as")
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(ColType.CURRENCY, ColType.NUMBER, ColType.PERCENT).forEach { f -> PillChip(ColType.label(f), format == f) { format = f } }
                    }
                    Text("Percent columns count as fractions when multiplied (e.g. Revenue × Prob.).", style = MaterialTheme.typography.labelSmall, color = KarColors.SlateLight,
                        modifier = Modifier.padding(top = 4.dp))
                }
                Label("Column width")
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    WIDTHS.forEach { (w, l) -> PillChip(l, width == w || (WIDTHS.none { it.first == width } && w == 120)) { width = w } }
                }
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                    Text("Hide this column", style = MaterialTheme.typography.bodySmall, color = KarColors.Ink, modifier = Modifier.weight(1f))
                    Switch(checked = hidden, onCheckedChange = { hidden = it }, colors = SwitchDefaults.colors(checkedTrackColor = KarColors.SkyDeep))
                }
            }
        },
        confirmButton = {
            TextButton(enabled = name.isNotBlank() && formulaOk, onClick = {
                onSave(col.copy(
                    name = name.trim(), type = type, width = width, hidden = hidden,
                    options = if (type == ColType.CHOICE) options.split(",").map { it.trim() }.filter { it.isNotEmpty() }.distinctBy { it.lowercase() } else col.options,
                    formula = if (type == ColType.FORMULA) ColFormula(a = fa, op = op, b = if (useConst) null else fb, constant = if (useConst) Commercials.number(constant) else null, format = format) else null,
                ))
            }) { Text("Save") }
        },
        dismissButton = {
            Row {
                if (onDelete != null) TextButton(onClick = { confirmDelete = true }) { Text("Delete", color = KarColors.Red) }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
    )
    if (confirmDelete && onDelete != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            containerColor = KarColors.Card,
            title = { Text("Delete column \"${col.name}\"?") },
            text = { Text("The column and the data typed in it will be removed from every row. Formulas that use it become plain numbers.", color = KarColors.Slate) },
            confirmButton = { TextButton(onClick = { confirmDelete = false; onDelete() }) { Text("Delete", color = KarColors.Red) } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun Label(text: String) {
    Text(text, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = KarColors.Navy, modifier = Modifier.padding(top = 10.dp, bottom = 4.dp))
}

@Composable
private fun ManageColumnsDialog(columns: List<CommercialColumn>, store: CommercialStore, onEdit: (CommercialColumn) -> Unit, onAdd: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = KarColors.Card,
        title = { Text("Columns") },
        text = {
            Column(Modifier.widthIn(min = 480.dp).verticalScroll(rememberScrollState())) {
                Text("Reorder, hide or edit columns. Your own columns can hold text, numbers, amounts, dates, lists, yes/no or formulas.",
                    style = MaterialTheme.typography.bodySmall, color = KarColors.Slate, modifier = Modifier.padding(bottom = 8.dp))
                columns.forEachIndexed { i, c ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(c.name, style = MaterialTheme.typography.labelLarge, color = if (c.hidden) KarColors.SlateLight else KarColors.Ink)
                            Text(ColType.label(c.type) + (c.formula?.let { f ->
                                val n = { id: String? -> columns.firstOrNull { it.id == id }?.name ?: "?" }
                                ": " + n(f.a) + " " + f.symbol() + " " + (f.b?.let { n(it) } ?: f.constant?.let { Commercials.fmt(it, ColType.NUMBER) } ?: "?")
                            } ?: ""), style = MaterialTheme.typography.labelSmall, color = KarColors.Slate)
                        }
                        IconButton(enabled = i > 0, onClick = { store.moveColumn(c.id, -1) }) { Icon(Icons.Outlined.ArrowUpward, "Move up", Modifier.size(16.dp)) }
                        IconButton(enabled = i < columns.size - 1, onClick = { store.moveColumn(c.id, 1) }) { Icon(Icons.Outlined.ArrowDownward, "Move down", Modifier.size(16.dp)) }
                        IconButton(onClick = { store.saveColumn(c.copy(hidden = !c.hidden)) }) {
                            Icon(if (c.hidden) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility, if (c.hidden) "Show" else "Hide", Modifier.size(16.dp))
                        }
                        IconButton(onClick = { onEdit(c) }) { Icon(Icons.Outlined.Edit, "Edit", Modifier.size(16.dp)) }
                    }
                    HorizontalDivider(color = KarColors.Border.copy(alpha = 0.6f))
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } },
        dismissButton = { TextButton(onClick = onAdd) { Text("Add column") } },
    )
}


// =============================================================================
// Bulk upload
// =============================================================================

private const val SKIP = "__skip"
private const val NEW = "__new"

private fun guessType(values: List<String>): String {
    val v = values.filter { it.isNotBlank() }
    if (v.isEmpty()) return ColType.TEXT
    return when {
        v.all { it.trim().endsWith("%") && Commercials.number(it) != null } -> ColType.PERCENT
        v.all { it.contains("₹") || it.startsWith("Rs", true) } && v.all { Commercials.number(it) != null } -> ColType.CURRENCY
        v.all { Commercials.number(it) != null } -> ColType.NUMBER
        v.all { Commercials.parseDate(it) != null || it.equals("TBD", true) } && v.any { Commercials.parseDate(it) != null } -> ColType.DATE
        v.all { it.equals("yes", true) || it.equals("no", true) || it.equals("y", true) || it.equals("n", true) } -> ColType.YESNO
        else -> ColType.TEXT
    }
}

@Composable
private fun BulkUploadDialog(
    table: UploadTable,
    columns: List<CommercialColumn>,
    onDismiss: () -> Unit,
    onTemplate: () -> Unit,
    onImport: (newColumns: List<CommercialColumn>, rows: List<Map<String, String>>, replace: Boolean, matchKeys: List<String>) -> Unit,
) {
    val byId = columns.associateBy { it.id }
    var mapping by remember {
        mutableStateOf(CommercialImport.autoMatch(table.headers, columns).mapIndexed { i, id ->
            when {
                id != null && byId[id]?.type == ColType.FORMULA -> SKIP          // calculated in the tracker
                id != null -> id
                table.rows.all { it[i].isBlank() } -> SKIP
                else -> NEW
            }
        })
    }
    var replace by remember { mutableStateOf(false) }
    val keyCols = listOf("client", "process", "location").filter { id -> byId.containsKey(id) }
    var update by remember { mutableStateOf(true) }
    val targets = listOf(SKIP to "— Skip —", NEW to "+ Add as new column") + columns.filter { it.type != ColType.FORMULA }.map { it.id to it.name.uppercase() }

    // build the result (new columns get fresh ids)
    fun build(): Triple<List<CommercialColumn>, List<Map<String, String>>, List<String>> {
        val newCols = HashMap<Int, CommercialColumn>()
        mapping.forEachIndexed { i, m ->
            if (m == NEW) newCols[i] = CommercialColumn(name = table.headers[i], type = guessType(table.rows.map { it[i] }), width = 130)
        }
        val target = mapping.mapIndexed { i, m -> when (m) { SKIP -> null; NEW -> newCols[i]; else -> byId[m] } }
        val data = table.rows.map { r ->
            buildMap {
                target.forEachIndexed { i, c -> if (c != null) CommercialImport.convert(c, r[i]).takeIf { it.isNotBlank() }?.let { put(c.id, it) } }
            }
        }.filter { it.isNotEmpty() }
        return Triple(newCols.values.toList(), data, if (update && !replace) keyCols.filter { k -> mapping.contains(k) } else emptyList())
    }
    val preview = build()

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = KarColors.Card,
        title = { Text("Bulk upload commercials") },
        text = {
            Column(Modifier.widthIn(min = 640.dp).verticalScroll(rememberScrollState())) {
                Text("${table.source} · ${table.rows.size} rows · ${table.headers.size} columns", style = MaterialTheme.typography.bodySmall, color = KarColors.Slate)
                Text("Check where each column of your file goes. Monthly Revenue is calculated by the tracker, so it is skipped.",
                    style = MaterialTheme.typography.labelSmall, color = KarColors.SlateLight, modifier = Modifier.padding(top = 2.dp, bottom = 6.dp))
                Row(Modifier.fillMaxWidth().background(KarColors.SkyWash).padding(horizontal = 10.dp, vertical = 6.dp)) {
                    Text("Column in your file", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = KarColors.Slate, modifier = Modifier.weight(1.2f))
                    Text("Sample", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = KarColors.Slate, modifier = Modifier.weight(1f))
                    Text("Goes to", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = KarColors.Slate, modifier = Modifier.weight(1.3f))
                }
                table.headers.forEachIndexed { i, h ->
                    Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(h, style = MaterialTheme.typography.labelMedium, color = KarColors.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1.2f))
                        Text(table.rows.firstOrNull { it[i].isNotBlank() }?.get(i).orEmpty(), style = MaterialTheme.typography.labelSmall, color = KarColors.Slate,
                            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f).padding(end = 8.dp))
                        Box(Modifier.weight(1.3f)) {
                            SmallMenu(mapping[i], targets) { v ->
                                // a tracker column can take only one file column
                                mapping = mapping.mapIndexed { j, m -> if (j == i) v else if (m == v && v != SKIP && v != NEW) SKIP else m }
                            }
                        }
                    }
                    HorizontalDivider(color = KarColors.Border.copy(alpha = 0.5f))
                }
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Replace all existing rows (otherwise rows are added)", style = MaterialTheme.typography.bodySmall, color = if (replace) KarColors.Red else KarColors.Ink, modifier = Modifier.weight(1f))
                    Switch(checked = replace, onCheckedChange = { replace = it }, colors = SwitchDefaults.colors(checkedTrackColor = KarColors.Red))
                }
                if (!replace && keyCols.isNotEmpty()) Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Update a row that already has the same " + keyCols.joinToString(" + ") { byId[it]?.name ?: it } + " instead of adding a duplicate",
                        style = MaterialTheme.typography.bodySmall, color = KarColors.Ink, modifier = Modifier.weight(1f))
                    Switch(checked = update, onCheckedChange = { update = it }, colors = SwitchDefaults.colors(checkedTrackColor = KarColors.SkyDeep))
                }
                val newCount = mapping.count { it == NEW }
                Text("${preview.second.size} rows will be uploaded" + (if (newCount > 0) " · $newCount new column(s) will be added" else ""),
                    style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = KarColors.Navy, modifier = Modifier.padding(top = 8.dp))
                Text("Tip: use “Download template” to get a sheet with the tracker's columns.", style = MaterialTheme.typography.labelSmall, color = KarColors.SlateLight,
                    modifier = Modifier.padding(top = 2.dp))
            }
        },
        confirmButton = {
            TextButton(enabled = preview.second.isNotEmpty(), onClick = { val (c, d, k) = build(); onImport(c, d, replace, k) }) {
                Text("Upload ${preview.second.size} rows")
            }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onTemplate) { Text("Download template") }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
    )
}

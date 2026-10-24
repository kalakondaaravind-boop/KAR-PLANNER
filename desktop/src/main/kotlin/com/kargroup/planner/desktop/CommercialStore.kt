package com.kargroup.planner.desktop

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID
import kotlin.math.abs
import kotlin.math.roundToLong

/*
 * Commercials tracker (desktop): one table of all client commercials with user-defined columns.
 * Saved in commercials.json next to the data file.
 */

object ColType {
    const val TEXT = "TEXT"
    const val NUMBER = "NUMBER"
    const val CURRENCY = "CURRENCY"
    const val PERCENT = "PERCENT"
    const val DATE = "DATE"
    const val CHOICE = "CHOICE"
    const val YESNO = "YESNO"
    const val FORMULA = "FORMULA"
    val ALL = listOf(TEXT, NUMBER, CURRENCY, PERCENT, DATE, CHOICE, YESNO, FORMULA)
    fun label(t: String) = when (t) {
        NUMBER -> "Number"; CURRENCY -> "Amount (₹)"; PERCENT -> "Percent"; DATE -> "Date"
        CHOICE -> "Drop-down list"; YESNO -> "Yes / No"; FORMULA -> "Formula"; else -> "Text"
    }
    fun numeric(t: String) = t == NUMBER || t == CURRENCY || t == PERCENT
}

/** Formula: [a] op ([b] column or [constant]). op: MUL, ADD, SUB, DIV. Result shown in [format]. */
data class ColFormula(val a: String, val op: String = "MUL", val b: String? = null, val constant: Double? = null, val format: String = ColType.CURRENCY) {
    fun symbol() = when (op) { "ADD" -> "+"; "SUB" -> "−"; "DIV" -> "÷"; else -> "×" }
}

/** role ties a column to the summary cards: CLIENT, FTE, RATE, REVENUE, PROB, STATUS (or null). */
data class CommercialColumn(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val type: String = ColType.TEXT,
    val options: List<String> = emptyList(),
    val formula: ColFormula? = null,
    val width: Int = 130,
    val hidden: Boolean = false,
    val role: String? = null,
)

data class CommercialRow(
    val id: String = UUID.randomUUID().toString(),
    val values: Map<String, String> = emptyMap(),
    val order: Double = 0.0,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
)

class CommercialStore(private val file: File = File(AppDirs.root, "commercials.json")) {

    private val _columns = MutableStateFlow<List<CommercialColumn>>(emptyList())
    val columns: StateFlow<List<CommercialColumn>> = _columns
    private val _rows = MutableStateFlow<List<CommercialRow>>(emptyList())
    val rows: StateFlow<List<CommercialRow>> = _rows

    init { load() }

    private fun JSONObject.str(k: String): String? = if (has(k) && !isNull(k)) optString(k).ifBlank { null } else null

    private fun load() {
        val o = runCatching { JSONObject(file.readText(Charsets.UTF_8)) }.getOrNull()
        if (o == null) { _columns.value = defaultColumns(); return }
        val cols = o.optJSONArray("columns")
        _columns.value = if (cols == null) defaultColumns() else (0 until cols.length()).map { i ->
            val c = cols.getJSONObject(i)
            CommercialColumn(
                id = c.optString("id"), name = c.optString("name"), type = c.optString("type", ColType.TEXT),
                options = c.optJSONArray("options")?.let { a -> (0 until a.length()).map { a.optString(it) } } ?: emptyList(),
                formula = c.optJSONObject("formula")?.let { f ->
                    ColFormula(f.optString("a"), f.optString("op", "MUL"), f.str("b"),
                        if (f.has("constant") && !f.isNull("constant")) f.optDouble("constant") else null, f.optString("format", ColType.CURRENCY))
                },
                width = c.optInt("width", 130), hidden = c.optBoolean("hidden"), role = c.str("role"),
            )
        }
        val rs = o.optJSONArray("rows") ?: JSONArray()
        _rows.value = (0 until rs.length()).map { i ->
            val r = rs.getJSONObject(i)
            val v = r.optJSONObject("values") ?: JSONObject()
            CommercialRow(
                id = r.optString("id"), values = v.keySet().associateWith { v.optString(it) },
                order = r.optDouble("order", i.toDouble()), createdAt = r.optLong("createdAt"), updatedAt = r.optLong("updatedAt"),
            )
        }
    }

    private fun save() {
        val o = JSONObject()
        o.put("columns", JSONArray(_columns.value.map { c ->
            JSONObject().put("id", c.id).put("name", c.name).put("type", c.type).put("options", JSONArray(c.options))
                .put("width", c.width).put("hidden", c.hidden).put("role", c.role ?: JSONObject.NULL)
                .put("formula", c.formula?.let { f ->
                    JSONObject().put("a", f.a).put("op", f.op).put("b", f.b ?: JSONObject.NULL)
                        .put("constant", f.constant ?: JSONObject.NULL).put("format", f.format)
                } ?: JSONObject.NULL)
        }))
        o.put("rows", JSONArray(_rows.value.map { r ->
            JSONObject().put("id", r.id).put("order", r.order).put("createdAt", r.createdAt).put("updatedAt", r.updatedAt)
                .put("values", JSONObject(r.values))
        }))
        runCatching {
            file.parentFile?.mkdirs()
            val tmp = File(file.parentFile, file.name + ".tmp")
            tmp.writeText(o.toString(2), Charsets.UTF_8)
            if (!tmp.renameTo(file)) { file.delete(); tmp.renameTo(file) }
        }
    }

    // ---------------------------------------------------------------- rows
    fun addRow(values: Map<String, String> = emptyMap()): CommercialRow {
        val r = CommercialRow(values = values, order = (_rows.value.maxOfOrNull { it.order } ?: 0.0) + 1)
        _rows.value = _rows.value + r; save(); return r
    }

    fun setCell(rowId: String, colId: String, value: String) {
        _rows.value = _rows.value.map {
            if (it.id != rowId) it
            else it.copy(values = if (value.isBlank()) it.values - colId else it.values + (colId to value.trim()), updatedAt = System.currentTimeMillis())
        }
        save()
    }

    fun saveRow(row: CommercialRow) {
        val r = row.copy(values = row.values.filterValues { it.isNotBlank() }, updatedAt = System.currentTimeMillis())
        _rows.value = if (_rows.value.any { it.id == r.id }) _rows.value.map { if (it.id == r.id) r else it } else _rows.value + r
        save()
    }

    fun duplicate(rowId: String) {
        val src = _rows.value.firstOrNull { it.id == rowId } ?: return
        val i = _rows.value.indexOf(src)
        val next = _rows.value.getOrNull(i + 1)?.order ?: (src.order + 2)
        val copy = CommercialRow(values = src.values, order = (src.order + next) / 2)
        _rows.value = _rows.value + copy; save()
    }

    fun deleteRows(ids: Set<String>) { _rows.value = _rows.value.filter { it.id !in ids }; save() }

    fun setRows(rows: List<CommercialRow>) { _rows.value = rows; save() }

    // ---------------------------------------------------------------- columns
    fun saveColumn(col: CommercialColumn, afterId: String? = null) {
        val list = _columns.value.toMutableList()
        val i = list.indexOfFirst { it.id == col.id }
        if (i >= 0) list[i] = col
        else {
            val at = afterId?.let { a -> list.indexOfFirst { it.id == a } }?.takeIf { it >= 0 }?.plus(1) ?: list.size
            list.add(at, col)
        }
        _columns.value = list; save()
    }

    fun deleteColumn(id: String) {
        _columns.value = _columns.value.filter { it.id != id }.map { c ->
            if (c.formula != null && (c.formula.a == id || c.formula.b == id)) c.copy(type = ColType.NUMBER, formula = null) else c
        }
        _rows.value = _rows.value.map { it.copy(values = it.values - id) }
        save()
    }

    fun moveColumn(id: String, delta: Int) {
        val list = _columns.value.toMutableList()
        val i = list.indexOfFirst { it.id == id }
        val j = i + delta
        if (i < 0 || j !in list.indices) return
        list.add(j, list.removeAt(i)); _columns.value = list; save()
    }

    fun resetColumns() { _columns.value = defaultColumns(); save() }

    companion object {
        val STATUS_OPTIONS = listOf("Received", "Commercials shared", "Won", "Lost", "On Hold")

        fun defaultColumns(): List<CommercialColumn> {
            val fte = CommercialColumn(id = "fte", name = "FTE", type = ColType.NUMBER, width = 70, role = "FTE")
            val rate = CommercialColumn(id = "rate", name = "Rate (₹)", type = ColType.CURRENCY, width = 110, role = "RATE")
            return listOf(
                CommercialColumn(id = "client", name = "Client", type = ColType.TEXT, width = 170, role = "CLIENT"),
                CommercialColumn(id = "process", name = "Process", type = ColType.TEXT, width = 120),
                CommercialColumn(id = "location", name = "Location", type = ColType.TEXT, width = 120),
                fte, rate,
                CommercialColumn(id = "revenue", name = "Monthly Revenue (₹)", type = ColType.FORMULA, width = 150, role = "REVENUE",
                    formula = ColFormula(a = fte.id, op = "MUL", b = rate.id, format = ColType.CURRENCY)),
                CommercialColumn(id = "prob", name = "Prob.", type = ColType.PERCENT, width = 70, role = "PROB"),
                CommercialColumn(id = "consultant", name = "Consultant", type = ColType.YESNO, width = 95),
                CommercialColumn(id = "golive", name = "Go Live Date", type = ColType.DATE, width = 115),
                CommercialColumn(id = "status", name = "Status", type = ColType.CHOICE, width = 165, role = "STATUS", options = STATUS_OPTIONS),
            )
        }
    }
}

/** Values, formulas and formatting for the commercials table. */
object Commercials {
    private val inDate = listOf("d-M-yyyy", "d/M/yyyy", "d.M.yyyy", "d MMM yyyy", "d-MMM-yyyy", "d MMM yy", "d-MMM-yy", "d/M/yy", "MMM yyyy")
        .map { DateTimeFormatter.ofPattern(it, Locale.ENGLISH) }
    private val outDate = DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.ENGLISH)

    fun number(s: String?): Double? {
        if (s.isNullOrBlank()) return null
        val t = s.replace("₹", "").replace(",", "").replace("%", "").replace("Rs.", "", true).replace("Rs", "", true).trim()
        val mult = when {
            t.endsWith("cr", true) -> 1e7; t.endsWith("l", true) || t.endsWith("lakh", true) || t.endsWith("lac", true) -> 1e5
            t.endsWith("k", true) -> 1e3; else -> 1.0
        }
        val core = t.trimEnd { it.isLetter() }.trim()
        return core.toDoubleOrNull()?.times(mult)
    }

    /** Normalises what the user typed into what is stored. */
    fun normalise(col: CommercialColumn, raw: String): String {
        val s = raw.trim()
        if (s.isEmpty()) return ""
        return when (col.type) {
            ColType.NUMBER, ColType.CURRENCY, ColType.PERCENT -> number(s)?.let { plain(it) } ?: s.uppercase()
            ColType.DATE -> parseDate(s)?.toString() ?: s.uppercase()
            ColType.YESNO -> when (s.lowercase()) { "y", "yes", "true", "1" -> "Yes"; "n", "no", "false", "0" -> "No"; else -> s }
            ColType.CHOICE -> col.options.firstOrNull { it.equals(s, true) } ?: s
            else -> s.uppercase()
        }
    }

    fun parseDate(s: String?): LocalDate? {
        if (s.isNullOrBlank()) return null
        runCatching { return LocalDate.parse(s.trim()) }
        val t = s.trim().split(' ', '-', '/').joinToString(" ") { p -> p.lowercase().replaceFirstChar { it.titlecase() } }
        for (f in inDate) {
            runCatching { return LocalDate.parse(s.trim(), f) }
            runCatching { return LocalDate.parse(t, f) }
            runCatching { return java.time.YearMonth.parse(t, f).atDay(1) }
        }
        return null
    }

    private fun plain(d: Double): String = if (d % 1.0 == 0.0 && abs(d) < 1e15) d.toLong().toString() else "%.4f".format(Locale.ENGLISH, d).trimEnd('0').trimEnd('.')

    /** Numeric value of a cell (formula columns are calculated). */
    fun value(row: CommercialRow, col: CommercialColumn, byId: Map<String, CommercialColumn>, depth: Int = 0): Double? {
        if (col.type == ColType.FORMULA) {
            val f = col.formula ?: return null
            if (depth > 6) return null
            val a = byId[f.a]?.let { value(row, it, byId, depth + 1) } ?: return null
            val b = f.b?.let { id -> byId[id]?.let { value(row, it, byId, depth + 1) } } ?: f.constant ?: return null
            return when (f.op) {
                "ADD" -> a + b; "SUB" -> a - b; "DIV" -> if (b == 0.0) null else a / b
                else -> {
                    // percent inputs act as fractions inside formulas (e.g. Revenue × Prob.)
                    val pa = if (byId[f.a]?.type == ColType.PERCENT) a / 100 else a
                    val pb = if (f.b != null && byId[f.b]?.type == ColType.PERCENT) b / 100 else b
                    pa * pb
                }
            }
        }
        return number(row.values[col.id])
    }

    fun formatType(col: CommercialColumn) = if (col.type == ColType.FORMULA) col.formula?.format ?: ColType.NUMBER else col.type

    /** Text shown in a cell. */
    fun display(row: CommercialRow, col: CommercialColumn, byId: Map<String, CommercialColumn>): String {
        val raw = row.values[col.id]
        return when (col.type) {
            ColType.FORMULA -> value(row, col, byId)?.let { fmt(it, formatType(col)) } ?: ""
            ColType.NUMBER, ColType.CURRENCY, ColType.PERCENT -> number(raw)?.let { fmt(it, col.type) } ?: raw.orEmpty()
            ColType.DATE -> parseDate(raw)?.format(outDate) ?: raw.orEmpty()
            else -> raw.orEmpty()
        }
    }

    fun fmt(v: Double, type: String): String = when (type) {
        ColType.CURRENCY -> inr(v)
        ColType.PERCENT -> (if (v % 1.0 == 0.0) v.toLong().toString() else "%.1f".format(Locale.ENGLISH, v)) + "%"
        else -> if (v % 1.0 == 0.0) indian(v.toLong()) else indian(v.toLong()) + "." + "%.2f".format(Locale.ENGLISH, abs(v) % 1.0).substringAfter('.')
    }

    /** ₹3,50,000 (Indian digit grouping, no decimals unless needed). */
    fun inr(v: Double): String {
        val r = (v * 100).roundToLong()
        val whole = indian(r / 100)
        val paise = abs(r % 100)
        return (if (v < 0 && r / 100 == 0L) "-" else "") + "₹" + whole + (if (paise != 0L) ".%02d".format(paise) else "")
    }

    /** Short form for summary cards: ₹3.5 L, ₹1.25 Cr. */
    fun inrShort(v: Double): String = when {
        abs(v) >= 1e7 -> "₹" + "%.2f".format(Locale.ENGLISH, v / 1e7).trimEnd('0').trimEnd('.') + " Cr"
        abs(v) >= 1e5 -> "₹" + "%.2f".format(Locale.ENGLISH, v / 1e5).trimEnd('0').trimEnd('.') + " L"
        else -> inr(v)
    }

    fun indian(n: Long): String {
        val neg = n < 0
        val s = abs(n).toString()
        if (s.length <= 3) return (if (neg) "-" else "") + s
        val last3 = s.takeLast(3)
        var rest = s.dropLast(3)
        val parts = ArrayList<String>()
        while (rest.length > 2) { parts.add(0, rest.takeLast(2)); rest = rest.dropLast(2) }
        if (rest.isNotEmpty()) parts.add(0, rest)
        return (if (neg) "-" else "") + parts.joinToString(",") + "," + last3
    }

    /** Sort key for a column. */
    fun sortKey(row: CommercialRow, col: CommercialColumn, byId: Map<String, CommercialColumn>): Comparable<*>? = when {
        col.type == ColType.FORMULA || ColType.numeric(col.type) -> value(row, col, byId)
        col.type == ColType.DATE -> parseDate(row.values[col.id])?.toEpochDay()?.toDouble() ?: row.values[col.id]?.let { Double.MAX_VALUE }
        col.type == ColType.CHOICE -> col.options.indexOfFirst { it.equals(row.values[col.id], true) }.takeIf { it >= 0 }?.toDouble()
            ?: row.values[col.id]?.let { 999.0 }
        else -> row.values[col.id]?.lowercase()
    }

    fun csv(columns: List<CommercialColumn>, rows: List<CommercialRow>): String {
        fun c(x: String?) = "\"" + (x ?: "").replace("\"", "\"\"") + "\""
        val byId = columns.associateBy { it.id }
        val sb = StringBuilder("﻿")
        sb.append(columns.joinToString(",") { c(it.name.uppercase()) }).append('\n')
        rows.forEach { r ->
            sb.append(columns.joinToString(",") { col ->
                when {
                    col.type == ColType.FORMULA -> c(value(r, col, byId)?.let { plain(it) })
                    col.type == ColType.PERCENT -> c(number(r.values[col.id])?.let { plain(it) + "%" } ?: r.values[col.id])
                    ColType.numeric(col.type) -> c(number(r.values[col.id])?.let { plain(it) } ?: r.values[col.id])
                    col.type == ColType.DATE -> c(parseDate(r.values[col.id])?.format(outDate) ?: r.values[col.id])
                    else -> c(r.values[col.id])
                }
            }).append('\n')
        }
        return sb.toString()
    }
}

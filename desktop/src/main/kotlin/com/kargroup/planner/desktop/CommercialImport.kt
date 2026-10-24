package com.kargroup.planner.desktop

import org.w3c.dom.Element
import java.io.File
import java.time.LocalDate
import java.util.zip.ZipFile
import javax.xml.parsers.DocumentBuilderFactory

/** A sheet read from an uploaded file: header names and the data rows (same length as headers). */
data class UploadTable(val headers: List<String>, val rows: List<List<String>>, val source: String)

/** Reads .xlsx / .csv files for the commercials bulk upload. */
object CommercialImport {

    fun read(file: File): UploadTable {
        val raw = when (file.extension.lowercase()) {
            "xlsx", "xlsm" -> readXlsx(file)
            "csv", "txt" -> readCsv(file.readText(Charsets.UTF_8))
            else -> error("Please choose an Excel (.xlsx) or CSV file")
        }
        val trimmed = raw.map { r -> r.map { it.trim() } }.filter { r -> r.any { it.isNotEmpty() } }
        require(trimmed.isNotEmpty()) { "The file is empty" }
        // header = first row with at least two filled cells
        val hi = trimmed.indexOfFirst { r -> r.count { it.isNotEmpty() } >= 2 }.coerceAtLeast(0)
        val header = trimmed[hi]
        val width = trimmed.drop(hi).maxOf { it.size }
        val headers = (0 until width).map { header.getOrNull(it).orEmpty().ifBlank { "Column ${it + 1}" } }
        val rows = trimmed.drop(hi + 1).map { r -> (0 until width).map { r.getOrNull(it).orEmpty() } }
            .filter { r -> !r.firstOrNull().orEmpty().equals("total", true) }
        // drop columns that are completely empty and unnamed
        val keep = headers.indices.filter { i -> !headers[i].startsWith("Column ") || rows.any { it[i].isNotEmpty() } }
        return UploadTable(keep.map { headers[it] }, rows.map { r -> keep.map { r[it] } }, file.name)
    }

    // ------------------------------------------------------------------ CSV
    fun readCsv(text: String): List<List<String>> {
        val t = text.removePrefix("﻿")
        val firstLine = t.lineSequence().firstOrNull().orEmpty()
        val sep = listOf(',', ';', '\t').maxBy { c -> firstLine.count { it == c } }
        val out = ArrayList<List<String>>()
        var row = ArrayList<String>()
        val cell = StringBuilder()
        var quoted = false
        var i = 0
        while (i < t.length) {
            val ch = t[i]
            if (quoted) {
                if (ch == '"') {
                    if (i + 1 < t.length && t[i + 1] == '"') { cell.append('"'); i++ } else quoted = false
                } else cell.append(ch)
            } else when (ch) {
                '"' -> quoted = true
                sep -> { row.add(cell.toString()); cell.clear() }
                '\r' -> {}
                '\n' -> { row.add(cell.toString()); cell.clear(); out.add(row); row = ArrayList() }
                else -> cell.append(ch)
            }
            i++
        }
        if (cell.isNotEmpty() || row.isNotEmpty()) { row.add(cell.toString()); out.add(row) }
        return out
    }

    // ------------------------------------------------------------------ XLSX (first worksheet)
    private fun readXlsx(file: File): List<List<String>> = ZipFile(file).use { zip ->
        val dbf = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = false; isExpandEntityReferences = false }
        fun doc(name: String) = zip.getEntry(name)?.let { e -> zip.getInputStream(e).use { dbf.newDocumentBuilder().parse(it) } }
        fun Element.children(tag: String): List<Element> {
            val nl = getElementsByTagName(tag); return (0 until nl.length).map { nl.item(it) as Element }
        }
        fun Element.allText(tag: String) = children(tag).joinToString("") { it.textContent }

        val shared = doc("xl/sharedStrings.xml")?.documentElement?.children("si")?.map { it.allText("t") } ?: emptyList()

        // first sheet in workbook order
        val sheetPath = runCatching {
            val wb = doc("xl/workbook.xml")!!.documentElement
            val rid = wb.children("sheet").first().getAttribute("r:id")
            val rels = doc("xl/_rels/workbook.xml.rels")!!.documentElement.children("Relationship")
            val target = rels.first { it.getAttribute("Id") == rid }.getAttribute("Target")
            if (target.startsWith("/")) target.removePrefix("/") else "xl/" + target.removePrefix("./")
        }.getOrNull()?.takeIf { zip.getEntry(it) != null }
            ?: zip.entries().asSequence().map { it.name }.filter { it.startsWith("xl/worksheets/sheet") && it.endsWith(".xml") }.sorted().firstOrNull()
            ?: error("No worksheet found in the file")

        // number formats that are dates (so serial numbers become dates)
        val dateStyles = runCatching {
            val st = doc("xl/styles.xml")!!.documentElement
            val custom = st.children("numFmt").associate { it.getAttribute("numFmtId").toInt() to it.getAttribute("formatCode").lowercase() }
            val xfs = st.children("cellXfs").firstOrNull()?.children("xf") ?: emptyList()
            xfs.mapIndexedNotNull { i, xf ->
                val id = xf.getAttribute("numFmtId").toIntOrNull() ?: 0
                val code = custom[id]
                val isDate = id in 14..22 || id in 45..47 || (code != null && (code.contains("d") || code.contains("y")) && !code.contains("0.0") && !code.contains("#"))
                if (isDate) i else null
            }.toSet()
        }.getOrDefault(emptySet())

        val sheet = doc(sheetPath)!!.documentElement
        val out = ArrayList<List<String>>()
        sheet.children("row").forEach { r ->
            val cells = sortedMapOf<Int, String>()
            r.children("c").forEach { c ->
                val col = colIndex(c.getAttribute("r"))
                val t = c.getAttribute("t")
                val v = c.children("v").firstOrNull()?.textContent
                val text = when (t) {
                    "s" -> v?.toIntOrNull()?.let { shared.getOrNull(it) }
                    "inlineStr" -> c.allText("t")
                    "b" -> if (v == "1") "Yes" else "No"
                    "e" -> ""
                    "str" -> v
                    else -> {
                        val s = c.getAttribute("s").toIntOrNull()
                        val d = v?.toDoubleOrNull()
                        if (d != null && s != null && s in dateStyles && d > 0 && d < 2958466) LocalDate.of(1899, 12, 30).plusDays(d.toLong()).toString()
                        else v?.let { cleanNumber(it) }
                    }
                }.orEmpty()
                if (col >= 0) cells[col] = text
            }
            val width = (cells.keys.maxOrNull() ?: -1) + 1
            out.add((0 until width).map { cells[it].orEmpty() })
        }
        out
    }

    private fun colIndex(ref: String): Int {
        var n = 0
        for (ch in ref) { if (ch.isLetter()) n = n * 26 + (ch.uppercaseChar() - 'A' + 1) else break }
        return n - 1
    }

    /** 349999.99999999994 → 350000 (floating noise from Excel). */
    private fun cleanNumber(v: String): String {
        val d = v.toDoubleOrNull() ?: return v
        val r = Math.round(d * 1e6) / 1e6
        return if (r % 1.0 == 0.0 && kotlin.math.abs(r) < 1e15) r.toLong().toString() else r.toString()
    }

    // ------------------------------------------------------------------ matching and conversion
    private fun key(s: String) = s.lowercase().replace(Regex("\\(.*?\\)|₹|rs\\.?|inr|[^a-z0-9]"), "")

    private val synonyms = mapOf(
        "client" to listOf("client", "clientname", "customer", "company", "account", "name"),
        "fte" to listOf("fte", "ftes", "headcount", "hc", "seats"),
        "rate" to listOf("rate", "ratefte", "rateperfte", "billingrate", "price", "unitrate"),
        "prob" to listOf("prob", "probability", "winprobability", "chance"),
        "golive" to listOf("golivedate", "golive", "livedate", "startdate"),
        "status" to listOf("status", "stage", "dealstatus"),
        "revenue" to listOf("monthlyrevenue", "revenue", "mrr", "monthlyvalue"),
    )

    /** For each uploaded header: the matching tracker column id, or null. */
    fun autoMatch(headers: List<String>, columns: List<CommercialColumn>): List<String?> {
        val used = HashSet<String>()
        return headers.map { h ->
            val k = key(h)
            val hit = columns.firstOrNull { it.id !in used && key(it.name) == k }
                ?: columns.firstOrNull { c -> c.id !in used && (synonyms[c.role?.lowercase()] ?: synonyms[c.id]).orEmpty().contains(k) }
            hit?.id?.also { used.add(it) }
        }
    }

    /** Converts one uploaded value for the target column (percent fractions, Excel dates, Yes/No ...). */
    fun convert(col: CommercialColumn, raw: String): String {
        val s = raw.trim()
        if (s.isEmpty()) return ""
        if (col.type == ColType.PERCENT && !s.contains('%')) {
            val d = s.toDoubleOrNull()
            if (d != null && d > 0 && d <= 1.0 && s.contains('.')) return Commercials.normalise(col, (d * 100).toString())
        }
        if (col.type == ColType.DATE) {
            val d = s.toDoubleOrNull()
            if (d != null && d > 20000 && d < 80000) return LocalDate.of(1899, 12, 30).plusDays(d.toLong()).toString()
        }
        if (col.type == ColType.CHOICE) {
            col.options.firstOrNull { key(it) == key(s) }?.let { return it }
        }
        return Commercials.normalise(col, s)
    }

    /** CSV template with the tracker's own columns and one example row. */
    fun template(columns: List<CommercialColumn>): String {
        fun c(x: String) = "\"" + x.replace("\"", "\"\"") + "\""
        val cols = columns.filter { it.type != ColType.FORMULA }
        val example = mapOf("client" to "Cholamandalam", "process" to "FTE", "location" to "Mumbai-A8", "fte" to "10", "rate" to "35000",
            "prob" to "10%", "consultant" to "No", "golive" to "TBD", "status" to "On Hold")
        return "﻿" + cols.joinToString(",") { c(it.name) } + "\n" + cols.joinToString(",") { c(example[it.id].orEmpty()) } + "\n"
    }
}

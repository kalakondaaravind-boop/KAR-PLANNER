package com.kargroup.planner.desktop

import com.kargroup.planner.ui.Report
import com.kargroup.planner.util.Ist

/** Printable HTML version of the Reports page (the browser's Print → Save as PDF gives a PDF). */
object ReportHtml {
    private fun e(s: String?) = (s ?: "").uppercase().replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")

    fun render(r: Report): String = buildString {
        append("<!doctype html><html><head><meta charset=\"utf-8\"><title>KAR PLANNER REPORT</title><style>")
        append("body{font-family:'Segoe UI',Arial,sans-serif;color:#0F172A;margin:32px}h1{color:#0284C7;margin:0}")
        append(".sub{color:#64748B;font-size:12px;margin-bottom:20px}.k{display:inline-block;border:1px solid #D6E8F7;border-radius:10px;padding:10px 16px;margin:0 8px 8px 0;min-width:120px}")
        append(".k b{display:block;font-size:22px;color:#0B1E3F}.k span{font-size:11px;color:#64748B}h2{font-size:14px;color:#0B1E3F;margin:24px 0 8px}")
        append("table{border-collapse:collapse;width:100%;font-size:12px}th{background:#E0F2FE;text-align:left}th,td{border:1px solid #D6E8F7;padding:6px 8px}.r{color:#DC2626;font-weight:bold}")
        append("@media print{body{margin:12mm}}</style></head><body>")
        append("<h1>KAR GROUP · PLANNER REPORT</h1><div class=\"sub\">${e(Ist.formatFull(r.from.toString()))} – ${e(Ist.formatFull(r.to.toString()))} · GENERATED ${e(Ist.formatStamp(System.currentTimeMillis()))} IST</div>")
        fun k(label: String, v: String) = append("<div class=\"k\"><b>${e(v)}</b><span>${e(label)}</span></div>")
        k("Completed", "${r.completed}")
        k("On time", r.onTimePct?.let { "$it%" } ?: "–")
        k("Overdue now", "${r.overdueNow}")
        k("Open now", "${r.openNow}")
        k("Avg. days to close", r.avgDaysToComplete?.let { "%.1f".format(it) } ?: "–")
        append("<h2>BY PERSON</h2><table><tr><th>PERSON</th><th>DONE</th><th>ON TIME</th><th>LATE</th><th>OPEN</th><th>OVERDUE</th></tr>")
        r.people.forEach { p -> append("<tr><td>${e(p.name)}</td><td>${p.completed}</td><td>${p.onTime}</td><td>${p.late}</td><td>${p.open}</td><td class=\"${if (p.overdue > 0) "r" else ""}\">${p.overdue}</td></tr>") }
        append("</table>")
        if (r.projects.isNotEmpty()) {
            append("<h2>BY PROJECT</h2><table><tr><th>PROJECT</th><th>DONE</th><th>OPEN</th><th>OVERDUE</th></tr>")
            r.projects.forEach { p -> append("<tr><td>${e(p.name)}</td><td>${p.completed}</td><td>${p.open}</td><td>${p.overdue}</td></tr>") }
            append("</table>")
        }
        if (r.overdueList.isNotEmpty()) {
            append("<h2>OVERDUE ACTIVITIES</h2><table><tr><th>ACTIVITY</th><th>OWNER</th><th>TARGET</th></tr>")
            r.overdueList.forEach { t -> append("<tr><td>${e(t.text)}</td><td>${e(t.assignee ?: "Unassigned")}</td><td class=\"r\">${e(t.dueDate)} ${e(t.dueTime)}</td></tr>") }
            append("</table>")
        }
        if (r.completedList.isNotEmpty()) {
            append("<h2>COMPLETED IN PERIOD</h2><table><tr><th>ACTIVITY</th><th>OWNER</th><th>COMPLETED</th><th>REMARKS</th></tr>")
            r.completedList.take(300).forEach { t -> append("<tr><td>${e(t.text)}</td><td>${e(t.assignee ?: "Unassigned")}</td><td>${e(t.completedAt?.let { Ist.formatStamp(it) })}</td><td>${e(t.completionRemarks)}</td></tr>") }
            append("</table>")
        }
        append("</body></html>")
    }
}

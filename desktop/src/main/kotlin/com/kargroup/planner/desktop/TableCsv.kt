package com.kargroup.planner.desktop

import com.kargroup.planner.data.ProjectEntity
import com.kargroup.planner.data.TaskEntity
import com.kargroup.planner.data.TaskStatus
import com.kargroup.planner.data.effectiveStatus
import com.kargroup.planner.util.Ist

/** CSV (opens in Excel) of exactly the activities shown in the Table view. */
object TableCsv {
    private fun c(s: String?) = "\"" + (s ?: "").replace("\"", "\"\"") + "\""

    fun csv(list: List<TaskEntity>, projects: List<ProjectEntity>, tagsOf: (String) -> Set<String>): String {
        val p = projects.associate { it.id to it.name }
        val sb = StringBuilder("﻿")
        sb.append("ACTIVITY,PROJECT,OWNER,PRIORITY,TARGET DATE,TARGET TIME,STATUS,CURRENT STATUS,NOTES,ESTIMATE (MIN),TAGS,COMPLETED AT,REMARKS\n")
        list.forEach { t ->
            sb.append(
                listOf(
                    t.text, t.projectId?.let { p[it] }, t.assignee, t.priority?.replace('_', ' ')?.uppercase() ?: "STANDARD",
                    t.dueDate, t.dueTime, TaskStatus.label(t.effectiveStatus).uppercase(), t.statusNote, t.notes,
                    t.estimatedMinutes?.toString(), tagsOf(t.id).joinToString(" "),
                    t.completedAt?.let { Ist.formatStamp(it) }, t.completionRemarks,
                ).joinToString(",") { c(it) }
            ).append('\n')
        }
        return sb.toString()
    }
}

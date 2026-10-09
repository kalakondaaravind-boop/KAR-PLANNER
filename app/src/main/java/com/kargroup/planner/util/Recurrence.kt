package com.kargroup.planner.util

import com.kargroup.planner.data.Frequency
import com.kargroup.planner.data.TaskEntity
import java.time.DayOfWeek
import java.time.LocalDate

object Recurrence {

    private val dayShort = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")

    /**
     * Next target date (IST) for a recurring task that has just been completed.
     * Starts from the current due date (or today) and keeps stepping until the
     * result is today or later, so an overdue daily task lands on today.
     */
    fun nextDueDate(task: TaskEntity, today: LocalDate = Ist.today()): LocalDate {
        val base = Ist.parse(task.dueDate) ?: today
        val freq = task.recurrenceFrequency ?: Frequency.DAILY
        val interval = task.recurrenceInterval.coerceAtLeast(1)
        val days = task.recurrenceDaysOfWeek.orEmpty().filter { it in 1..7 }.toSet()
        var next = step(base, freq, interval, days)
        var guard = 0
        while (next.isBefore(today) && guard < 2000) {
            next = step(next, freq, interval, days)
            guard++
        }
        return next
    }

    private fun step(base: LocalDate, freq: String, interval: Int, days: Set<Int>): LocalDate = when (freq) {
        Frequency.WEEKLY -> weekly(base, interval, days)
        Frequency.BIWEEKLY -> weekly(base, interval * 2, days)
        Frequency.MONTHLY -> base.plusMonths(interval.toLong())
        else -> base.plusDays(interval.toLong())
    }

    private fun weekly(base: LocalDate, intervalWeeks: Int, days: Set<Int>): LocalDate {
        if (days.isEmpty()) return base.plusWeeks(intervalWeeks.toLong())
        var candidate = base.plusDays(1)
        while (candidate.dayOfWeek.value !in days) candidate = candidate.plusDays(1)
        val baseWeek = base.with(DayOfWeek.MONDAY)
        val candidateWeek = candidate.with(DayOfWeek.MONDAY)
        if (candidateWeek != baseWeek && intervalWeeks > 1) {
            candidate = candidate.plusWeeks((intervalWeeks - 1).toLong())
        }
        return candidate
    }

    fun describe(task: TaskEntity): String {
        if (!task.isRecurring) return ""
        val n = task.recurrenceInterval.coerceAtLeast(1)
        val days = task.recurrenceDaysOfWeek.orEmpty().filter { it in 1..7 }.sorted()
        val dayText = when {
            days.isEmpty() -> ""
            days == listOf(1, 2, 3, 4, 5) -> " · Mon–Fri"
            days.size == 7 -> " · every day"
            else -> " · " + days.joinToString(", ") { dayShort[it - 1] }
        }
        return when (task.recurrenceFrequency) {
            Frequency.WEEKLY -> (if (n == 1) "Weekly" else "Every $n weeks") + dayText
            Frequency.BIWEEKLY -> (if (n == 1) "Biweekly" else "Every ${n * 2} weeks") + dayText
            Frequency.MONTHLY -> if (n == 1) "Monthly" else "Every $n months"
            else -> if (n == 1) "Daily" else "Every $n days"
        }
    }
}

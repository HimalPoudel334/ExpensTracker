package com.roomies.expensetracker.util

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

object DateUtils {

    fun formatDate(millis: Long): String {
        val sdf = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
        return sdf.format(Date(millis))
    }

    fun formatMonthYear(millis: Long): String {
        val sdf = SimpleDateFormat("MMMM yyyy", Locale.getDefault())
        return sdf.format(Date(millis))
    }

    fun monthYearKey(millis: Long): String {
        val cal = Calendar.getInstance().apply { timeInMillis = millis }
        return "${cal.get(Calendar.YEAR)}-${cal.get(Calendar.MONTH)}"
    }

    fun currentMonthYearKey(): String = monthYearKey(System.currentTimeMillis())

    fun isSameMonth(millis: Long, refMillis: Long): Boolean =
        monthYearKey(millis) == monthYearKey(refMillis)

    fun addMonths(millis: Long, months: Int): Long {
        val cal = Calendar.getInstance().apply { timeInMillis = millis }
        cal.add(Calendar.MONTH, months)
        return cal.timeInMillis
    }

    fun millisForDayInMonth(refMillis: Long, day: Int): Long {
        val cal = Calendar.getInstance().apply { timeInMillis = refMillis }
        val maxDay = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
        cal.set(Calendar.DAY_OF_MONTH, day.coerceAtMost(maxDay))
        cal.set(Calendar.HOUR_OF_DAY, 12)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        return cal.timeInMillis
    }
}
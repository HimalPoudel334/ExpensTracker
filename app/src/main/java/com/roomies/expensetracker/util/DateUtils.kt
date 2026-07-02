package com.roomies.expensetracker.util

import dev.shivathapaa.nepalidatepickerkmp.calendar_model.NepaliDateConverter
import dev.shivathapaa.nepalidatepickerkmp.calendar_model.NepaliDatePickerDefaults
import dev.shivathapaa.nepalidatepickerkmp.data.SimpleDate
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

    /** Today's date stored at local noon (our convention, avoids midnight edge cases). */
    fun todayLocalNoon(): Long {
        val now = Calendar.getInstance()
        return toLocalNoon(now.get(Calendar.YEAR), now.get(Calendar.MONTH), now.get(Calendar.DAY_OF_MONTH))
    }

    fun toLocalNoon(year: Int, month: Int, day: Int): Long {
        val cal = Calendar.getInstance()
        cal.clear()
        cal.set(year, month, day, 12, 0, 0)
        return cal.timeInMillis
    }

    /** Our local-noon AD millis -> the library's BS SimpleDate, for initializing the picker. */
    fun toNepaliSimpleDate(localMillis: Long): SimpleDate {
        val cal = Calendar.getInstance().apply { timeInMillis = localMillis }
        val nepali = NepaliDateConverter.convertEnglishToNepali(
            cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1, cal.get(Calendar.DAY_OF_MONTH)
        )
        return SimpleDate(nepali.year, nepali.month, nepali.dayOfMonth)
    }

    /** BS year/month(1-12)/day -> our local-noon AD millis storage convention. */
    fun fromEnglishCustomCalendar(year: Int, month: Int, day: Int): Long =
        toLocalNoon(year, month - 1, day)

    /** Formats our local-noon AD millis as a BS date string, e.g. "Asar 6, 2083". */
    fun formatNepali(localMillis: Long): String {
        val cal = Calendar.getInstance().apply { timeInMillis = localMillis }
        val nepali = NepaliDateConverter.convertEnglishToNepali(
            cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1, cal.get(Calendar.DAY_OF_MONTH)
        )
        return NepaliDateConverter.formatNepaliDate(nepali, NepaliDatePickerDefaults.DefaultLocale)
    }

    /** "2083-3" style key based on BS month — used for BS-aware report filtering. */
    fun bsMonthYearKey(localMillis: Long): String {
        val cal = Calendar.getInstance().apply { timeInMillis = localMillis }
        val nepali = NepaliDateConverter.convertEnglishToNepali(
            cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1, cal.get(Calendar.DAY_OF_MONTH)
        )
        return "${nepali.year}-${nepali.month}"
    }

    fun isSameBsMonth(millis: Long, refMillis: Long): Boolean =
        bsMonthYearKey(millis) == bsMonthYearKey(refMillis)

    /** Navigate by BS months so Ashar stays Ashar regardless of AD month boundaries. */
    fun addBsMonths(localMillis: Long, months: Int): Long {
        val cal = Calendar.getInstance().apply { timeInMillis = localMillis }
        val nepali = NepaliDateConverter.convertEnglishToNepali(
            cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1, cal.get(Calendar.DAY_OF_MONTH)
        )
        var year = nepali.year
        var month = nepali.month + months
        while (month > 12) { month -= 12; year++ }
        while (month < 1) { month += 12; year-- }
        val english = NepaliDateConverter.convertNepaliToEnglish(year, month, 1)
        return toLocalNoon(english.year, english.month - 1, english.dayOfMonth)
    }

    private val BS_MONTH_NAMES = listOf(
        "Baisakh", "Jestha", "Asar", "Shrawan", "Bhadra", "Aswin",
        "Kartik", "Mangsir", "Poush", "Magh", "Falgun", "Chaitra"
    )

    /** Formats as "Asar 2083" (BS month + year only) for report headers. */
    fun formatNepaliMonthYear(localMillis: Long): String {
        val cal = Calendar.getInstance().apply { timeInMillis = localMillis }
        val nepali = NepaliDateConverter.convertEnglishToNepali(
            cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1, cal.get(Calendar.DAY_OF_MONTH)
        )
        val monthName = BS_MONTH_NAMES.getOrElse(nepali.month - 1) { "Month ${nepali.month}" }
        return "$monthName ${nepali.year}"
    }
}


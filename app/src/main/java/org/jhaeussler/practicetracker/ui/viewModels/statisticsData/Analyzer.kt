/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 J. Häußler
 */

package org.jhaeussler.practicetracker.ui.viewModels.statisticsData

import org.jhaeussler.practicetracker.datastorage.PracticeTime
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoField
import java.time.temporal.ChronoUnit
import java.time.temporal.WeekFields
import java.util.Locale
import kotlin.collections.forEach
import kotlin.math.floor


class Analyzer(
    private val dbEntries : List<PracticeTime>,
    private var currentDate: LocalDate,
    private var totalHoursForEachWeek : Map<WeekOfYear, Double>? = null
) {
    init {
        totalHoursForEachWeek = getAllWeeksAsMap()
    }

    // Helpers and stuff

    // returns Map(WeekOfYear, duration)
    private fun getAllWeeksAsMap() : Map<WeekOfYear, Double> {
        val mapOfWeeks = mutableMapOf<WeekOfYear, Double>()
        dbEntries.forEach {
            val weekOfYear = WeekOfYear.fromLocalDate(it.date)
            if(mapOfWeeks.containsKey(weekOfYear))
                mapOfWeeks[weekOfYear] = mapOfWeeks[weekOfYear]!! + it.value
            else
                mapOfWeeks[weekOfYear] = it.value
        }
        return mapOfWeeks.toMap()
    }

    private fun roundToSecondDigit(input: Double) : Double {
        return floor(input * 100) / 100
    }

    private fun getCountOfCurrentWeekDay() : Int {
        return DayOfWeek.from(currentDate).get(ChronoField.DAY_OF_WEEK)
    }

    private fun getCurrentWeekOfYear() : WeekOfYear {
        return WeekOfYear.fromLocalDate(currentDate)
    }

    private fun getEntriesInWeekOfYear(weekOfYear: WeekOfYear) : List<PracticeTime> {
        return dbEntries.filter { entry ->
            WeekOfYear.fromLocalDate(entry.date) == weekOfYear
        }
    }

    private fun getHoursPlayedInWeek(weekOfYear: WeekOfYear) : Double {
        return getEntriesInWeekOfYear(weekOfYear).sumOf { it.value }
    }

    private fun getHoursPlayedInTimeSpan(startDate: LocalDate, endDate: LocalDate) : Double {
        if(dbEntries.isEmpty())
            return 0.0

        val selectedDays : List<PracticeTime> =
            dbEntries.filter { it.date in startDate..endDate }

        if (selectedDays.isEmpty())
            return 0.0

        return selectedDays.sumOf { it.value }
    }

    private fun getDaysBetween(startDate: LocalDate, endDate: LocalDate) : Long {
        return startDate.until(endDate, ChronoUnit.DAYS)
    }

    private fun getAverageHoursPerDayInTimeSpan(startDate: LocalDate, endDate: LocalDate) : Double {
        if(dbEntries.isEmpty())
            return 0.0

        val daysBetween = getDaysBetween(startDate, endDate)

        if(daysBetween == 0L && dbEntries.isNotEmpty())
            return dbEntries.find { it.date == startDate }?.value ?: 0.0

        return roundToSecondDigit(
            getHoursPlayedInTimeSpan(startDate, endDate) / daysBetween
        )
    }

    private fun getAverageHoursPerPlayDayInTimeSpan(startDate: LocalDate, endDate: LocalDate) : Double {
        if(dbEntries.isEmpty())
            return 0.0

        val selectedDays : List<PracticeTime> =
            dbEntries.filter { it.date in startDate..endDate }

        if(selectedDays.isEmpty())
            return 0.0

        return roundToSecondDigit(
            getHoursPlayedInTimeSpan(startDate, endDate) / selectedDays.size
        )
    }

    private fun getFirstDate() : LocalDate {
        return dbEntries.minByOrNull { it.date }?.date ?: LocalDate.now()
    }

    // General Data generation

    fun getAllHoursPlayed() : Double {
        return getHoursPlayedInTimeSpan(
            dbEntries.minByOrNull { it.date }?.date ?: currentDate, currentDate)
    }

    fun getAverageHoursThisWeek() : Double {
        if(dbEntries.isEmpty())
            return 0.0

        val dayOfWeek = getCountOfCurrentWeekDay()

        if(dayOfWeek <= 1)
            return getHoursPlayedThisWeek()

        return roundToSecondDigit(getHoursPlayedThisWeek() / dayOfWeek)
    }

    fun getFirstDateFormatted() : String {
        return getFirstDate().format(DateTimeFormatter
            .ofPattern("dd.MM.yyyy"))
    }

    // All time data Generation

    fun getAverageHoursPerDay() : Double {
        return getAverageHoursPerDayInTimeSpan(
            dbEntries.minByOrNull { it.date }?.date ?: currentDate, currentDate)
    }

    fun getAverageHoursOnPlayDates() : Double {
        return getAverageHoursPerPlayDayInTimeSpan(
            dbEntries.minByOrNull { it.date }?.date ?: currentDate, currentDate)
    }

    fun getBestDays() : String {
        val firstBestEntry = dbEntries.maxByOrNull { it.value }
        val bestEntries = dbEntries.filter { it.value == firstBestEntry?.value }
        return if (firstBestEntry == null) "None"
        else "${firstBestEntry.value} h\n${bestEntries.count()} day(s)"
    }

    // Current Week Data Generation

    fun getAverageHoursOnPlayDatesThisWeek() : Double {
        if(dbEntries.isEmpty())
            return 0.0

        val entriesThisWeek = getEntriesInWeekOfYear(getCurrentWeekOfYear())

        return roundToSecondDigit(entriesThisWeek.sumOf { it.value } / entriesThisWeek.size)
    }

    fun getBestDayThisWeek() : String {
        val entriesThisWeek = getEntriesInWeekOfYear(getCurrentWeekOfYear())
        val firstBest = entriesThisWeek.maxByOrNull { it.value }
        val count = entriesThisWeek.count { it.value == firstBest?.value }

        return if(firstBest == null) "None"
        else "${firstBest.value} h " +
                "(${firstBest.date.format(DateTimeFormatter.ofPattern("dd.MM."))})\n" +
                "$count day(s)"
    }

    fun getHoursPlayedThisWeek() : Double {
        return getHoursPlayedInWeek(
            getCurrentWeekOfYear()
        )
    }

    fun getNumOfDaysPlayedThisWeek() : String {
        val entriesThisWeek = getEntriesInWeekOfYear(getCurrentWeekOfYear())
        return "${entriesThisWeek.size}/${currentDate.dayOfWeek.value}"
    }

    // All time weekly data generation

    fun getBestWeeks(): String {
        val maxim = totalHoursForEachWeek?.entries?.maxByOrNull { it.value }?.value
        val bestWeeks = totalHoursForEachWeek?.filter { it.value == maxim }

        return "${bestWeeks?.values?.maxOrNull() ?: ""} h\n" +
                "${bestWeeks?.keys?.count()} week(s)"
    }

    fun getAverageHoursPerWeek() : Double {
        if(totalHoursForEachWeek?.isEmpty() != false)
            return 0.0

        return roundToSecondDigit(
            totalHoursForEachWeek!!.values.sum() / totalHoursForEachWeek!!.values.size
        )
    }

    // Additional infos

    fun getDaysPassedSinceFirstEntry() : Long {
        return getDaysBetween(getFirstDate(), LocalDate.now())
    }

    fun getDaysPlayedTotal() : Int {
        return dbEntries.size
    }

    fun getDaysPlayedInPercent() : Int {
        return (roundToSecondDigit(
            getDaysPlayedTotal().toDouble() / getDaysPassedSinceFirstEntry()
        ) * 100).toInt()
    }

    fun getLongestStreak() : Int {
        var currentStreak = 0
        var longestStreak = 0
        var lastDate : LocalDate? = null

        if(dbEntries.size == 1)
            return 1

        dbEntries.forEach {
            if(lastDate == null) {
                currentStreak ++
            } else {
                if (it.date == lastDate.plusDays(1))
                    currentStreak ++
                else {
                    if(longestStreak  < currentStreak) longestStreak = currentStreak
                    currentStreak = 1
                }

            }
            lastDate = it.date
        }

        return longestStreak
    }

    fun getLongestAbsence() : Int {
        var maxAbsence = 0
        var lastDate: LocalDate? = null

        if(dbEntries.size == 1)
            return dbEntries.first().date.until(currentDate, ChronoUnit.DAYS).toInt()

        dbEntries.forEach {
            if (lastDate != null){
                if(it.date != lastDate.plusDays(1)) {
                    val absence = ChronoUnit.DAYS.between(lastDate, it.date) - 1
                    if(maxAbsence < absence)
                        maxAbsence = absence.toInt()
                }
            }
            lastDate = it.date
        }
        return maxAbsence
    }

    fun getPracticeHoursToday() : Double {
        return dbEntries.find { it.date == currentDate }?.value ?: 0.0
    }
}

data class WeekOfYear(
    val kw: Int,
    val year: Int
) {
    companion object {
        fun fromLocalDate(date : LocalDate) : WeekOfYear {
            return WeekOfYear(date.get(WeekFields.of(Locale.GERMAN).weekOfYear()), date.year)
        }
    }
    fun asString() : String {
        return "KW $kw of $year"
    }
}
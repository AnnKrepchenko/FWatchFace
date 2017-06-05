package com.krepchenko.fwatchface.utils

/**
 * Created by ann on 6/5/17.
 */
object TimeToTextUtil {
    val begin = "It's a fucking"


    fun getFirstLine(): String {
        return begin
    }

    fun getSecondLine(min:Int): String {
        var minute = min
        if (minute != 15 && minute != 45 && minute != 30) {
            minute = if (minute > 30) 30 - minute.rem(30) else minute
            return getMinutes(minute) + if (minute !in 21..29) " "+getMinutesText(minute) else ""
        } else if (minute == 30) {
            return "half"
        } else {
            return "quarter"
        }

    }

    fun getThirdLine(minute: Int): String {
        return getMinutesText(minute)
    }

    fun getFourthLine(minute: Int, hour: Int): String {
        var hourText = ""
        var hours = hour
        if (minute > 30) {
            hours = (hours + 1).rem(12)
            hourText += "to "
        } else if (minute in 0..30) {
            hours = hour.rem(12)
            if (minute>0) hourText += "past "
        }
        hourText += getHours(hours)
        if (minute == 30 || minute == 0)
            hourText += " o'clock"
        return hourText

    }

    fun getMinutes(minutes: Int): String {
        return NumberWordConverter.convert(minutes)
    }

    private fun getMinutesText(minutes: Int): String {
        return if (minutes.rem(10) == 1) "minute" else "minutes"
    }

    private fun getHours(hours: Int): String {
        var time = ""
        when (hours) {
            0, 12 -> time += "twelve"
            1 -> time += "one"
            2 -> time += "two"
            3 -> time += "tree"
            4 -> time += "four"
            5 -> time += "five"
            6 -> time += "six"
            7 -> time += "seven"
            8 -> time += "eight"
            9 -> time += "nine"
            10 -> time += "ten"
            11 -> time += "eleven"
        }
        return time
    }

    fun convertTo12Hour(hour: Int): Int {
        val result = hour.rem(12)
        return if (result == 0) 12 else result
    }
}
package com.krepchenko.fwatchface.utils

import java.util.Random

object NumberWordConverter {
    val units = arrayOf("", "one", "two", "three", "four", "five", "six", "seven", "eight", "nine", "ten", "eleven", "twelve", "thirteen", "fourteen", "fifteen", "sixteen", "seventeen", "eighteen", "nineteen")

    val tens = arrayOf("", // 0
            "", // 1
            "twenty", // 2
            "thirty", // 3
            "forty", // 4
            "fifty", // 5
            "sixty", // 6
            "seventy", // 7
            "eighty", // 8
            "ninety"   // 9
    )

    fun convert(n: Int): String {
        if (n < 0) {
            return "minus " + convert(-n)
        }

        if (n < 20) {
            return units[n]
        }

        if (n < 100) {
            return tens[n / 10] + (if (n % 10 != 0) " " else "") + units[n % 10]
        }

        if (n < 1000) {
            return units[n / 100] + " hundred" + (if (n % 100 != 0) " " else "") + convert(n % 100)
        }

        if (n < 1000000) {
            return convert(n / 1000) + " thousand" + (if (n % 1000 != 0) " " else "") + convert(n % 1000)
        }

        if (n < 1000000000) {
            return convert(n / 1000000) + " million" + (if (n % 1000000 != 0) " " else "") + convert(n % 1000000)
        }

        return convert(n / 1000000000) + " billion" + (if (n % 1000000000 != 0) " " else "") + convert(n % 1000000000)
    }

}
package masters.utils

import kotlin.math.ceil

/**
 * Created by Jacob Stringer on 3/12/2019.
 */

fun descriptiveStatsHeader() : String {
    return "0%,10%,25%,50%,75%,90%,95%,99%,100%"
}

// Expects sorted list
fun descriptiveStats(numbers: MutableList<Long>?) : String {
    if (numbers == null || numbers.size == 0) return ""

    val tenth = getIndex(numbers.size, 10)
    val twentyfifth = getIndex(numbers.size, 25)
    val median = getIndex(numbers.size, 50)
    val seventyfifth = getIndex(numbers.size, 75)
    val ninetieth = getIndex(numbers.size, 90)
    val ninetyfifth = getIndex(numbers.size, 95)
    val ninetyninth = getIndex(numbers.size, 99)

    return "${numbers[0]},${numbers[tenth]},${numbers[twentyfifth]},${numbers[median]},${numbers[seventyfifth]},${numbers[ninetieth]},${numbers[ninetyfifth]},${numbers[ninetyninth]},${numbers.last()}"
}

// percentile in range [1,100]
private fun getIndex(size: Int, percentile: Int) : Int {
    return ceil(percentile.toDouble() / 100 * size).toInt() - 1
}
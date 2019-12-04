package masters.utils

import masters.ContainsTime
import java.text.SimpleDateFormat
import kotlin.math.abs

/**
 * Created by Jacob Stringer on 3/12/2019.
 */

val dateFormat = SimpleDateFormat("yyyy-MM-dd hh:mm:ss zzz")
val millisInDay = 3_600_000 * 24

fun getLongFromString(time: String) : Long {
    return dateFormat.parse(time).time
}

fun isUnderOneDayDiff(first: String, second: String) : Boolean {
    return abs(getLongFromString(first) - getLongFromString(second)) / millisInDay < 1
}

fun getDaysLag(currentVersion: ContainsTime, firstNewerDepVersion: ContainsTime?) : Long {
    firstNewerDepVersion ?: return 0

    try {
        return (getLongFromString(currentVersion.time) - getLongFromString(firstNewerDepVersion.time)) / millisInDay
    } catch (e: Exception) {
        e.printStackTrace()
    }

    return -1
}
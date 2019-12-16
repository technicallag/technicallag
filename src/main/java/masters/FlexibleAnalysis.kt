package masters

import masters.flexilag.MatcherResult
import masters.libiostudy.Classifications
import java.io.File
import java.util.*

/**
 * Created by Jacob Stringer on 16/12/2019.
 */

fun analyseAll() {
    val results: Array<Vector<Array<Int>>> = Array(PairCollector.PackageManager.values().size) { Vector<Array<Int>>() }
    for (pm in PairCollector.PackageManager.values()) {
        val lag = FlexibleAnalysisByPM(pm)
        val result = lag.getLag()
        results[pm.ordinal] = result
    }

    File("data/flexible_lag.csv").bufferedWriter().use { out ->
        out.write(",")
        PairCollector.PackageManager.values().forEach {
            out.write(",$it")
        }
        out.write("\n")

        Classifications.ALL.forEach { classification ->
            MatcherResult.values().forEach { match ->
                out.write("\n$classification,$match")
                PairCollector.PackageManager.values().forEach { pm ->
                    out.write(",${results[pm.ordinal][Classifications.ALL.indexOf(classification)][match.ordinal]}")
                }
            }
        }
    }
}
package masters

import com.sun.org.apache.xerces.internal.impl.xpath.regex.Match
import masters.flexilag.LagCheckingService
import masters.flexilag.MatcherResult
import masters.libiostudy.Classifications
import masters.utils.Logging
import java.io.*
import java.util.*

/**
 * Created by Jacob Stringer on 16/12/2019.
 */

const val results_bin_path = "../flexible_study/flexible_lag.bin"
const val results_xml_path = "../flexible_study/flexible_lag.xml"
val log = Logging.getLogger("")
val mapClassifications = mapOf(
        "fixed" to 0,
        "soft" to 0,
        "var-micro" to 1,
        "var-minor" to 1,
        "at-most" to 1,
        "range" to 1,
        "not" to 1,
        "any" to 2,
        "at-least" to 2,
        "latest" to 2,
        "other" to 3,
        "unresolved" to 3,
        "unclassified" to 3
)

// results[pm.ordinal][classification.indexOf][match.ordinal]
var results: Array<Vector<Array<Int>>> = Array(PairCollector.PackageManager.values().size) { Vector<Array<Int>>() }

fun analyseAll() {
    loadResults()

//    PairCollector.PackageManager.values().forEach { pm ->
//        Classifications.ALL.forEach { classification ->
//            val temp = results[pm.ordinal][Classifications.ALL.indexOf(classification)][0]
//            results[pm.ordinal][Classifications.ALL.indexOf(classification)][0] = results[pm.ordinal][Classifications.ALL.indexOf(classification)][1]
//            results[pm.ordinal][Classifications.ALL.indexOf(classification)][1] = results[pm.ordinal][Classifications.ALL.indexOf(classification)][2]
//            results[pm.ordinal][Classifications.ALL.indexOf(classification)][2] = temp
//        }
//    }

    for (pm in PairCollector.PackageManager.values()) {
        if (!LagCheckingService.supported(pm)) {
            // Only check pairs in PMs with finished classes
            log.info("$pm flexible study not supported")
            continue
        }

        if (resultsCount(pm) > 0) {
            // Avoid recomputing values
            log.info("${resultsCount(pm)} in $pm")
            continue
        }

        val lag = FlexibleAnalysisByPM(pm)
        val result = lag.getLag()
        results[pm.ordinal] = result
    }

    saveResults()
    printToFile()
}

fun resultsCount(pm: PairCollector.PackageManager) : Int {
    return results[pm.ordinal].map { it.sum() }.sum()
}

private fun loadResults() {
    try {
        val streamin = ObjectInputStream(FileInputStream(results_bin_path))
        results = streamin.readObject() as Array<Vector<Array<Int>>>
        streamin.close()
    } catch (e: FileNotFoundException) {
        Logging.getLogger("").warn("File $results_bin_path was not found")
        results.forEach { vector -> Classifications.ALL.forEach { vector.add(Array(MatcherResult.values().size) {0}) } }
    }
}

private fun saveResults() {
    val streamout = ObjectOutputStream(FileOutputStream(results_bin_path))
    streamout.writeObject(results)
    streamout.close()
}

private fun printToFile() {
    File("data/flexible_lag.csv").bufferedWriter().use { out ->
        out.write(",")
        PairCollector.PackageManager.values().forEach {
            if (resultsCount(it) > 0)
                out.write(",$it")
        }

        Classifications.ALL.forEach { classification ->
            MatcherResult.values().forEach { match ->
                out.write("\n$classification,$match")
                PairCollector.PackageManager.values().forEach { pm ->
                    if (resultsCount(pm) > 0)
                        out.write(",${results[pm.ordinal][Classifications.ALL.indexOf(classification)][match.ordinal]}")
                }
            }
        }
    }

    File("data/flexible_lag_grouped.tex").bufferedWriter().use { out ->
        out.write("\\begin{tabular}{|l|rr|rr|}\n")
        out.write("\\hline\n")
        out.write("& \\multicolumn{2}{c|}{Fixed} & \\multicolumn{2}{c|}{Flexible} \\\\\n")
        out.write("PM & Current & Outdated & Current & Outdated \\\\\n")
        out.write("\\hline\n")

        PairCollector.PackageManager.values().forEach {pm ->
            if (resultsCount(pm) == 0)
                return@forEach

            val result = Array(4) { Array(3) { 0 } }
            Classifications.ALL.forEachIndexed { index, classification ->
                MatcherResult.values().forEach { match ->
                    result[mapClassifications[classification]!!][match.ordinal] += results[pm.ordinal][index][match.ordinal]
                }
            }

            out.write("$pm ")
            for (i in 0 until 2) {
                for (j in 0 until 2) {
                    out.write("& ${result[i][j]} ")
                }
            }
            out.write("\\\\\n")
        }

        out.write("\\hline\n")
        out.write("\\end{tabular}\n")
    }
}
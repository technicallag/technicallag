package masters

import masters.flexilag.LagCheckingService
import masters.flexilag.MatcherResult
import masters.libiostudy.Classifications
import masters.utils.Logging
import java.io.*
import java.util.*

/**
 * Created by Jacob Stringer on 16/12/2019.
 */

const val results_bin_path = "data/flexible_study/flexible_lag.bin"
val log = Logging.getLogger("")

// results[pm.ordinal][classification.indexOf][match.ordinal]
var results: Array<Vector<Array<Int>>> = Array(PairCollector.PackageManager.values().size) { Vector<Array<Int>>() }

fun analyseAll() {
    loadResults()

    for (pm in PairCollector.PackageManager.values()) {
        if (!LagCheckingService.supported(pm)) {
            // Only check pairs in PMs with finished classes
            log.info("$pm flexible study not supported")
            continue
        }

        if (pm == PairCollector.PackageManager.NPM || pm == PairCollector.PackageManager.ATOM) {

        }
        else if (resultsCount(pm) > 0) {
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
            out.write(",$it")
        }

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
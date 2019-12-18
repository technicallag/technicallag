package masters

import masters.flexilag.MatcherResult
import masters.libiostudy.Classifications
import masters.utils.Logging
import java.io.*
import java.util.*

/**
 * Created by Jacob Stringer on 16/12/2019.
 */

const val results_bin_path = "data/flexible_lag.bin"
var results: Array<Vector<Array<Int>>> = Array(PairCollector.PackageManager.values().size) { Vector<Array<Int>>() }

fun analyseAll() {
    loadResults()

    for (pm in PairCollector.PackageManager.values()) {
        val lag = FlexibleAnalysisByPM(pm)
        val result = lag.getLag()
        results[pm.ordinal] = result
    }

    saveResults()
    printToFile()
}

private fun loadResults() {
    try {
        val streamin = ObjectInputStream(FileInputStream(results_bin_path))
        results = streamin.readObject() as Array<Vector<Array<Int>>>
        streamin.close()
    } catch (e: FileNotFoundException) {
        Logging.getLogger("").warn("File $results_bin_path was not found")
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
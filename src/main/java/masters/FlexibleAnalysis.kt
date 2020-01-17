package masters

import com.sun.org.apache.xerces.internal.impl.xpath.regex.Match
import masters.flexilag.LagCheckingService
import masters.flexilag.MatcherResult
import masters.libiostudy.Classifications
import masters.utils.*
import java.io.*
import java.util.*

/**
 * Created by Jacob Stringer on 16/12/2019.
 */

const val results_bin_path = "../flexible_study/flexible_lag.bin"
const val results_analysisobject_path = "../flexible_study/analysisobjects.bin"
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

val mapClassifications2 = mapOf(
        "fixed" to 0,
        "soft" to 0,
        "var-micro" to 1,
        "var-minor" to 2,
        "at-most" to 3,
        "range" to 4,
        "not" to 4
)

val rerun = setOf( PairCollector.PackageManager.CPAN ) // CPAN is the dummy value

// results[pm.ordinal][classification.indexOf][match.ordinal]
var results: Array<Vector<Array<Int>>> = Array(PairCollector.PackageManager.values().size) { Vector<Array<Int>>() }
var resultsByPM = mutableMapOf<PairCollector.PackageManager, FlexibleAnalysisByPM>()

fun analyseAll() {
    loadResults()

    for (pm in PairCollector.PackageManager.values()) {
        // Only check pairs in PMs that are in scope
        if (!LagCheckingService.supported(pm)) {
            log.info("$pm flexible study not supported")
            continue
        }

        // Avoid recomputing lag values unnecessarily
        if (resultsCount(pm) == 0 || rerun.contains(pm)) {
            log.info("${resultsCount(pm)} in $pm")
            results[pm.ordinal] = resultsByPM[pm]!!.getLag()
        }

        if (resultsByPM[pm]!!.lagValueBuckets["major"]!!.isEmpty()) {
            resultsByPM[pm]!!.getLag(extraLag = true)
        }
    }

    saveResults()
    printToFile()
    printLagDetails()
    printTypesOfLagToFile()
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

    try {
        val streamin = ObjectInputStream(FileInputStream(results_analysisobject_path))
        resultsByPM = streamin.readObject() as MutableMap<PairCollector.PackageManager, FlexibleAnalysisByPM>
        streamin.close()
    } catch (e: FileNotFoundException) {
        Logging.getLogger("").warn("File $results_analysisobject_path was not found")
        PairCollector.PackageManager.values().forEach { resultsByPM[it] = FlexibleAnalysisByPM(it) }
    }
}

private fun saveResults() {
    val streamout = ObjectOutputStream(FileOutputStream(results_bin_path))
    streamout.writeObject(results)
    streamout.close()

    val streamout2 = ObjectOutputStream(FileOutputStream(results_analysisobject_path))
    streamout2.writeObject(resultsByPM)
    streamout2.close()
}

fun printLagDetails() {
    File("data/flexible_lag_amounts.tex").bufferedWriter().use {out ->
        out.write("\\begin{tabular}{|l|rrr|rrr|rrr|}\n")
        out.write("\\hline\n")
        out.write("& \\multicolumn{3}{c|}{Major} & \\multicolumn{3}{c|}{Minor} & \\multicolumn{3}{c|}{Micro} \\\\\n")
        out.write("PM & Mean & StdDev & NoLag & Mean & StdDev & NoLag & Mean & StdDev & NoLag  \\\\\n")
        out.write("\\hline\n")

        resultsByPM.toSortedMap().forEach { (pm, analysis) ->
            if (analysis.lagValueBuckets["major"]!!.isEmpty())
                return@forEach

            out.write("$pm ")
            for (cat in listOf("major", "minor", "micro")) {
                val stats = meanAndStddevFromBucketsExcludeZerosAndTwentyYears(analysis.lagValueBuckets[cat]!!)
                val zeros = analysis.lagValueBuckets[cat]!!.filter { it.key == 0L }.map { it.value }.sum()
                val count = analysis.lagValueBuckets[cat]!!.map { it.value }.sum()
                out.write("& %.2f & %.2f & %.2f ".format(stats.first, stats.second, 100.0 * zeros / count))
            }
            out.write("\\\\\n")
        }

        out.write("\\hline\n")
        out.write("\\\\\n")
        out.write("& \\multicolumn{9}{c|}{Time lag in days} \\\\\n")
        out.write("& \\multicolumn{3}{c|}{Major Time} & \\multicolumn{3}{c|}{Minor Time} & \\multicolumn{3}{c|}{Micro Time} \\\\\n")
        out.write("PM & Mean & StdDev & NoLag & Mean & StdDev & NoLag & Mean & StdDev & NoLag  \\\\\n")
        out.write("\\hline\n")

        resultsByPM.toSortedMap().forEach { (pm, analysis) ->
            if (analysis.lagValueBuckets["major"]!!.isEmpty())
                return@forEach

            out.write("$pm ")
            for (cat in listOf("majorTime", "minorTime", "microTime")) {
                val stats = meanAndStddevFromBucketsExcludeZerosAndTwentyYears(analysis.lagValueBuckets[cat]!!)
                out.write("& %.1f & %.1f & ".format(stats.first, stats.second))
            }
            out.write("\\\\\n")
        }

        out.write("\\hline\n")
        out.write("\\end{tabular}\n")
    }
}

private fun printTypesOfLagToFile() {
    File("data/flexible_lag_types.tex").bufferedWriter().use {out ->
        out.write("\\begin{tabular}{|l|ccccccc|c|c|}\n")
        out.write("\\hline\n")
        out.write("\\multirow{3}{*}{Contains lag} & \\multirow{3}{*}{Major} & \\multirow{3}{*}{Major \\& Minor} & \\multirow{3}{*}{Major \\& Micro} & \\multirow{3}{*}{Major \\& Minor \\& Micro} & \\multirow{3}{*}{Minor} & \\multirow{3}{*}{Minor \\& Micro} & \\multirow{3}{*}{Micro} & \\multirow{3}{*}{No Lag} & \\multirow{3}{*}{Dependency Declaration Total} \\\\\n")
        out.write(" & & & & & & & & & \\\\\n")
        out.write(" & & & & & & & & & \\\\\n")
        out.write("\\hline\n")

        resultsByPM.toSortedMap().forEach { (pm, analysis) ->
            if (analysis.lagValueBuckets["major"]!!.isEmpty())
                return@forEach

            out.write("$pm ")
            val counts = analysis.lagtypes.sum()
            Lag.Type.values().forEach {
                out.write("& %.2f".format(analysis.lagtypes[it.ordinal] * 100.0 / counts) + "\\% ")
            }
            out.write("& $counts ")
            out.write("\\\\\n")
        }

        out.write("\\hline\n")
        out.write("\\end{tabular}\n")
    }
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

    File("data/flexible_lag_percentages.tex").bufferedWriter().use { out ->
        File("data/flexible_lag_raw.tex").bufferedWriter().use { outraw ->
            out.write("\\begin{tabular}{|l|rrrrr|r|}\n")
            out.write("\\hline\n")
            out.write("PM & Fixed & Micro & Minor & At-Most & Range & Overall \\\\\n")
            out.write("\\hline\n")

            outraw.write("\\begin{tabular}{|l|rrrrrrrrrr|rr|}\n")
            outraw.write("\\hline\n")
            outraw.write("PM & Fixed & Total & Micro & Total & Minor & Total & At-Most & Total & Range & Total & Overall & Total \\\\\n")
            outraw.write("\\hline\n")

            PairCollector.PackageManager.values().forEach { pm ->
                if (resultsCount(pm) == 0)
                    return@forEach

                val matches = Array(5) { 0 }
                val total = Array(5) { 0 }

                var matchTotal = 0
                var totalTotal = 0

                mapClassifications2.forEach { (classification, index) ->
                    val classIndex = Classifications.ALL.indexOf(classification)
                    matches[index] += results[pm.ordinal][classIndex][0]
                    total[index] += results[pm.ordinal][classIndex][0] + results[pm.ordinal][classIndex][1]

                    matchTotal += results[pm.ordinal][classIndex][0]
                    totalTotal += results[pm.ordinal][classIndex][0] + results[pm.ordinal][classIndex][1]
                }

                out.write("$pm ")
                outraw.write("$pm ")

                for (i in 0..matches.lastIndex) {
                    if (total[i] == 0)
                        out.write("& - ")
                    else
                        out.write("& ${"%.1f".format(100.0 - matches[i].toDouble() * 100 / total[i])}\\% ")

                    outraw.write("& ${matches[i]} & ${total[i]} ")
                }

                out.write("& ${"%.1f".format(100.0 - matchTotal.toDouble() * 100 / totalTotal)}\\% ")
                outraw.write("& $matchTotal & $totalTotal ")

                out.write("\\\\\n")
                outraw.write("\\\\\n")
            }

            out.write("\\hline\n")
            out.write("\\end{tabular}\n")

            outraw.write("\\hline\n")
            outraw.write("\\end{tabular}\n")
        }
    }
}
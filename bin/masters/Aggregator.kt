package masters

import masters.utils.Logging
import masters.utils.descriptiveStats
import masters.utils.descriptiveStatsHeader
import java.io.BufferedWriter
import java.io.File
import java.io.FileWriter

/**
 * Created by Jacob Stringer on 7/11/2019.
 */


class Aggregator(val name: String) {

    val matrices = Array(9) { UpdateMatrix(it) }
    val depsMissingAtEnd = mutableMapOf<Int, Int>()
    val depsMissingInMiddle = mutableMapOf<Int, Int>()
    val ge1Update = mutableMapOf<Update, Int>()
    var projectsWithoutChangeObjects = 0
    var projectCounter = 0L
    var lagCounter = 0L

    val totalLag = Lag(0,0,0,0,0,0)
    val lagValues = mapOf("major" to mutableListOf<Long>(),
            "majorTime" to mutableListOf<Long>(),
            "minor" to mutableListOf<Long>(),
            "minorTime" to mutableListOf<Long>(),
            "micro" to mutableListOf<Long>(),
            "microTime" to mutableListOf<Long>())

    val aggregatorTotals = mutableMapOf<String, String>()
    //val pairStats = mutableListOf<String>()

    @Synchronized fun addStatistics(stats: PairStatistics) {
        projectCounter++
        lagCounter += stats.quantityOfLag.size

        totalLag.addLag(stats.totalLag)
        stats.hasThisUpdate.forEach { ge1Update[it] = ge1Update.getOrDefault(it, 0) + 1 }

        lagValues.forEach { (k, v) -> stats.lagValues[k]?.let { v.addAll(it) } }

        for (index in 0 until matrices.size - 1) {
            matrices[index].addMatrix(stats.matrices[index])
        }

        val missingDeps = stats.missingDepsTotal - stats.missingDepsAtEnd
        depsMissingAtEnd[stats.missingDepsAtEnd] = depsMissingAtEnd.getOrDefault(stats.missingDepsAtEnd, 0) + 1
        depsMissingInMiddle[missingDeps] = depsMissingInMiddle.getOrDefault(missingDeps, 0) + 1
        projectsWithoutChangeObjects += if (stats.classifyUpdates.size == 0) 1 else 0

        //if (pairStats.size == 0) pairStats.add(stats.header())
        //pairStats.add(stats.toString())
    }

    // Note pairStats are not added when combining aggregators currently (performance concerns)
    @Synchronized fun addAggreator(other: Aggregator) {
        projectCounter += other.projectCounter
        projectsWithoutChangeObjects += other.projectsWithoutChangeObjects

        lagCounter += other.lagCounter
        totalLag.addLag(other.totalLag)

        lagValues.forEach { (k, v) -> other.lagValues[k]?.let { v.addAll(it) } }

        other.ge1Update.forEach { (update, int) ->
            ge1Update[update] = ge1Update.getOrDefault(update, 0) + int
        }

        other.depsMissingInMiddle.forEach { (numberDeps, counter) ->
            depsMissingInMiddle[numberDeps] = depsMissingInMiddle.getOrDefault(numberDeps, 0) + counter
        }

        other.depsMissingAtEnd.forEach { (numberDeps, counter) ->
            depsMissingAtEnd[numberDeps] = depsMissingAtEnd.getOrDefault(numberDeps, 0) + counter
        }

        // Totals only get processed when printed
        matrices.forEachIndexed { index, updateMatrix -> if (index == matrices.size - 1) return@forEachIndexed
            updateMatrix.addMatrix(other.matrices[index])
        }

        aggregatorTotals[other.name] = other.matrices.last().collapseColsToStringNormalised()
    }

    fun printAggregator() {
        // Preprocessing has not been done yet
        if (matrices.last().total == 0) {
            lagValues.forEach { it.value.sort() }
            for (i in 0 until matrices.size - 1) matrices[matrices.size-1].addMatrix(matrices[i])
        }

        File("data/aggregations/${name}/").mkdirs()

        BufferedWriter(FileWriter("data/aggregations/${name}/matrix_raw_summary.csv")).use {out ->
            out.write("Raw Values," + Update.values().joinToString(",") + ",Total\n")
            UpdateMatrix.matrixLabels.forEachIndexed { index, it -> out.write(it + "," + matrices[index].collapseColsToString() + "\n") }
        }

        BufferedWriter(FileWriter("data/aggregations/${name}/matrix_normal_summary.csv")).use {out ->
            out.write("Normalised (Each row sums to 1)," + Update.values().joinToString(",") + ",Total\n")
            UpdateMatrix.matrixLabels.forEachIndexed { index, it -> out.write(it + "," + matrices[index].collapseColsToStringNormalised() + "\n") }
        }

        BufferedWriter(FileWriter("data/aggregations/${name}/descriptive_stats.csv")).use {out ->
            out.write("," + descriptiveStatsHeader() + "\n" +
                    "MAJOR,${descriptiveStats(lagValues["major"])}\n" +
                    "MAJOR TIME,${descriptiveStats(lagValues["majorTime"])}\n" +
                    "MINOR,${descriptiveStats(lagValues["minor"])}\n" +
                    "MINOR TIME,${descriptiveStats(lagValues["minorTime"])}\n" +
                    "MICRO,${descriptiveStats(lagValues["micro"])}\n" +
                    "MICRO TIME,${descriptiveStats(lagValues["microTime"])}\n"
            )
        }

        BufferedWriter(FileWriter("data/aggregations/${name}/miscellaneous.csv")).use {out ->
            out.write("Dependencies missing at end: \n${depsMissingAtEnd.toSortedMap()}\n")
            out.write("\nDependencies missing in the middle: \n${depsMissingInMiddle.toSortedMap()}\n")
            out.write("\nTypes of changes found at least once in the pair history: \n${ge1Update.toSortedMap()}\n")
            out.write("\nPairs without any change objects at all (i.e. they did not have two contiguous versions with a dependency to B): $projectsWithoutChangeObjects\n")
            out.write("\nTotal pairs considered: $projectCounter")
        }

        BufferedWriter(FileWriter("data/aggregations/${name}/matrices_detailed.csv")).use {out ->
            // Matrix details
            matrices.forEach {
                out.write(it.toString())
            }

            matrices.forEach {
                out.write("Normalised:\n")
                out.write(it.normalizeMatrix().toString())
            }
        }

        // For the summary aggregator, it prints the average values by
        if (aggregatorTotals.size > 0) {
            BufferedWriter(FileWriter("data/aggregations/${name}/matrices_aggregation_summary.csv")).use {out ->
                out.write("PM," + Update.values().joinToString (",") + ",Totals\n")
                aggregatorTotals.forEach { out.write("${it.key},${it.value}\n") }
            }
        }

//        if (pairStats.size > 0)
//            BufferedWriter(FileWriter("data/aggregations/${name}/ProjectStats.csv")).use {out ->
//                pairStats.forEach { out.write(it); out.write("\n") }
//            }

        Logging.getLogger("").info("$name aggregator has been printed")
    }

}